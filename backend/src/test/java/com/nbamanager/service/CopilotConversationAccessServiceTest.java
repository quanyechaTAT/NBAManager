package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.UserCopilotConversation;
import com.nbamanager.repository.UserCopilotConversationRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * P0-1 归属判定三态（安全报告 §十二 V1 / 任务书验收）：
 * 归属命中（本人）/ 未命中（他人或匿名）/ ADMIN 对存量无归属会话的迁移期可见。
 */
class CopilotConversationAccessServiceTest {

    private UserCopilotConversationRepository repository;
    private CopilotConversationAccessService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserCopilotConversationRepository.class);
        service = new CopilotConversationAccessService(repository);
    }

    private void mapped(Long userId, String conversationId) {
        when(repository.existsByUserIdAndConversationId(userId, conversationId)).thenReturn(true);
        // 该会话已被登记归属（供 ADMIN 分支判定"是否存在归属"使用）
        when(repository.existsByConversationId(conversationId)).thenReturn(true);
    }

    @Test
    void ownedConversationAccessibleOnlyToOwner() {
        mapped(1L, "conv-A");

        assertThat(service.accessible(1L, false, "conv-A")).isTrue();
        // 他人（即使 ADMIN）不能访问已归属会话：归属判定不看 admin 位
        assertThat(service.accessible(2L, false, "conv-A")).isFalse();
        assertThat(service.accessible(2L, true, "conv-A")).isFalse();
    }

    @Test
    void unmappedLegacyConversationVisibleToAdminOnly() {
        when(repository.existsByUserIdAndConversationId(1L, "legacy")).thenReturn(false);
        when(repository.existsByConversationId("legacy")).thenReturn(false);

        assertThat(service.accessible(1L, false, "legacy")).isFalse();
        assertThat(service.accessible(1L, true, "legacy")).isTrue();
    }

    @Test
    void anonymousIsAlwaysDenied() {
        assertThat(service.accessible(null, false, "conv-A")).isFalse();
        assertThat(service.accessible(null, true, "legacy")).isFalse();
    }

    @Test
    void registerIsIdempotentPerUserAndConversation() {
        when(repository.existsByUserIdAndConversationId(1L, "conv-A")).thenReturn(true);

        service.register(1L, "conv-A");
        service.register(1L, null);      // 非法入参静默忽略
        service.register(null, "conv-A");

        verify(repository, Mockito.never()).save(any(UserCopilotConversation.class));
    }

    @Test
    void registerSavesWhenNotYetMapped() {
        when(repository.existsByUserIdAndConversationId(1L, "conv-B")).thenReturn(false);

        service.register(1L, "conv-B");

        verify(repository).save(any(UserCopilotConversation.class));
    }

    @Test
    void ownedIdsReturnsOnlyThatUsersMappings() {
        UserCopilotConversation mine = new UserCopilotConversation();
        mine.setUserId(1L);
        mine.setConversationId("conv-A");
        when(repository.findByUserId(1L)).thenReturn(List.of(mine));
        when(repository.findByUserId(2L)).thenReturn(List.of());

        assertThat(service.ownedIds(1L)).containsExactly("conv-A");
        assertThat(service.ownedIds(2L)).isEmpty();
        assertThat(service.ownedIds(null)).isEmpty();
    }
}
