package com.nbamanager.service;

import com.nbamanager.domain.GameNews;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.UserBrowseHistoryRepository;
import com.nbamanager.repository.UserFavoriteRepository;
import com.nbamanager.web.dto.GameNewsDto;
import com.nbamanager.web.dto.GameNewsRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
public class GameNewsService {

    private final GameNewsRepository gameNewsRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final UserBrowseHistoryRepository userBrowseHistoryRepository;

    /** 分页查询所有赛事资讯 */
    @Transactional(readOnly = true)
    public Page<GameNewsDto> list(String keyword, Long userId, Pageable pageable) {
        Page<GameNews> page;
        if (keyword == null || keyword.isBlank()) {
            page = gameNewsRepository.findAll(pageable);
        } else {
            page = gameNewsRepository.search(keyword.trim(), pageable);
        }
        return page.map(g -> toDto(g, userId != null && userFavoriteRepository.existsByUserIdAndTargetTypeAndTargetId(userId, "NEWS", g.getId())));
    }

    /** 查询单条资讯 */
    @Transactional
    public GameNewsDto get(Long id, Long userId) {
        GameNews g = gameNewsRepository.findById(id)
                .orElseThrow(() -> notFound(id));
        g.setViewCount(g.getViewCount() + 1);
        gameNewsRepository.save(g);
        boolean favorited = userId != null && userFavoriteRepository.existsByUserIdAndTargetTypeAndTargetId(userId, "NEWS", id);
        return toDto(g, favorited);
    }

    /** 收藏/取消收藏 */
    @Transactional
    public boolean toggleFavorite(Long userId, Long newsId) {
        gameNewsRepository.findById(newsId).orElseThrow(() -> notFound(newsId));
        if (userFavoriteRepository.existsByUserIdAndTargetTypeAndTargetId(userId, "NEWS", newsId)) {
            userFavoriteRepository.deleteByUserIdAndTargetTypeAndTargetId(userId, "NEWS", newsId);
            GameNews g = gameNewsRepository.findById(newsId).orElse(null);
            if (g != null) { g.setFavoriteCount(Math.max(0, g.getFavoriteCount() - 1)); gameNewsRepository.save(g); }
            return false;
        } else {
            com.nbamanager.domain.UserFavorite fav = new com.nbamanager.domain.UserFavorite();
            fav.setUserId(userId);
            fav.setTargetType("NEWS");
            fav.setTargetId(newsId);
            userFavoriteRepository.save(fav);
            GameNews g = gameNewsRepository.findById(newsId).orElse(null);
            if (g != null) { g.setFavoriteCount(g.getFavoriteCount() + 1); gameNewsRepository.save(g); }
            return true;
        }
    }

    /** 查询今日赛事 */
    @Cacheable(value = "todayGames", key = "'today'")
    @Transactional(readOnly = true)
    public List<GameNewsDto> getTodayGames() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        return gameNewsRepository.findTodayGames(start, end).stream()
                .map(g -> toDto(g, false))
                .collect(Collectors.toList());
    }

    /** 新增赛事资讯 */
    @CacheEvict(value = {"news", "todayGames"}, allEntries = true)
    @Transactional
    public GameNewsDto create(GameNewsRequest req) {
        GameNews g = new GameNews();
        apply(g, req);
        g.setCreateTime(LocalDateTime.now());
        return toDto(gameNewsRepository.save(g), false);
    }

    /** 修改赛事资讯 */
    @CacheEvict(value = {"news", "todayGames"}, allEntries = true)
    @Transactional
    public GameNewsDto update(Long id, GameNewsRequest req) {
        GameNews g = gameNewsRepository.findById(id)
                .orElseThrow(() -> notFound(id));
        apply(g, req);
        return toDto(gameNewsRepository.save(g), false);
    }

    /** 删除赛事资讯 */
    @CacheEvict(value = {"news", "todayGames"}, allEntries = true)
    @Transactional
    public void delete(Long id) {
        if (!gameNewsRepository.existsById(id)) {
            throw notFound(id);
        }
        gameNewsRepository.deleteById(id);
    }

    /** 清空所有赛事资讯 */
    @CacheEvict(value = {"news", "todayGames"}, allEntries = true)
    @Transactional
    public int deleteAll() {
        long count = gameNewsRepository.count();
        gameNewsRepository.deleteAll();
        return (int) count;
    }

    /** 清理超出100条的旧资讯，同步删除浏览历史和收藏 */
    @CacheEvict(value = {"news", "todayGames"}, allEntries = true)
    @Transactional
    public int cleanupOldNews(int keepCount) {
        List<GameNews> allNews = gameNewsRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createTime"));
        if (allNews.size() <= keepCount) return 0;

        List<GameNews> toDelete = allNews.subList(keepCount, allNews.size());
        int deleted = 0;
        for (GameNews news : toDelete) {
            Long newsId = news.getId();
            // 删除相关的浏览历史
            try {
                userBrowseHistoryRepository.deleteByTargetTypeAndTargetId("NEWS", newsId);
            } catch (Exception e) { /* ignore */ }
            // 删除相关的收藏
            try {
                userFavoriteRepository.deleteByTargetTypeAndTargetId("NEWS", newsId);
            } catch (Exception e) { /* ignore */ }
            gameNewsRepository.delete(news);
            deleted++;
        }
        return deleted;
    }

    private void apply(GameNews g, GameNewsRequest req) {
        g.setTitle(req.title());
        g.setSummary(req.summary());
        g.setContent(req.content());
        g.setHomeTeam(req.homeTeam());
        g.setAwayTeam(req.awayTeam());
        g.setHomeScore(req.homeScore());
        g.setAwayScore(req.awayScore());
        g.setGameStartTime(req.gameStartTime());
        g.setGameEndTime(req.gameEndTime());
        if (req.status() != null && !req.status().isEmpty()) {
            g.setStatus(req.status());
        }
        g.setNbaGameId(req.nbaGameId());
        if (req.category() != null) {
            g.setCategory(req.category());
        }
        if (req.sourceUrl() != null) {
            g.setSourceUrl(req.sourceUrl());
        }
        if (req.imageUrl() != null) {
            g.setImageUrl(req.imageUrl());
        }
    }

    /**
     * 根据当前时间自动计算比赛状态：
     * - 当前时间 < 开始时间 → SCHEDULED（未开始）
     * - 开始时间 <= 当前时间 <= 结束时间 → LIVE（进行中）
     * - 当前时间 > 结束时间 → FINISHED（已结束）
     */
    private String calcStatus(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(startTime)) return "SCHEDULED";
        if (now.isAfter(endTime)) return "FINISHED";
        return "LIVE";
    }

    private GameNewsDto toDto(GameNews g, boolean favoritedByMe) {
        String status = (g.getStatus() != null && !g.getStatus().isEmpty())
                ? g.getStatus()
                : calcStatus(g.getGameStartTime(), g.getGameEndTime());
        return new GameNewsDto(
                g.getId(), g.getTitle(), g.getSummary(), g.getContent(),
                g.getHomeTeam(), g.getAwayTeam(), g.getHomeScore(), g.getAwayScore(),
                g.getGameStartTime(), g.getGameEndTime(),
                status,
                g.getCreateTime(),
                g.getNbaGameId(),
                g.getCategory(),
                g.getSourceUrl(),
                g.getImageUrl(),
                g.getViewCount() != null ? g.getViewCount() : 0,
                g.getFavoriteCount() != null ? g.getFavoriteCount() : 0,
                favoritedByMe);
    }

    private static ApiException notFound(Long id) {
        throw new ApiException(HttpStatus.NOT_FOUND, "赛事资讯不存在: " + id);
    }
}
