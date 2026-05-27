package com.nbamanager.web;

import com.nbamanager.service.TeamService;
import com.nbamanager.web.dto.PageResponse;
import com.nbamanager.web.dto.TeamDto;
import com.nbamanager.web.dto.TeamRankDto;
import com.nbamanager.web.dto.TeamRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    public PageResponse<TeamDto> list(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return PageResponse.from(teamService.list(q, pageable));
    }

    /** 东西部分区排名 */
    @GetMapping("/rankings")
    public Map<String, List<TeamRankDto>> rankings() {
        return teamService.getConferenceRankings();
    }

    @GetMapping("/{id}")
    public TeamDto get(@PathVariable Long id) {
        return teamService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public TeamDto create(@Valid @RequestBody TeamRequest request) {
        return teamService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TeamDto update(@PathVariable Long id, @Valid @RequestBody TeamRequest request) {
        return teamService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        teamService.delete(id);
    }
}
