package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.GameNews;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.UserBrowseHistoryRepository;
import com.nbamanager.repository.UserFavoriteRepository;
import com.nbamanager.web.dto.GameNewsRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class GameNewsRetentionTest {

    @Mock private GameNewsRepository newsRepository;
    @Mock private UserFavoriteRepository favoriteRepository;
    @Mock private UserBrowseHistoryRepository historyRepository;
    @Mock private org.springframework.beans.factory.ObjectProvider<NewsListCache> newsListCacheProvider;

    private GameNewsService service;

    @BeforeEach
    void setUp() {
        service = new GameNewsService(newsRepository, favoriteRepository, newsListCacheProvider,
                historyRepository);
    }

    @Test
    void cleanup_keepsExactlyLimitAcrossAllCategories() {
        when(newsRepository.count()).thenReturn(105L);
        when(newsRepository.findOldestIds(any(Pageable.class)))
                .thenReturn(List.of(1L, 2L, 3L, 4L, 5L));

        int deleted = service.cleanupOldNews(100);

        assertThat(deleted).isEqualTo(5);
        verify(favoriteRepository).deleteNewsTargetsByIds("NEWS", List.of(1L, 2L, 3L, 4L, 5L));
        verify(historyRepository).deleteNewsTargetsByIds("NEWS", List.of(1L, 2L, 3L, 4L, 5L));
        verify(newsRepository).deleteAllByIdInBatch(List.of(1L, 2L, 3L, 4L, 5L));
    }

    @Test
    void cleanup_doesNothingAtLimit() {
        when(newsRepository.count()).thenReturn(100L);

        assertThat(service.cleanupOldNews(100)).isZero();

        verify(newsRepository, never()).findOldestIds(any(Pageable.class));
        verify(newsRepository, never()).deleteAllByIdInBatch(any());
    }

    @Test
    void manualCreate_enforcesSameOneHundredRecordLimit() {
        when(newsRepository.save(any(GameNews.class))).thenAnswer(invocation -> {
            GameNews news = invocation.getArgument(0);
            news.setId(101L);
            return news;
        });
        when(newsRepository.count()).thenReturn(101L);
        when(newsRepository.findOldestIds(any(Pageable.class))).thenReturn(List.of(1L));

        service.create(request());

        verify(newsRepository).deleteAllByIdInBatch(List.of(1L));
    }

    private static GameNewsRequest request() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 20, 12, 0);
        return new GameNewsRequest(
                "title", "summary", "content", "湖人", "勇士",
                null, null, start, start.plusHours(2), "FINISHED",
                null, "general", "https://example.com/manual", null);
    }
}
