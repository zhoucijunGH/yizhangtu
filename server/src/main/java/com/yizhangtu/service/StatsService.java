package com.yizhangtu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yizhangtu.entity.PointObject;
import com.yizhangtu.mapper.PointObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计服务：区域指标 + 按分类分布（给 ECharts）。
 */
@Service
public class StatsService {

    /** 分类展示顺序 */
    private static final String[] CATEGORIES = {"MONITOR", "HAZARD", "PROJECT", "PATROL"};

    private final PointObjectMapper pointMapper;
    private final RegionService regionService;

    public StatsService(PointObjectMapper pointMapper, RegionService regionService) {
        this.pointMapper = pointMapper;
        this.regionService = regionService;
    }

    /** 总览：区域指标表 + 分类分布 */
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("regionStats", regionService.stats());
        data.put("categoryDist", categoryDist());
        return data;
    }

    /** 按分类统计点位数（固定四类顺序，缺项补 0） */
    private List<Map<String, Object>> categoryDist() {
        List<PointObject> points = pointMapper.selectList(new LambdaQueryWrapper<PointObject>()
                .select(PointObject::getCategory));
        Map<String, Long> countByCategory = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            countByCategory.put(category, 0L);
        }
        for (PointObject point : points) {
            countByCategory.merge(point.getCategory(), 1L, Long::sum);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        countByCategory.forEach((category, count) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", category);
            item.put("count", count);
            result.add(item);
        });
        return result;
    }
}
