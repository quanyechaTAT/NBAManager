package com.nbamanager.service;

import com.nbamanager.domain.RAGConversation;
import com.nbamanager.repository.RAGConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
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

    /** 保存或更新会话（设置用户ID） */
    @Transactional
    public RAGConversation saveOrUpdate(String sessionId, String title, String messagesJson, Long userId) {
        RAGConversation conv = repository.findBySessionId(sessionId)
                .orElse(new RAGConversation());
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
