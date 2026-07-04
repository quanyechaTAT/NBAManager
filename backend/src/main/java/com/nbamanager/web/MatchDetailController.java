package com.nbamanager.web;

import com.nbamanager.domain.PlayByPlaySyncLog;
import com.nbamanager.service.MatchDetailService;
import com.nbamanager.service.PlayByPlayCollectorService;
import com.nbamanager.service.PlayByPlaySyncLogService;
import com.nbamanager.service.SmartDataService;
import com.nbamanager.web.dto.BoxScoreDto;
import com.nbamanager.web.dto.PlayByPlayDto;
import com.nbamanager.web.dto.QuarterScoreDto;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/match-detail")
@RequiredArgsConstructor
@Slf4j
public class MatchDetailController {

    private final MatchDetailService matchDetailService;
    private final SmartDataService smartDataService;
    private final PlayByPlayCollectorService collector;
    private final PlayByPlaySyncLogService syncLogService;

    @GetMapping("/{gameId}/boxscore")
    public BoxScoreDto getBoxScore(@PathVariable String gameId) {
        return matchDetailService.getBoxScore(gameId);
    }

    @GetMapping("/{gameId}/playbyplay")
    public List<PlayByPlayDto> getPlayByPlay(
            @PathVariable String gameId,
            @RequestParam(required = false) Integer period) {
        return matchDetailService.getPlayByPlay(gameId, period);
    }

    @GetMapping("/{gameId}/quarters")
    public List<QuarterScoreDto> getQuarterScores(@PathVariable String gameId) {
        return matchDetailService.getQuarterScores(gameId);
    }

    @PostMapping("/{gameId}/sync")
    public Map<String, Object> syncPlayByPlay(@PathVariable String gameId) {
        PlayByPlayCollectorService.CollectResult result = collector.collect(gameId);
        return Map.of(
            "success", result.success(),
            "source", result.source() != null ? result.source() : "",
            "eventCount", result.eventCount(),
            "message", result.message()
        );
    }

    @GetMapping("/{gameId}/sync-status")
    public PlayByPlaySyncLog getSyncStatus(@PathVariable String gameId) {
        return syncLogService.getLatestLog(gameId).orElse(null);
    }
}
