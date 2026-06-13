package com.nbamanager.web;

import com.nbamanager.service.NbaDataService;
import com.nbamanager.service.PlayoffService;
import lombok.RequiredArgsConstructor;
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

    /** 同步季后赛数据（从API获取并导入数据库） */
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
