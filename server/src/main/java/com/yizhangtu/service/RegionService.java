package com.yizhangtu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yizhangtu.entity.AlarmRecord;
import com.yizhangtu.entity.PointObject;
import com.yizhangtu.entity.RegionArea;
import com.yizhangtu.mapper.AlarmRecordMapper;
import com.yizhangtu.mapper.PointObjectMapper;
import com.yizhangtu.mapper.RegionAreaMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 区域服务：区域面数据 + 按区域统计点位数 / 告警数 / 办结率。
 */
@Service
public class RegionService {

    private final RegionAreaMapper regionMapper;
    private final PointObjectMapper pointMapper;
    private final AlarmRecordMapper alarmMapper;

    public RegionService(RegionAreaMapper regionMapper, PointObjectMapper pointMapper,
                         AlarmRecordMapper alarmMapper) {
        this.regionMapper = regionMapper;
        this.pointMapper = pointMapper;
        this.alarmMapper = alarmMapper;
    }

    /** 区域列表（含 GeoJSON） */
    public List<RegionArea> list() {
        return regionMapper.selectList(new LambdaQueryWrapper<RegionArea>().orderByAsc(RegionArea::getId));
    }

    /**
     * 按区域统计：点位数 / 告警数 / 办结率（已处理+已关闭 占比）。
     */
    public List<Map<String, Object>> stats() {
        List<RegionArea> regions = list();
        List<PointObject> points = pointMapper.selectList(null);
        // 区域名 → 点位 id 集合
        Map<String, List<Long>> idsByRegion = points.stream()
                .filter(p -> p.getRegion() != null)
                .collect(Collectors.groupingBy(PointObject::getRegion,
                        LinkedHashMap::new, Collectors.mapping(PointObject::getId, Collectors.toList())));

        // 点位 id → 告警
        Map<Long, List<AlarmRecord>> alarmsByPoint = alarmMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(AlarmRecord::getPointId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (RegionArea region : regions) {
            List<Long> ids = idsByRegion.getOrDefault(region.getName(), List.of());
            int pointCount = ids.size();
            int alarmCount = 0;
            int closed = 0;
            int unprocessed = 0;
            for (Long id : ids) {
                for (AlarmRecord alarm : alarmsByPoint.getOrDefault(id, List.of())) {
                    alarmCount++;
                    if ("PROCESSED".equals(alarm.getStatus()) || "CLOSED".equals(alarm.getStatus())) {
                        closed++;
                    }
                    if ("UNPROCESSED".equals(alarm.getStatus())) {
                        unprocessed++;
                    }
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("region", region.getName());
            item.put("parentName", region.getParentName());
            item.put("centroidLng", region.getCentroidLng());
            item.put("centroidLat", region.getCentroidLat());
            item.put("pointCount", pointCount);
            item.put("alarmCount", alarmCount);
            item.put("unprocessed", unprocessed);
            item.put("closeRate", closeRate(closed, alarmCount));
            result.add(item);
        }
        return result;
    }

    /** 办结率（%）：无告警视为 100 */
    private double closeRate(int closed, int total) {
        if (total == 0) {
            return 100.0;
        }
        return BigDecimal.valueOf(closed * 100.0 / total).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
