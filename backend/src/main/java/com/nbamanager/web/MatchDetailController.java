package com.nbamanager.web;

import com.nbamanager.service.MatchDetailService;
import com.nbamanager.service.SmartDataService;
import com.nbamanager.web.dto.BoxScoreDto;
import com.nbamanager.web.dto.PlayByPlayDto;
import com.nbamanager.web.dto.QuarterScoreDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/{gameId}/boxscore")
    public Object getBoxScore(@PathVariable String gameId) {
        // 优先从API实时获取
        JSONObject realtimeData = smartDataService.getMatchDetailRealtime(gameId);
        if (realtimeData != null && realtimeData.has("boxscore")) {
            return realtimeData.get("boxscore");
        }
        // 降级：从数据库读取
        return matchDetailService.getBoxScore(gameId);
    }

    @GetMapping("/{gameId}/playbyplay")
    public List<PlayByPlayDto> getPlayByPlay(
            @PathVariable String gameId,
            @RequestParam(required = false) Integer period) {
        return matchDetailService.getPlayByPlay(gameId, period);
    }

    @GetMapping("/{gameId}/quarters")
    public Object getQuarterScores(@PathVariable String gameId) {
        // 优先从API实时获取
        JSONObject realtimeData = smartDataService.getQuarterScoresRealtime(gameId);
        if (realtimeData != null && realtimeData.has("quarterScores")) {
            return realtimeData.get("quarterScores");
        }
        // 降级：从数据库读取
        return matchDetailService.getQuarterScores(gameId);
    }
}
