package com.nbamanager.web;

import com.nbamanager.service.NbaDataService;
import com.nbamanager.service.PlayoffService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nba")
@RequiredArgsConstructor
public class NbaDataController {

    private final NbaDataService nbaDataService;
    private final PlayoffService playoffService;

    /** 获取季后赛对阵图（从数据库） */
    @GetMapping("/playoff")
    public ResponseEntity<Map<String, Object>> getPlayoffBracket(
            @RequestParam(required = false) String season) {
        if (season == null || season.isEmpty()) {
            season = getCurrentSeason();
        }
        Map<String, Object> bracket = playoffService.getPlayoffBracket(season);
        return ResponseEntity.ok(bracket);
    }

    /**
     * 同步季后赛数据（从外部 API 获取并导入数据库）。
     *
     * 仅限 ADMIN（安全报告 §十三 H7）：该端点触发 python 子进程 + 外部 API 拉取 +
     * 数据库批量写入，属运维操作；AdminController 系列同步端点均已限 ADMIN，
     * 此前唯独本端点漏网（SecurityConfig 仅路由级 authenticated）。
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/playoff/sync")
    public ResponseEntity<Map<String, Object>> syncPlayoffData(
            @RequestParam(required = false) String season) {
        if (season == null || season.isEmpty()) {
            season = getCurrentSeason();
        }
        Map<String, Object> result = nbaDataService.fetchAndImportPlayoffData(season);
        return ResponseEntity.ok(result);
    }

    /** 获取当前赛季（10月前为上赛季，10月后为本赛季） */
    private String getCurrentSeason() {
        java.time.LocalDate now = java.time.LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        if (month >= 10) {
            return year + "-" + String.valueOf(year + 1).substring(2);
        } else {
            return (year - 1) + "-" + String.valueOf(year).substring(2);
        }
    }

    /** 获取可用赛季列表 */
    @GetMapping("/playoff/seasons")
    public ResponseEntity<List<String>> getPlayoffSeasons() {
        return ResponseEntity.ok(playoffService.getAvailableSeasons());
    }
}
