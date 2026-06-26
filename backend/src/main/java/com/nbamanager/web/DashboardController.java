package com.nbamanager.web;

import com.nbamanager.service.DashboardService;
import com.nbamanager.web.dto.DashboardStatsDto;
import com.nbamanager.web.dto.PlayerDto;
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

    @GetMapping("/stats")
    public DashboardStatsDto stats() {
        return dashboardService.buildStats();
    }

    @GetMapping("/top-scorers")
    public Page<PlayerDto> topScorers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return dashboardService.getTopScorers(page, size);
    }
}
