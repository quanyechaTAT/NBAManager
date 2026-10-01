package com.nbamanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.Poll;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.PollRepository;
import com.nbamanager.repository.PollVoteRepository;
import com.nbamanager.service.PollService;
import com.nbamanager.repository.UserAccountRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * P1 回归（安全报告 §十五 M4/VULN）：投票缺 optionIndex 必须 400，而不是 null 拆箱 NPE → 500。
 * 同时锁定既有投票语义：索引越界 400、重复投票 409、正常投票 200 语义。
 */
@ExtendWith(MockitoExtension.class)
class PollVoteValidationTest {

    @Mock private PollRepository pollRepository;
    @Mock private PollVoteRepository pollVoteRepository;
    @Mock private UserAccountRepository userAccountRepository;

    private PollService service;
    private PollController controller;

    @BeforeEach
    void setUp() {
        service = new PollService(pollRepository, pollVoteRepository, userAccountRepository);
        controller = new PollController(service);
    }

    private Poll poll(int options) {
        Poll poll = new Poll();
        poll.setId(9L);
        poll.setQuestion("q");
        poll.setOption1("A");
        poll.setOption2("B");
        poll.setOption1Votes(0);
        poll.setOption2Votes(0);
        poll.setTotalVotes(0);
        if (options > 2) {
            poll.setOption3("C");
            poll.setOption3Votes(0);
        }
        if (options > 3) {
            poll.setOption4("D");
            poll.setOption4Votes(0);
        }
        return poll;
    }

    private com.nbamanager.security.UserPrincipal user(long id) {
        com.nbamanager.domain.UserAccount account = new com.nbamanager.domain.UserAccount();
        account.setId(id);
        account.setUsername("u" + id);
        account.setRole(com.nbamanager.domain.Role.USER);
        return new com.nbamanager.security.UserPrincipal(account);
    }

    @Test
    void controllerRejectsMissingOptionIndexWith400() {
        // M4 复现体：{} —— 此前 null 拆箱 NPE → 500
        assertThatThrownBy(() -> controller.vote(9L, user(1L), new HashMap<>()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("optionIndex");
    }

    @Test
    void serviceDefensivelyRejectsNullOptionIndex() {
        assertThatThrownBy(() -> service.vote(9L, 1L, null))
                .as("双保险：服务层也必须判 null，防未来其他调用方拆箱 NPE")
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void outOfRangeOptionIndexStillRejectedWith400() {
        assertThatThrownBy(() -> service.vote(9L, 1L, 9))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void duplicateVoteStillRejectedWith409() {
        when(pollRepository.findById(9L)).thenReturn(Optional.of(poll(2)));
        when(pollVoteRepository.existsByPollIdAndUserId(9L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.vote(9L, 1L, 0))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void normalVoteStillSucceeds() {
        when(pollRepository.findById(9L)).thenReturn(Optional.of(poll(2)));
        when(pollVoteRepository.existsByPollIdAndUserId(9L, 1L)).thenReturn(false);
        when(pollVoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pollRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.vote(9L, 1L, 0);

        assertThat(dto).isNotNull();
    }
}
