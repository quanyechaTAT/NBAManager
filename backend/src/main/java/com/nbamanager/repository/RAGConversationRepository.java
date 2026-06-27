package com.nbamanager.repository;

import com.nbamanager.domain.RAGConversation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RAGConversationRepository extends JpaRepository<RAGConversation, Long> {

    Optional<RAGConversation> findBySessionId(String sessionId);

    Optional<RAGConversation> findBySessionIdAndUserId(String sessionId, Long userId);

    Optional<RAGConversation> findByShareId(String shareId);

    List<RAGConversation> findAllByOrderByUpdatedAtDesc();

    List<RAGConversation> findByUserIdOrderByUpdatedAtDesc(Long userId);

    boolean existsBySessionId(String sessionId);
}
