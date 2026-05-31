package com.nbamanager.service;

import com.nbamanager.domain.Team;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.web.dto.TeamDto;
import com.nbamanager.web.dto.TeamRankDto;
import com.nbamanager.web.dto.TeamRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;

    @Transactional(readOnly = true)
    public Page<TeamDto> list(String keyword, Pageable pageable) {
        Page<Team> page;
        if (keyword == null || keyword.isBlank()) {
            page = teamRepository.findAll(pageable);
        } else {
            String q = keyword.trim();
            page = teamRepository.findByNameContainingIgnoreCaseOrCityContainingIgnoreCase(q, q, pageable);
        }
        return page.map(this::toDto);
    }

    @Cacheable(value = "teams", key = "#id")
    @Transactional(readOnly = true)
    public TeamDto get(Long id) {
        return toDto(teamRepository.findById(id).orElseThrow(() -> notFound(id)));
    }

    @CacheEvict(value = {"teams", "rankings", "dashboard"}, allEntries = true)
    @Transactional
    public TeamDto create(TeamRequest req) {
        Team t = new Team();
        apply(t, req);
        return toDto(teamRepository.save(t));
    }

    @CacheEvict(value = {"teams", "rankings", "dashboard"}, allEntries = true)
    @Transactional
    public TeamDto update(Long id, TeamRequest req) {
        Team t = teamRepository.findById(id).orElseThrow(() -> notFound(id));
        apply(t, req);
        return toDto(teamRepository.save(t));
    }

    @CacheEvict(value = {"teams", "rankings", "dashboard"}, allEntries = true)
    @Transactional
    public void delete(Long id) {
        if (!teamRepository.existsById(id)) {
            throw notFound(id);
        }
        teamRepository.deleteById(id);
    }

    /** 东西部分区排名（按净胜场排序，含胜场差） */
    @Cacheable(value = "rankings", key = "'all'")
    @Transactional(readOnly = true)
    public Map<String, List<TeamRankDto>> getConferenceRankings() {
        List<Team> all = teamRepository.findAll();
        Map<String, List<Team>> grouped = all.stream()
                .collect(Collectors.groupingBy(Team::getConference));

        Map<String, List<TeamRankDto>> result = new java.util.LinkedHashMap<>();
        for (String conf : List.of("东部", "西部")) {
            List<Team> confTeams = grouped.getOrDefault(conf, List.of());
            // 按净胜场（wins - losses）降序排列
            List<Team> sorted = confTeams.stream()
                    .sorted(Comparator.comparingInt((Team t) -> t.getWins() - t.getLosses()).reversed())
                    .collect(Collectors.toList());
            if (sorted.isEmpty()) {
                result.put(conf, List.of());
                continue;
            }
            Team first = sorted.get(0);
            double firstGb = first.getWins() - first.getLosses();
            List<TeamRankDto> ranks = new ArrayList<>();
            for (int i = 0; i < sorted.size(); i++) {
                Team t = sorted.get(i);
                double gb = i == 0 ? 0.0 : (firstGb - (t.getWins() - t.getLosses())) / 2.0;
                ranks.add(new TeamRankDto(t.getName(), t.getWins(), t.getLosses(), gb, conf));
            }
            result.put(conf, ranks);
        }
        return result;
    }

    private void apply(Team t, TeamRequest req) {
        t.setName(req.name());
        t.setCity(req.city());
        t.setConference(req.conference());
        t.setWins(req.wins());
        t.setLosses(req.losses());
    }

    private TeamDto toDto(Team t) {
        return new TeamDto(t.getId(), t.getName(), t.getCity(), t.getConference(), t.getWins(), t.getLosses());
    }

    private static ApiException notFound(Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "球队不存在: " + id);
    }
}
