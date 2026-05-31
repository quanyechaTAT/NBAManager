package com.nbamanager.web;

import com.nbamanager.service.MatchDetailService;
import com.nbamanager.web.dto.BoxScoreDto;
import com.nbamanager.web.dto.PlayByPlayDto;
import com.nbamanager.web.dto.QuarterScoreDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/match-detail")
@RequiredArgsConstructor
public class MatchDetailController {

    private final MatchDetailService matchDetailService;

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
}
