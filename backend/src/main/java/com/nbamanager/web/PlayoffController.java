package com.nbamanager.web;

import com.nbamanager.service.NbaDataService;
import com.nbamanager.service.PlayoffService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/playoff")
@RequiredArgsConstructor
public class PlayoffController {

    private final PlayoffService playoffService;
    private final NbaDataService nbaDataService;

    /** 获取季后赛对阵图 */
    @GetMapping("/bracket")
    public ResponseEntity<Map<String, Object>> getPlayoffBracket(
            @RequestParam(required = false, defaultValue = "2024-25") String season) {
        Map<String, Object> bracket = playoffService.getPlayoffBracket(season);
        return ResponseEntity.ok(bracket);
    }

    /** 获取可用赛季列表 */
    @GetMapping("/seasons")
    public ResponseEntity<List<String>> getAvailableSeasons() {
        return ResponseEntity.ok(playoffService.getAvailableSeasons());
    }

    /** 获取系列赛详情 */
    @GetMapping("/matchup/{id}")
    public ResponseEntity<Map<String, Object>> getMatchupDetail(@PathVariable Long id) {
        return ResponseEntity.ok(playoffService.getMatchupDetail(id));
    }

    /** 修正季后赛数据（运维修复端点，仅管理员） */
    @PostMapping("/fix-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> fixPlayoffData(
            @RequestParam(required = false, defaultValue = "2025-26") String season) {
        Map<String, Object> result = nbaDataService.fetchAndImportPlayoffData(season);
        result.put("status", "ok");
        result.put("message", "季后赛数据已按参考对阵图修正");
        return ResponseEntity.ok(result);
    }
}
