package com.nbamanager.web;

import com.nbamanager.service.ShotChartService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shot-chart")
@RequiredArgsConstructor
public class ShotChartController {

    private final ShotChartService shotChartService;

    /**
     * 获取球员投篮数据
     */
    @GetMapping("/player/{playerId}")
    public ResponseEntity<Map<String, Object>> getPlayerShots(
            @PathVariable Long playerId,
            @RequestParam(defaultValue = "2025-26") String season) {
        Map<String, Object> result = shotChartService.getPlayerShots(playerId, season);
        return ResponseEntity.ok(result);
    }

    /**
     * 清除球员投篮缓存（管理员）
     */
    @DeleteMapping("/player/{playerId}/cache")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> clearCache(
            @PathVariable Long playerId,
            @RequestParam(defaultValue = "2025-26") String season) {
        // 需要先获取nbaPlayerId
        shotChartService.clearCache(playerId, season);
        return ResponseEntity.ok(Map.of(
                "message", "缓存已清除",
                "status", "success"
        ));
    }
}
