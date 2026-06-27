package com.nbamanager.web;

import com.nbamanager.service.DraftService;
import com.nbamanager.web.dto.DraftPickDto;
import com.nbamanager.web.dto.DraftPickRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/drafts")
@RequiredArgsConstructor
public class DraftController {

    private final DraftService draftService;

    /** 按年份查询选秀记录 */
    @GetMapping
    public List<DraftPickDto> listByYear(@RequestParam(required = false) Integer year) {
        if (year == null) {
            year = java.time.LocalDate.now().getYear();
        }
        return draftService.listByYear(year);
    }

    /** 按球队查询选秀记录 */
    @GetMapping("/team/{teamName}")
    public List<DraftPickDto> listByTeam(@PathVariable String teamName) {
        return draftService.listByTeam(teamName);
    }

    /** 新增选秀记录（仅管理员） */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public DraftPickDto create(@Valid @RequestBody DraftPickRequest request) {
        return draftService.create(request);
    }

    /** 修改选秀记录（仅管理员） */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DraftPickDto update(@PathVariable Long id, @Valid @RequestBody DraftPickRequest request) {
        return draftService.update(id, request);
    }

    /** 删除选秀记录（仅管理员） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        draftService.delete(id);
    }
}
