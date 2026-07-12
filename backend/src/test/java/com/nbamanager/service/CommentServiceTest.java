package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.Comment;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.CommentLikeRepository;
import com.nbamanager.repository.CommentRepository;
import com.nbamanager.repository.PostRepository;
import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.web.dto.CommentRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private CommentLikeRepository commentLikeRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private NotificationService notificationService;

    private CommentService service;
    private Comment comment;

    @BeforeEach
    void setUp() {
        service = new CommentService(commentRepository, commentLikeRepository, postRepository,
                userAccountRepository, notificationService);
        comment = new Comment();
        comment.setId(10L);
        comment.setUserId(7L);
        comment.setGameId("game-1");
        comment.setContent("before");
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
    }

    @Test
    void authorCanEditOwnComment() {
        when(commentRepository.save(comment)).thenReturn(comment);
        service.update(10L, 7L, new CommentRequest(null, null, "after", null));

        assertThat(comment.getContent()).isEqualTo("after");
    }

    @Test
    void otherUserCannotEditComment() {
        assertThatThrownBy(() -> service.update(
                10L, 8L, new CommentRequest(null, null, "after", null)))
                .isInstanceOf(ApiException.class);
    }
}
