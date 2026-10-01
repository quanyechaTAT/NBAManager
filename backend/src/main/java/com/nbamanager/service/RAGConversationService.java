package com.nbamanager.service;

import com.nbamanager.domain.RAGConversation;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.RAGConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RAGConversationService {

    private final RAGConversationRepository repository;

    /** 获取用户自己的会话列表 */
    public List<RAGConversation> listByUserId(Long userId) {
        return repository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    /** 获取用户自己的会话（按sessionId） */
    public Optional<RAGConversation> getBySessionId(String sessionId, Long userId) {
        return repository.findBySessionIdAndUserId(sessionId, userId);
    }

    /** 获取分享的会话（公开，不需要用户隔离） */
    public Optional<RAGConversation> getByShareId(String shareId) {
        return repository.findByShareId(shareId);
    }

    /**
     * GET 流式问答的多轮上下文注入：取会话最近 8 轮（16 条），单条截 1000 字。
     * 历史由服务端持久化，前端无需再回传 conversation_history。
     */
    public List<Map<String, String>> recentHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        return repository.findBySessionId(sessionId)
                .map(conv -> {
                    try {
                        org.json.JSONArray arr = new org.json.JSONArray(conv.getMessagesJson());
                        java.util.List<Map<String, String>> history = new java.util.ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            org.json.JSONObject item = arr.optJSONObject(i);
                            if (item == null) continue;
                            String role = item.optString("role", "");
                            String content = item.optString("content", "").trim();
                            if (!(role.equals("user") || role.equals("assistant")) || content.isEmpty()) continue;
                            if (content.length() > 1000) content = content.substring(0, 1000);
                            history.add(Map.of("role", role, "content", content));
                        }
                        int start = Math.max(0, history.size() - 16);
                        return history.subList(start, history.size());
                    } catch (Exception e) {
                        return List.<Map<String, String>>of();
                    }
                })
                .orElse(List.of());
    }

    private static final int MAX_CONVERSATIONS_PER_USER = 50;

    /** 每用户会话上限：新建时超限则自动清理最旧的会话（防会话表无界增长） */
    private void enforceConversationQuota(Long userId) {
        long count = repository.countByUserId(userId);
        if (count < MAX_CONVERSATIONS_PER_USER) {
            return;
        }
        int toDelete = (int) (count - MAX_CONVERSATIONS_PER_USER + 1);
        List<RAGConversation> oldest = repository.findByUserIdOrderByUpdatedAtAsc(userId);
        if (oldest.size() >= toDelete) {
            repository.deleteAll(oldest.subList(0, toDelete));
        }
    }

    /** 保存或更新会话（设置用户ID） */
    @Transactional
    public RAGConversation saveOrUpdate(String sessionId, String title, String messagesJson, Long userId) {
        // DB 列是 TEXT（64KB）：超限时给出明确的 413，而不是等 MySQL 截断报错
        // 被 DataIntegrityViolation 兜底成 409"存在关联数据或约束冲突"（安全 12.3 I3）
        if (messagesJson != null && messagesJson.length() > 60_000) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "会话内容过大，请清理历史消息后再保存");
        }
        RAGConversation conv = repository.findBySessionId(sessionId)
                .map(existing -> {
                    if (!userId.equals(existing.getUserId())) {
                        throw new AccessDeniedException("无权修改该对话");
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    enforceConversationQuota(userId);
                    return new RAGConversation();
                });
        conv.setSessionId(sessionId);
        conv.setUserId(userId);
        conv.setTitle(title);
        conv.setMessagesJson(messagesJson);
        return repository.save(conv);
    }

    /** 删除用户自己的会话 */
    @Transactional
    public boolean deleteBySessionId(String sessionId, Long userId) {
        Optional<RAGConversation> conv = repository.findBySessionIdAndUserId(sessionId, userId);
        if (conv.isPresent()) {
            repository.delete(conv.get());
            return true;
        }
        return false;
    }

    /** 生成分享ID（需要验证用户归属） */
    @Transactional
    public String generateShareId(String sessionId, Long userId) {
        RAGConversation conv = repository.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new RuntimeException("对话不存在或无权操作"));
        if (conv.getShareId() == null) {
            conv.setShareId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            repository.save(conv);
        }
        return conv.getShareId();
    }
}
