package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.GameNews;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.UserBrowseHistoryRepository;
import com.nbamanager.repository.UserFavoriteRepository;
import java.util.List;
import org.json.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class NewsPersistenceServiceTest {

    @Mock
    private GameNewsRepository repository;

    @Mock
    private UserFavoriteRepository favoriteRepository;

    @Mock
    private UserBrowseHistoryRepository historyRepository;

    @Mock private NewsRetentionService retention;

    private NewsPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new NewsPersistenceService(repository, retention);
        when(repository.findBySourceUrlIn(anyCollection())).thenReturn(List.of());
        when(repository.findByTitleIn(anyCollection())).thenReturn(List.of());
        when(repository.findByTitleEnIn(anyCollection())).thenReturn(List.of());
        when(repository.saveAll(org.mockito.ArgumentMatchers.<List<GameNews>>any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void persist_skipsDatabaseAndBatchDuplicatesAndSavesOnce() {
        GameNews existing = new GameNews();
        existing.setSourceUrl("https://example.com/existing");
        when(repository.findBySourceUrlIn(anyCollection())).thenReturn(List.of(existing));

        JSONArray candidates = new JSONArray()
                .put(candidate("Existing", "https://example.com/existing"))
                .put(candidate("Brand New", "https://example.com/new?utm_source=rss"))
                .put(candidate("Brand New duplicate", "https://example.com/new"));

        NewsPersistenceService.PersistResult result = service.persist(candidates);

        assertThat(result.fetched()).isEqualTo(3);
        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(2);

        ArgumentCaptor<List<GameNews>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getTitle()).isEqualTo("Brand New");
    }

    @Test
    void persist_mapsAllNewCandidatesAndUsesSingleBatchSave() {
        JSONArray candidates = new JSONArray()
                .put(candidate("First", "https://example.com/first"))
                .put(candidate("Second", "https://example.com/second"));

        NewsPersistenceService.PersistResult result = service.persist(candidates);

        assertThat(result).isEqualTo(new NewsPersistenceService.PersistResult(2, 2, 0));
        ArgumentCaptor<List<GameNews>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(GameNews::getTitle)
                .containsExactly("First", "Second");
    }

    @Test
    void persist_allowsDistinctArticlesForSameGameId() {
        JSONArray candidates = new JSONArray()
                .put(candidate("Fresh injury update", "https://example.com/fresh-injury")
                        .put("nbaGameId", "401000001"));

        NewsPersistenceService.PersistResult result = service.persist(candidates);

        assertThat(result).isEqualTo(new NewsPersistenceService.PersistResult(1, 1, 0));
        ArgumentCaptor<List<GameNews>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(GameNews::getTitle)
                .containsExactly("Fresh injury update");
    }

    @Test
    void persist_trimsOldestRowsInsidePersistenceOperation() {
        service.persist(new JSONArray()
                .put(candidate("Newest", "https://example.com/newest")));

        verify(retention).cleanup();
        org.mockito.Mockito.verifyNoInteractions(favoriteRepository, historyRepository);
    }

    private static org.json.JSONObject candidate(String headline, String sourceUrl) {
        return new org.json.JSONObject()
                .put("headline", headline)
                .put("headlineEn", headline)
                .put("description", headline + " summary")
                .put("content", headline + " content")
                .put("contentEn", headline + " content")
                .put("sourceUrl", sourceUrl)
                .put("published", "2026-07-20T08:00:00Z")
                .put("category", "general")
                .put("homeTeam", "")
                .put("awayTeam", "")
                .put("nbaGameId", "");
    }
}
