package com.nbamanager.service;

import com.nbamanager.domain.GameNews;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.UserBrowseHistoryRepository;
import com.nbamanager.repository.UserFavoriteRepository;
import com.nbamanager.web.dto.GameNewsDto;
import com.nbamanager.web.dto.GameNewsRequest;
import com.nbamanager.web.support.PageRequestSanitizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GameNewsService {

    private final GameNewsRepository gameNewsRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final org.springframework.beans.factory.ObjectProvider<NewsListCache> newsListCache;
    private final UserBrowseHistoryRepository userBrowseHistoryRepository;

    /** 资讯列表允许的排序字段（白名单，未知字段返回 400 而非 500）。 */
    private static final Set<String> ALLOWED_SORTS = Set.of(
            "id", "title", "category", "viewCount", "favoriteCount",
            "gameStartTime", "createTime", "status");
    private static final Map<String, String> SORT_ALIASES = Map.of(
            "createdAt", "createTime",
            "updatedAt", "createTime",
            "startTime", "gameStartTime");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createTime");

    /**
     * 分页查询所有赛事资讯（第 8 轮 P1-4 改造）：
     * 用户无关基座来自 NewsListCache（85KB 大响应缓存，写后由仓库层切面失效），
     * 每用户 favoritedByMe 在基座上装饰（仍是每页一条批量收藏查询）。
     */
    public Page<GameNewsDto> list(String keyword, Long userId, Pageable pageable) {
        Pageable safePageable = PageRequestSanitizer.sanitize(
                pageable, ALLOWED_SORTS, SORT_ALIASES, DEFAULT_SORT);
        NewsListCache.PagePayload base = newsListCache.getObject().load(keyword, safePageable);

        List<Long> pageIds = base.content().stream().map(GameNewsDto::id).collect(Collectors.toList());
        Set<Long> favoritedIds = (userId == null || pageIds.isEmpty())
                ? Set.of()
                : new java.util.HashSet<>(userFavoriteRepository
                        .findTargetIdsByUserIdAndTargetTypeAndTargetIdIn(userId, "NEWS", pageIds));
        List<GameNewsDto> decorated = base.content().stream()
                .map(dto -> withFavoritedByMe(dto, favoritedIds.contains(dto.id())))
                .collect(Collectors.toList());
        return new org.springframework.data.domain.PageImpl<>(decorated, safePageable, base.totalElements());
    }

    /** 用户无关基座查询（NewsListCache 未命中时执行）。 */
    public NewsListCache.PagePayload queryNewsBasePage(String keyword, Pageable safePageable) {
        Page<GameNews> page;
        if (keyword == null || keyword.isBlank()) {
            page = gameNewsRepository.findAll(safePageable);
        } else {
            page = gameNewsRepository.search(keyword.trim(), safePageable);
        }
        List<GameNewsDto> base = page.getContent().stream()
                .map(g -> toDto(g, false))
                .collect(Collectors.toList());
        return new NewsListCache.PagePayload(base, page.getTotalElements());
    }

    /** GameNewsDto 是 record：按收藏标记复制一份（其余字段原样）。 */
    private static GameNewsDto withFavoritedByMe(GameNewsDto dto, boolean favorited) {
        if (dto.favoritedByMe() == favorited) {
            return dto;
        }
        return new GameNewsDto(dto.id(), dto.title(), dto.summary(), dto.content(),
                dto.homeTeam(), dto.awayTeam(), dto.homeScore(), dto.awayScore(),
                dto.gameStartTime(), dto.gameEndTime(), dto.status(), dto.createTime(),
                dto.nbaGameId(), dto.category(), dto.sourceUrl(), dto.imageUrl(),
                dto.viewCount(), dto.favoriteCount(), favorited);
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

    /** 收藏/取消收藏（原子化：以唯一键为准，计数走原子 UPDATE） */
    @Transactional
    public boolean toggleFavorite(Long userId, Long newsId) {
        gameNewsRepository.findById(newsId).orElseThrow(() -> notFound(newsId));
        // 资讯收藏与帖子收藏共用同一张 user_favorites 表，按同键串行化避免死锁
        return com.nbamanager.service.support.KeyedMutex.execute(
                "favorite:" + userId + ":NEWS:" + newsId,
                () -> {
                    int deleted = userFavoriteRepository.deleteByUserIdAndTargetTypeAndTargetId(
                            userId, "NEWS", newsId);
                    if (deleted > 0) {
                        gameNewsRepository.bumpFavoriteCount(newsId, -1);
                        return false;
                    }

                    int inserted = userFavoriteRepository.insertIgnore(userId, "NEWS", newsId);
                    if (inserted > 0) {
                        gameNewsRepository.bumpFavoriteCount(newsId, +1);
                        return true;
                    }

                    throw new ApiException(HttpStatus.CONFLICT, "收藏状态正在更新，请稍后重试");
                });
    }

    /** 查询今日赛事 */
    @Cacheable(value = "todayGames", key = "'today'")
    @Transactional(readOnly = true)
    public List<GameNewsDto> getTodayGames() {
        // 使用 Asia/Shanghai 时区判断"今天"，与存储时区一致
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        return gameNewsRepository.findTodayGames(start, end).stream()
                .map(g -> toDto(g, false))
                .collect(Collectors.toList());
    }

    /** 新增赛事资讯 */
    @CacheEvict(value = {"news", "todayGames", "dashboard"}, allEntries = true)
    @Transactional
    public GameNewsDto create(GameNewsRequest req) {
        GameNews g = new GameNews();
        apply(g, req);
        g.setCreateTime(LocalDateTime.now());
        GameNews saved = gameNewsRepository.save(g);
        cleanupOldNews(100);
        return toDto(saved, false);
    }

    /** 修改赛事资讯 */
    @CacheEvict(value = {"news", "todayGames", "dashboard"}, allEntries = true)
    @Transactional
    public GameNewsDto update(Long id, GameNewsRequest req) {
        GameNews g = gameNewsRepository.findById(id)
                .orElseThrow(() -> notFound(id));
        apply(g, req);
        return toDto(gameNewsRepository.save(g), false);
    }

    /** 删除赛事资讯 */
    @CacheEvict(value = {"news", "todayGames", "dashboard"}, allEntries = true)
    @Transactional
    public void delete(Long id) {
        if (!gameNewsRepository.existsById(id)) {
            throw notFound(id);
        }
        gameNewsRepository.deleteById(id);
    }

    /** 清空所有赛事资讯 */
    @CacheEvict(value = {"news", "todayGames", "dashboard"}, allEntries = true)
    @Transactional
    public int deleteAll() {
        long count = gameNewsRepository.count();
        gameNewsRepository.deleteAll();
        return (int) count;
    }

    /** 所有资讯类型共用一个保留上限，并批量清理关联数据。 */
    @CacheEvict(value = {"news", "todayGames", "dashboard"}, allEntries = true)
    @Transactional
    public int cleanupOldNews(int keepCount) {
        int boundedKeepCount = Math.max(0, keepCount);
        long excess = gameNewsRepository.count() - boundedKeepCount;
        if (excess <= 0) return 0;

        int deleteCount = (int) Math.min(excess, Integer.MAX_VALUE);
        List<Long> ids = gameNewsRepository.findOldestIds(PageRequest.of(0, deleteCount));
        if (ids.isEmpty()) return 0;

        userFavoriteRepository.deleteNewsTargetsByIds("NEWS", ids);
        userBrowseHistoryRepository.deleteNewsTargetsByIds("NEWS", ids);
        gameNewsRepository.deleteAllByIdInBatch(ids);
        return ids.size();
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
