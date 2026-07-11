package com.nbamanager.service;

import com.nbamanager.repository.ChatMessageRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatCleanupBatchService {

    private final ChatMessageRepository chatMessageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteOneBatch(Instant cutoff, int limit) {
        return chatMessageRepository.deleteBatchBefore(cutoff, limit);
    }
}
