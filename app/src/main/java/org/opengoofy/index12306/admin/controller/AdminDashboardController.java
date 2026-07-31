package org.opengoofy.index12306.admin.controller;

import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.admin.service.AdminStatsService;
import org.opengoofy.index12306.framework.convention.result.Result;
import org.opengoofy.index12306.framework.convention.result.Results;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员仪表盘控制器。
 */
@RestController
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/api/admin/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Results.success(adminStatsService.dashboard());
    }

    @GetMapping("/api/admin/stats/order-trend")
    public Result<Map<String, Object>> orderTrend(@RequestParam(defaultValue = "7") int days) {
        return Results.success(adminStatsService.orderTrend(days));
    }

    @GetMapping("/api/admin/stats/revenue-trend")
    public Result<Map<String, Object>> revenueTrend(@RequestParam(defaultValue = "7") int days) {
        return Results.success(adminStatsService.revenueTrend(days));
    }
}
