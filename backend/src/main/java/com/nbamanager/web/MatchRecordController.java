package com.nbamanager.web;

import com.nbamanager.service.MatchRecordService;
import com.nbamanager.web.dto.MatchRecordDto;
import com.nbamanager.web.dto.MatchRecordRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/match-records")
@RequiredArgsConstructor
public class MatchRecordController {

    private final MatchRecordService matchRecordService;

    /** 查询某支球队的所有比赛记录 */
    @GetMapping
    public List<MatchRecordDto> list(@RequestParam(required = false) String teamName) {
        if (teamName != null && !teamName.isBlank()) {
            return matchRecordService.getByTeam(teamName);
        }
        return matchRecordService.getAll();
    }

    /** 查询两队交锋记录 */
    @GetMapping("/head-to-head")
    public List<MatchRecordDto> headToHead(
            @RequestParam String team1,
            @RequestParam String team2) {
        return matchRecordService.getHeadToHead(team1, team2);
    }

    /** 新增比赛记录（管理员） */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public MatchRecordDto create(@Valid @RequestBody MatchRecordRequest request) {
        return matchRecordService.create(request);
    }

    /** 删除比赛记录（管理员） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        matchRecordService.delete(id);
    }
}
