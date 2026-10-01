package com.nbamanager.web;

import com.nbamanager.service.DashboardService;
import com.nbamanager.service.DashboardOverviewService;
import com.nbamanager.web.dto.DashboardOverviewDto;
import com.nbamanager.web.dto.DashboardStatsDto;
import com.nbamanager.web.dto.PlayerDto;
import com.nbamanager.web.support.PageRequestSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardOverviewService dashboardOverviewService;

    @GetMapping("/overview")
    public DashboardOverviewDto overview() {
        return dashboardOverviewService.buildOverview();
    }

    @GetMapping("/stats")
    public DashboardStatsDto stats() {
        return dashboardService.buildStats();
    }

    @GetMapping("/top-scorers")
    public Page<PlayerDto> topScorers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // page * size 是 int 运算，越界会静默溢出成负数并返回错误的一页，这里先夹紧
        int safePage = Math.max(0, Math.min(page, PageRequestSanitizer.MAX_PAGE_NUMBER));
        int safeSize = Math.max(1, Math.min(size, PageRequestSanitizer.MAX_PAGE_SIZE));
        return dashboardService.getTopScorers(safePage, safeSize);
    }
}
