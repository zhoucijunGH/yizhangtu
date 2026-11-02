package com.yizhangtu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yizhangtu.common.BizException;
import com.yizhangtu.common.PageResult;
import com.yizhangtu.entity.AlarmRecord;
import com.yizhangtu.entity.AlarmRule;
import com.yizhangtu.mapper.AlarmRecordMapper;
import com.yizhangtu.mapper.AlarmRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 告警服务：分页筛选 / 处理留痕 / 关闭。
 */
@Service
public class AlarmService {

    private final AlarmRecordMapper alarmMapper;
    private final AlarmRuleMapper ruleMapper;

    public AlarmService(AlarmRecordMapper alarmMapper, AlarmRuleMapper ruleMapper) {
        this.alarmMapper = alarmMapper;
        this.ruleMapper = ruleMapper;
    }

    /** 分页列表（状态 / 点位 / 关键词 / 日期区间筛选），返回前按规则补充属性名 */
    public PageResult<AlarmRecord> page(String status, Long pointId, String keyword,
                                        LocalDate dateFrom, LocalDate dateTo, int page, int size) {
        IPage<AlarmRecord> result = alarmMapper.selectPage(new Page<>(page, size),
                buildQuery(status, pointId, keyword, dateFrom, dateTo));
        enrich(result.getRecords());
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    /** 全量列表（导出用，复用同一套筛选） */
    public List<AlarmRecord> list(String status, Long pointId, String keyword,
                                  LocalDate dateFrom, LocalDate dateTo) {
        List<AlarmRecord> records = alarmMapper.selectList(buildQuery(status, pointId, keyword, dateFrom, dateTo));
        enrich(records);
        return records;
    }

    private LambdaQueryWrapper<AlarmRecord> buildQuery(String status, Long pointId, String keyword,
                                                       LocalDate dateFrom, LocalDate dateTo) {
        return new LambdaQueryWrapper<AlarmRecord>()
                .eq(StringUtils.hasText(status), AlarmRecord::getStatus, status)
                .eq(pointId != null, AlarmRecord::getPointId, pointId)
                .like(StringUtils.hasText(keyword), AlarmRecord::getPointName, keyword)
                .ge(dateFrom != null, AlarmRecord::getCreatedAt, dateFrom)
                .lt(dateTo != null, AlarmRecord::getCreatedAt, dateTo == null ? null : dateTo.plusDays(1))
                .orderByDesc(AlarmRecord::getCreatedAt)
                .orderByDesc(AlarmRecord::getId);
    }

    /** 处理告警：必填说明，留痕处理人与时间 */
    public AlarmRecord handle(Long id, String handleNote, String handler) {
        AlarmRecord alarm = alarmMapper.selectById(id);
        if (alarm == null) {
            throw new BizException(404, "告警不存在");
        }
        if ("CLOSED".equals(alarm.getStatus())) {
            throw new BizException(400, "已关闭的告警不能再处理");
        }
        if (!StringUtils.hasText(handleNote)) {
            throw new BizException(400, "请填写处理说明");
        }
        alarm.setStatus("PROCESSED");
        alarm.setHandleNote(handleNote.trim());
        alarm.setHandler(handler);
        alarm.setHandledAt(LocalDateTime.now());
        alarmMapper.updateById(alarm);
        return alarm;
    }

    /** 关闭告警（ADMIN）：说明可选 */
    public AlarmRecord close(Long id, String handleNote, String handler) {
        AlarmRecord alarm = alarmMapper.selectById(id);
        if (alarm == null) {
            throw new BizException(404, "告警不存在");
        }
        if ("CLOSED".equals(alarm.getStatus())) {
            throw new BizException(400, "告警已关闭");
        }
        alarm.setStatus("CLOSED");
        if (StringUtils.hasText(handleNote)) {
            alarm.setHandleNote(handleNote.trim());
        }
        alarm.setHandler(handler);
        alarm.setHandledAt(LocalDateTime.now());
        alarmMapper.updateById(alarm);
        return alarm;
    }

    /** 按规则补充属性键/属性名（规则被删时按 alarm_type 兜底解析） */
    private void enrich(List<AlarmRecord> records) {
        Set<Long> ruleIds = records.stream()
                .map(AlarmRecord::getRuleId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, AlarmRule> ruleMap = ruleIds.isEmpty() ? Map.of()
                : ruleMapper.selectBatchIds(ruleIds).stream()
                        .collect(Collectors.toMap(AlarmRule::getId, Function.identity(), (a, b) -> a));
        for (AlarmRecord record : records) {
            AlarmRule rule = record.getRuleId() == null ? null : ruleMap.get(record.getRuleId());
            if (rule != null) {
                record.setAttrKey(rule.getAttrKey());
                record.setAttrLabel(rule.getAttrLabel());
            } else {
                String type = record.getAlarmType() == null ? "" : record.getAlarmType();
                int idx = type.lastIndexOf('_');
                String key = idx > 0 ? type.substring(0, idx) : type;
                record.setAttrKey(key);
                record.setAttrLabel(key);
            }
        }
    }
}
