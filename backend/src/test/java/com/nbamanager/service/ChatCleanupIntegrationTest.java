package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nbamanager.domain.ChatMessage;
import com.nbamanager.domain.ChatMessageType;
import com.nbamanager.repository.ChatMessageRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class ChatCleanupIntegrationTest {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatCleanupBatchService batchService;

    @AfterEach
    void cleanup() {
        chatMessageRepository.deleteAll();
    }

    private ChatMessage createMessage(String gameId, Instant createdAt, boolean deleted) {
        ChatMessage msg = new ChatMessage();
        msg.setGameId(gameId);
        msg.setUserId(1L);
        msg.setUsername("testuser");
        msg.setMessageType(ChatMessageType.TEXT);
        msg.setContent("test message");
        msg.setClientMessageId("cid-" + System.nanoTime());
        msg.setCreatedAt(createdAt);
        msg.setDeleted(deleted);
        return chatMessageRepository.save(msg);
    }

    @Test
    void deleteBatchBefore_removesExpiredMessages() {
        Instant now = Instant.now();
        // 181 days old — should be deleted
        createMessage("game1", now.minus(181, ChronoUnit.DAYS), false);
        createMessage("game2", now.minus(200, ChronoUnit.DAYS), false);

        Instant cutoff = now.minus(180, ChronoUnit.DAYS);
        int deleted = batchService.deleteOneBatch(cutoff, 1000);

        assertThat(deleted).isEqualTo(2);
        assertThat(chatMessageRepository.count()).isZero();
    }

    @Test
    void deleteBatchBefore_retainsRecentMessages() {
        Instant now = Instant.now();
        // 179 days old — should be retained
        createMessage("game1", now.minus(179, ChronoUnit.DAYS), false);
        // 181 days old — should be deleted
        createMessage("game2", now.minus(181, ChronoUnit.DAYS), false);

        Instant cutoff = now.minus(180, ChronoUnit.DAYS);
        int deleted = batchService.deleteOneBatch(cutoff, 1000);

        assertThat(deleted).isEqualTo(1);
        assertThat(chatMessageRepository.count()).isEqualTo(1);
    }

    @Test
    void deleteBatchBefore_removesSoftDeletedMessages() {
        Instant now = Instant.now();
        // deleted=true and expired — should be physically deleted
        createMessage("game1", now.minus(181, ChronoUnit.DAYS), true);
        // deleted=false and expired — should also be deleted
        createMessage("game2", now.minus(181, ChronoUnit.DAYS), false);

        Instant cutoff = now.minus(180, ChronoUnit.DAYS);
        int deleted = batchService.deleteOneBatch(cutoff, 1000);

        assertThat(deleted).isEqualTo(2);
    }

    @Test
    void deleteBatchBefore_respectsLimit() {
        Instant now = Instant.now();
        for (int i = 0; i < 10; i++) {
            createMessage("game" + i, now.minus(200, ChronoUnit.DAYS), false);
        }

        Instant cutoff = now.minus(180, ChronoUnit.DAYS);
        int deleted = batchService.deleteOneBatch(cutoff, 3);

        assertThat(deleted).isEqualTo(3);
        assertThat(chatMessageRepository.count()).isEqualTo(7);
    }

    @Test
    void deleteBatchBefore_idempotentOnSecondRun() {
        Instant now = Instant.now();
        createMessage("game1", now.minus(181, ChronoUnit.DAYS), false);

        Instant cutoff = now.minus(180, ChronoUnit.DAYS);
        int first = batchService.deleteOneBatch(cutoff, 1000);
        int second = batchService.deleteOneBatch(cutoff, 1000);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }
}
