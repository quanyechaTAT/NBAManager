package com.nbamanager.web;

import com.nbamanager.service.PlayerDetailService;
import com.nbamanager.web.dto.PlayerCareerStatsDto;
import com.nbamanager.web.dto.PlayerDetailDto;
import com.nbamanager.web.dto.PlayerGameLogDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerDetailController {

    private final PlayerDetailService playerDetailService;

    @GetMapping("/{id}/detail")
    public PlayerDetailDto getDetail(@PathVariable Long id) {
        return playerDetailService.getDetail(id);
    }

    @GetMapping("/{id}/career")
    public List<PlayerCareerStatsDto> getCareerStats(@PathVariable Long id) {
        return playerDetailService.getCareerStats(id);
    }

    @GetMapping("/{id}/gamelog")
    public Page<PlayerGameLogDto> getGameLog(
            @PathVariable Long id,
            @RequestParam(required = false) String season,
            @PageableDefault(size = 10) Pageable pageable) {
        return playerDetailService.getGameLog(id, season, pageable);
    }
}
