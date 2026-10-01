package com.nbamanager.repository;

import com.nbamanager.domain.UserCopilotConversation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCopilotConversationRepository extends JpaRepository<UserCopilotConversation, Long> {

    boolean existsByUserIdAndConversationId(Long userId, String conversationId);

    boolean existsByConversationId(String conversationId);

    List<UserCopilotConversation> findByUserId(Long userId);

    void deleteByUserIdAndConversationId(Long userId, String conversationId);

    /** 全部已登记的会话 id（用于 ADMIN 识别"存量无归属"会话，见控制器注释）。 */
    List<UserCopilotConversation> findAll();
}
