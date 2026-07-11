package com.nbamanager.repository;

import com.nbamanager.domain.ChatMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT m FROM ChatMessage m WHERE m.gameId = :gameId AND m.deleted = false " +
            "AND (:beforeId IS NULL OR m.id < :beforeId) ORDER BY m.id DESC")
    List<ChatMessage> findHistory(@Param("gameId") String gameId,
                                  @Param("beforeId") Long beforeId,
                                  Pageable pageable);

    Optional<ChatMessage> findByUserIdAndClientMessageId(Long userId, String clientMessageId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.userId = :userId AND m.createdAt > :since")
    long countRecentByUser(@Param("userId") Long userId, @Param("since") Instant since);

    @Modifying
    @Query(value = """
        DELETE FROM chat_messages
        WHERE id IN (
            SELECT id FROM (
                SELECT id FROM chat_messages
                WHERE created_at < :cutoff
                ORDER BY id
                LIMIT :limit
            ) t
        )
        """, nativeQuery = true)
    int deleteBatchBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
