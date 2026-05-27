package com.nbamanager.web;

import com.nbamanager.service.GameNewsService;
import com.nbamanager.web.dto.GameNewsDto;
import com.nbamanager.web.dto.GameNewsRequest;
import com.nbamanager.web.dto.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class GameNewsController {

    private final GameNewsService gameNewsService;

    /** 分页列表（所有认证用户均可查看） */
    @GetMapping
    public PageResponse<GameNewsDto> list(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 10, sort = "gameTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(gameNewsService.list(q, pageable));
    }

    /** 单条详情 */
    @GetMapping("/{id}")
    public GameNewsDto get(@PathVariable Long id) {
        return gameNewsService.get(id);
    }

    /** 今日赛事 */
    @GetMapping("/today")
    public List<GameNewsDto> today() {
        return gameNewsService.getTodayGames();
    }

    /** 新增赛事资讯（仅管理员） */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public GameNewsDto create(@Valid @RequestBody GameNewsRequest request) {
        return gameNewsService.create(request);
    }

    /** 修改赛事资讯（仅管理员） */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public GameNewsDto update(@PathVariable Long id, @Valid @RequestBody GameNewsRequest request) {
        return gameNewsService.update(id, request);
    }

    /** 删除赛事资讯（仅管理员） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        gameNewsService.delete(id);
    }
}
