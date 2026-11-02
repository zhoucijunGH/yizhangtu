package com.yizhangtu.task;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yizhangtu.entity.AlarmRecord;
import com.yizhangtu.entity.AlarmRule;
import com.yizhangtu.entity.PointObject;
import com.yizhangtu.mapper.AlarmRecordMapper;
import com.yizhangtu.mapper.AlarmRuleMapper;
import com.yizhangtu.mapper.PointObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 模拟数据流：每 30 秒对 NORMAL 的 MONITOR 点位随机游走 water_level / rainfall，
 * 越限时写入告警记录（同点位同规则 30 分钟内去重）。
 * <p>
 * 只更新属性字段，不会删除或覆盖启动时已有点位。
 */
@Slf4j
@Component
public class DataSimulationTask {

    private static final BigDecimal WL_MIN = BigDecimal.ZERO;
    private static final BigDecimal WL_MAX = new BigDecimal("12");
    private static final BigDecimal RF_MIN = BigDecimal.ZERO;
    private static final BigDecimal RF_MAX = new BigDecimal("150");
    private static final int DEDUP_MINUTES = 30;

    private final PointObjectMapper pointMapper;
    private final AlarmRuleMapper ruleMapper;
    private final AlarmRecordMapper alarmMapper;

    public DataSimulationTask(PointObjectMapper pointMapper, AlarmRuleMapper ruleMapper,
                              AlarmRecordMapper alarmMapper) {
        this.pointMapper = pointMapper;
        this.ruleMapper = ruleMapper;
        this.alarmMapper = alarmMapper;
    }

    /** 每 30 秒执行一次（上一轮跑完再计时） */
    @Scheduled(fixedDelay = 30_000)
    public void tick() {
        try {
            List<PointObject> monitors = pointMapper.selectList(new LambdaQueryWrapper<PointObject>()
                    .eq(PointObject::getCategory, "MONITOR")
                    .eq(PointObject::getStatus, "NORMAL"));
            if (monitors.isEmpty()) {
                return;
            }
            List<AlarmRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<AlarmRule>()
                    .eq(AlarmRule::getEnabled, 1));
            Map<Long, List<AlarmRule>> rulesByPoint = rules.stream()
                    .collect(Collectors.groupingBy(AlarmRule::getPointId));

            for (PointObject point : monitors) {
                if (!StringUtils.hasText(point.getAttrsJson())) {
                    continue;
                }
                JSONObject attrs;
                try {
                    attrs = JSONUtil.parseObj(point.getAttrsJson());
                } catch (Exception e) {
                    continue;
                }

                boolean changed = walk(attrs);
                if (changed) {
                    point.setAttrsJson(attrs.toString());
                    pointMapper.updateById(point);
                }

                for (AlarmRule rule : rulesByPoint.getOrDefault(point.getId(), List.of())) {
                    checkRule(point, rule, attrs);
                }
            }
        } catch (Exception e) {
            log.error("模拟数据流执行异常", e);
        }
    }

    /** 随机游走：水位 ±0.15m（1 位小数），雨量 ±2mm（整数），均限制在合理区间 */
    private boolean walk(JSONObject attrs) {
        boolean changed = false;
        BigDecimal waterLevel = attrs.getBigDecimal("water_level");
        if (waterLevel != null) {
            double delta = ThreadLocalRandom.current().nextDouble(-0.15, 0.15);
            BigDecimal next = waterLevel.add(BigDecimal.valueOf(delta)).setScale(1, RoundingMode.HALF_UP);
            next = next.max(WL_MIN).min(WL_MAX);
            attrs.set("water_level", next);
            changed = true;
        }
        BigDecimal rainfall = attrs.getBigDecimal("rainfall");
        if (rainfall != null) {
            int delta = ThreadLocalRandom.current().nextInt(-2, 3);
            BigDecimal next = rainfall.add(BigDecimal.valueOf(delta)).setScale(0, RoundingMode.HALF_UP);
            next = next.max(RF_MIN).min(RF_MAX);
            attrs.set("rainfall", next);
            changed = true;
        }
        return changed;
    }

    /** 阈值判定：越限则写告警（30 分钟去重） */
    private void checkRule(PointObject point, AlarmRule rule, JSONObject attrs) {
        BigDecimal value = attrs.getBigDecimal(rule.getAttrKey());
        if (value == null) {
            return;
        }
        if (rule.getUpperLimit() != null && value.compareTo(rule.getUpperLimit()) > 0) {
            raise(point, rule, value, rule.getUpperLimit(), "_UPPER", ">");
        }
        if (rule.getLowerLimit() != null && value.compareTo(rule.getLowerLimit()) < 0) {
            raise(point, rule, value, rule.getLowerLimit(), "_LOWER", "<");
        }
    }

    private void raise(PointObject point, AlarmRule rule, BigDecimal value, BigDecimal limit,
                       String typeSuffix, String operator) {
        String alarmType = rule.getAttrKey() + typeSuffix;
        LocalDateTime since = LocalDateTime.now().minusMinutes(DEDUP_MINUTES);
        Long recent = alarmMapper.selectCount(new LambdaQueryWrapper<AlarmRecord>()
                .eq(AlarmRecord::getPointId, point.getId())
                .eq(AlarmRecord::getRuleId, rule.getId())
                .eq(AlarmRecord::getAlarmType, alarmType)
                .ge(AlarmRecord::getCreatedAt, since));
        if (recent != null && recent > 0) {
            return;
        }

        AlarmRecord alarm = new AlarmRecord();
        alarm.setPointId(point.getId());
        alarm.setPointName(point.getName());
        alarm.setRuleId(rule.getId());
        alarm.setAlarmType(alarmType);
        alarm.setValueStr(fmt(value, rule.getAttrKey()) + unit(rule.getAttrKey()));
        alarm.setThresholdStr(operator + " " + fmt(limit, rule.getAttrKey()) + unit(rule.getAttrKey()));
        alarm.setStatus("UNPROCESSED");
        alarm.setCreatedAt(LocalDateTime.now());
        alarmMapper.insert(alarm);
        log.info("模拟告警：点位【{}】{} 当前 {}，阈值 {}",
                point.getName(), rule.getAttrLabel(), alarm.getValueStr(), alarm.getThresholdStr());
    }

    /** 属性单位映射：水位 m、雨量 mm */
    private String unit(String attrKey) {
        return switch (attrKey) {
            case "water_level" -> "m";
            case "rainfall" -> "mm";
            default -> "";
        };
    }

    private String fmt(BigDecimal v, String attrKey) {
        return "rainfall".equals(attrKey)
                ? v.setScale(0, RoundingMode.HALF_UP).toPlainString()
                : v.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }
}
