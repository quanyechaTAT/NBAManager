package com.nbamanager.web;

import com.nbamanager.service.PlayerService;
import com.nbamanager.web.dto.PageResponse;
import com.nbamanager.web.dto.PlayerCompareDto;
import com.nbamanager.web.dto.PlayerDetailDto;
import com.nbamanager.web.dto.PlayerDto;
import com.nbamanager.web.dto.PlayerRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;
    private final com.nbamanager.service.PlayerDetailService playerDetailService;

    @GetMapping
    public PageResponse<PlayerDto> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) String position,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return PageResponse.from(playerService.list(q, teamId, position, pageable));
    }

    @GetMapping("/{id}")
    public PlayerDto get(@PathVariable Long id) {
        return playerService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PlayerDto create(@Valid @RequestBody PlayerRequest request) {
        return playerService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PlayerDto update(@PathVariable Long id, @Valid @RequestBody PlayerRequest request) {
        return playerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        playerService.delete(id);
    }

    @GetMapping("/compare")
    public PlayerCompareDto compare(
            @RequestParam Long id1,
            @RequestParam Long id2) {
        PlayerDetailDto p1 = playerDetailService.getDetail(id1);
        PlayerDetailDto p2 = playerDetailService.getDetail(id2);
        return new PlayerCompareDto(p1, p2);
    }
}
