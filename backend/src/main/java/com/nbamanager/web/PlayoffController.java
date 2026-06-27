package com.nbamanager.web;

import com.nbamanager.domain.PlayoffMatchup;
import com.nbamanager.repository.PlayoffMatchupRepository;
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
    private final PlayoffMatchupRepository matchupRepo;

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

    /** 修正季后赛数据（临时端点） */
    @PostMapping("/fix-data")
    public ResponseEntity<Map<String, Object>> fixPlayoffData() {
        // 修正东部决赛：尼克斯 4-0 骑士
        matchupRepo.findById(239L).ifPresent(m -> {
            m.setTeam1Name("尼克斯");
            m.setTeam1Wins(4);
            m.setTeam2Name("骑士");
            m.setTeam2Wins(0);
            m.setWinnerName("尼克斯");
            matchupRepo.save(m);
        });

        // 修正总决赛：尼克斯 4-1 马刺
        matchupRepo.findById(241L).ifPresent(m -> {
            m.setTeam1Name("尼克斯");
            m.setTeam1Wins(4);
            m.setTeam2Name("马刺");
            m.setTeam2Wins(1);
            m.setWinnerName("尼克斯");
            matchupRepo.save(m);
        });

        return ResponseEntity.ok(Map.of("status", "ok", "message", "季后赛数据已修正"));
    }
}
