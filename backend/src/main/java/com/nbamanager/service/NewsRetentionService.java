package com.nbamanager.service;

import com.nbamanager.domain.GameNews;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.UserBrowseHistoryRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Both import and CRUD use this policy. Favorites and game records are never auto-pruned. */
@Service
public class NewsRetentionService {
    private final GameNewsRepository news;
    private final UserBrowseHistoryRepository history;
    private final int days;
    private final int maxCount;

    public NewsRetentionService(GameNewsRepository news, UserBrowseHistoryRepository history,
            @Value("${app.news.retention-days:90}") int days,
            @Value("${app.news.retention-max-count:10000}") int maxCount) {
        if (days < 1 || maxCount < 1) throw new IllegalArgumentException("News retention limits must be positive");
        this.news = news; this.history = history; this.days = days; this.maxCount = maxCount;
    }

    @Scheduled(cron = "${app.news.retention-cron:0 15 3 * * *}", zone = "Asia/Shanghai")
    @Transactional
    public int cleanup() {
        return cleanupAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
    }

    @Transactional
    public int cleanupAt(LocalDateTime now) {
        int removed = 0;
        List<GameNews> batch;
        while (!(batch = news.findExpiredRetentionCandidates(now.minusDays(days), PageRequest.of(0, 500))).isEmpty()) {
            removed += remove(batch);
        }
        long excess = Math.max(0, news.countRetentionCandidates() - maxCount);
        while (excess > 0) {
            batch = news.findRetentionCandidates(PageRequest.of(0, (int) Math.min(500, excess)));
            if (batch.isEmpty()) break;
            int deleted = remove(batch);
            removed += deleted; excess -= deleted;
        }
        return removed;
    }

    private int remove(List<GameNews> batch) {
        // Candidate queries lock the news rows. Both favorite entry points acquire the same row lock.
        // A new favorite therefore either protects this row before selection or observes its deletion.
        var ids = batch.stream().map(GameNews::getId).toList();
        history.deleteNewsTargetsByIds("NEWS", ids);
        news.deleteAllInBatch(batch);
        return batch.size();
    }
}
