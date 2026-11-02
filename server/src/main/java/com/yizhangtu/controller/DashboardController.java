package com.yizhangtu.controller;

import com.yizhangtu.common.Result;
import com.yizhangtu.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 看板接口：今日开单、欠款、近 7 天趋势。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(dashboardService.dashboard());
    }
}
