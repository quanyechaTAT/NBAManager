package com.nbamanager.service;

import com.nbamanager.domain.RAGConversation;
import com.nbamanager.repository.RAGConversationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RAGConversationServiceTest {

    @Mock
    private RAGConversationRepository repository;

    @InjectMocks
    private RAGConversationService service;

    @Test
    void restoredStreamHistoryIsBoundToTheCurrentUser() {
        when(repository.findBySessionIdAndUserId("foreign-session", 7L)).thenReturn(Optional.empty());
        assertThat(service.recentHistory("foreign-session", 7L)).isEmpty();
        verify(repository, never()).findBySessionId("foreign-session");
    }

    @Test
    void restoredStreamHistoryKeepsOnlyRecentNonEmptyTurns() {
        RAGConversation conversation = new RAGConversation();
        conversation.setMessagesJson("[{\"role\":\"user\",\"content\":\"分析马刺和尼克斯的比赛\"},{\"role\":\"assistant\",\"content\":\"2024-12-25比分117:114\"},{\"role\":\"assistant\",\"content\":\"\"}]");
        when(repository.findBySessionIdAndUserId("owned-session", 7L)).thenReturn(Optional.of(conversation));
        assertThat(service.recentHistory("owned-session", 7L)).hasSize(2);
    }

    @Test
    void saveOrUpdateCreatesConversationForCurrentUser() {
        when(repository.findBySessionId("session-1")).thenReturn(Optional.empty());
        when(repository.save(any(RAGConversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RAGConversation saved = service.saveOrUpdate("session-1", "title", "[{\"role\":\"user\"}]", 7L);

        assertThat(saved.getSessionId()).isEqualTo("session-1");
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getTitle()).isEqualTo("title");
        assertThat(saved.getMessagesJson()).isEqualTo("[{\"role\":\"user\"}]");
        verify(repository).save(saved);
    }

    @Test
    void saveOrUpdateUpdatesConversationOwnedByCurrentUser() {
        RAGConversation existing = new RAGConversation();
        existing.setSessionId("session-1");
        existing.setUserId(7L);
        existing.setTitle("old");
        existing.setMessagesJson("[]");
        when(repository.findBySessionId("session-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(RAGConversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RAGConversation saved = service.saveOrUpdate("session-1", "new", "[{\"role\":\"assistant\"}]", 7L);

        assertThat(saved).isSameAs(existing);
        assertThat(existing.getTitle()).isEqualTo("new");
        assertThat(existing.getMessagesJson()).isEqualTo("[{\"role\":\"assistant\"}]");
        verify(repository).save(existing);
    }

    @Test
    void saveOrUpdateRejectsSessionOwnedByAnotherUser() {
        RAGConversation existing = new RAGConversation();
        existing.setSessionId("shared-session-id");
        existing.setUserId(11L);
        existing.setTitle("owner conversation");
        when(repository.findBySessionId("shared-session-id")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.saveOrUpdate(
                "shared-session-id", "attacker title", "[]", 22L
        )).isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(existing);
    }

    @Test
    void deleteBySessionIdDeletesOnlyOwnedConversation() {
        RAGConversation existing = new RAGConversation();
        existing.setSessionId("session-1");
        existing.setUserId(7L);
        when(repository.findBySessionIdAndUserId("session-1", 7L)).thenReturn(Optional.of(existing));

        assertThat(service.deleteBySessionId("session-1", 7L)).isTrue();

        verify(repository).delete(existing);
    }

    @Test
    void deleteBySessionIdReturnsFalseForMissingOrForeignConversation() {
        when(repository.findBySessionIdAndUserId("session-1", 7L)).thenReturn(Optional.empty());

        assertThat(service.deleteBySessionId("session-1", 7L)).isFalse();

        verify(repository, never()).delete(any(RAGConversation.class));
    }

    @Test
    void generateShareIdCreatesShareIdForOwnedConversation() {
        RAGConversation existing = new RAGConversation();
        existing.setSessionId("session-1");
        existing.setUserId(7L);
        when(repository.findBySessionIdAndUserId("session-1", 7L)).thenReturn(Optional.of(existing));

        String shareId = service.generateShareId("session-1", 7L);

        assertThat(shareId).hasSize(16);
        assertThat(existing.getShareId()).isEqualTo(shareId);
        verify(repository).save(existing);
    }

    @Test
    void generateShareIdReusesExistingShareIdWithoutSavingAgain() {
        RAGConversation existing = new RAGConversation();
        existing.setSessionId("session-1");
        existing.setUserId(7L);
        existing.setShareId("existing-share-id");
        when(repository.findBySessionIdAndUserId("session-1", 7L)).thenReturn(Optional.of(existing));

        assertThat(service.generateShareId("session-1", 7L)).isEqualTo("existing-share-id");

        verify(repository, never()).save(any(RAGConversation.class));
    }
}
