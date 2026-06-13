package com.nbamanager.web;

import com.nbamanager.service.PlayoffService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/playoff")
@RequiredArgsConstructor
public class PlayoffController {

    private final PlayoffService playoffService;

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
}
