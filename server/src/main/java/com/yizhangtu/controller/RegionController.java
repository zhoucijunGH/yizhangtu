package com.yizhangtu.controller;

import com.yizhangtu.common.Result;
import com.yizhangtu.entity.RegionArea;
import com.yizhangtu.service.RegionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 区域接口：区域面（含 GeoJSON）与按区域统计。
 */
@RestController
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionService regionService;

    public RegionController(RegionService regionService) {
        this.regionService = regionService;
    }

    /** 区域列表（含 GeoJSON、中心点） */
    @GetMapping
    public Result<List<RegionArea>> list() {
        return Result.ok(regionService.list());
    }

    /** 按区域统计：点位数 / 告警数 / 办结率 */
    @GetMapping("/stats")
    public Result<List<Map<String, Object>>> stats() {
        return Result.ok(regionService.stats());
    }
}
