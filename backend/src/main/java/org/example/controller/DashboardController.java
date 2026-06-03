package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.common.Result;
import org.example.dto.DashboardStatsDTO;
import org.example.entity.Issue;
import org.example.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public Result<DashboardStatsDTO> stats() {
        return Result.success(dashboardService.getStats());
    }

    @GetMapping("/recent")
    public Result<List<Issue>> recent(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(dashboardService.getRecentIssues(limit));
    }
}
