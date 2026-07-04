package com.nbamanager.repository;

import com.nbamanager.domain.AiConversationMemory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiConversationMemoryRepository extends JpaRepository<AiConversationMemory, Long> {
    Optional<AiConversationMemory> findByConversationId(String conversationId);
}
