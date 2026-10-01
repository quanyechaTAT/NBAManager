package com.nbamanager.service;

import com.nbamanager.domain.UserCopilotConversation;
import com.nbamanager.repository.UserCopilotConversationRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Copilot 会话归属判定（安全报告 §十二 V1）。
 *
 * python 端是全局单用户存储，归属只能建在 java 层：
 * chat 产生/续用会话时登记 (userId, conversationId)，conversations 系列端点据此鉴权。
 *
 * 判定规则：
 * - 已登记归属：仅归属本人可访问（含 ADMIN——他人会话内容对 ADMIN 也不可见，避免权限放大）；
 * - 无归属记录（存量会话/迁移期）：仅 ADMIN 可访问（任务书 P0-1 允许的迁移兜底，二选一取此项）；
 * - userId 为 null（匿名）：一律拒绝。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CopilotConversationAccessService {

    private final UserCopilotConversationRepository repository;

    /** 登记归属（幂等）：并发重复登记由唯一约束兜底，冲突视为已存在。 */
    @Transactional
    public void register(Long userId, String conversationId) {
        if (userId == null || conversationId == null || conversationId.isBlank()) {
            return;
        }
        if (repository.existsByUserIdAndConversationId(userId, conversationId)) {
            return;
        }
        UserCopilotConversation mapping = new UserCopilotConversation();
        mapping.setUserId(userId);
        mapping.setConversationId(conversationId);
        mapping.setCreatedAt(LocalDateTime.now());
        try {
            repository.save(mapping);
        } catch (DataIntegrityViolationException e) {
            // 并发登记同一归属：唯一约束已保证只有一行，视为成功
            log.debug("Copilot 会话归属并发登记冲突（幂等忽略）: userId={}, conversationId={}",
                    userId, conversationId);
        }
    }

    /** 会话对 (userId, admin) 是否可访问。 */
    public boolean accessible(Long userId, boolean admin, String conversationId) {
        if (userId == null || conversationId == null || conversationId.isBlank()) {
            return false;
        }
        boolean owned = repository.existsByUserIdAndConversationId(userId, conversationId);
        if (owned) {
            return true;
        }
        // 未登记：只有 ADMIN 可触达存量会话（迁移期兜底）
        return admin && !repository.existsByConversationId(conversationId);
    }

    /** 当前用户拥有的会话 id 集合（列表过滤用）。 */
    public Set<String> ownedIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (UserCopilotConversation mapping : repository.findByUserId(userId)) {
            ids.add(mapping.getConversationId());
        }
        return ids;
    }

    /** 全部已登记的会话 id（ADMIN 过滤"存量无归属"会话时求补集用）。 */
    public Set<String> allMappedIds() {
        Set<String> ids = new HashSet<>();
        for (UserCopilotConversation mapping : repository.findAll()) {
            ids.add(mapping.getConversationId());
        }
        return ids;
    }

    /** 会话删除成功后移除归属登记（python 端已不存在，映射不应残留）。 */
    @Transactional
    public void remove(Long userId, String conversationId) {
        if (userId == null || conversationId == null) {
            return;
        }
        repository.deleteByUserIdAndConversationId(userId, conversationId);
    }

    /** 供测试/诊断：某用户全部归属行。 */
    public List<UserCopilotConversation> mappingsOf(Long userId) {
        return repository.findByUserId(userId);
    }
}
