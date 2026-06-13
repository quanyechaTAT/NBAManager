package com.nbamanager.service;

import com.nbamanager.domain.Poll;
import com.nbamanager.domain.PollVote;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.PollRepository;
import com.nbamanager.repository.PollVoteRepository;
import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.web.dto.PollDto;
import com.nbamanager.web.dto.PollRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PollService {

    private final PollRepository pollRepository;
    private final PollVoteRepository pollVoteRepository;
    private final UserAccountRepository userAccountRepository;

    @Transactional(readOnly = true)
    public PollDto getByPostId(Long postId, Long userId) {
        Poll poll = pollRepository.findByPostId(postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "该帖子没有投票"));

        Integer userVote = null;
        if (userId != null) {
            Optional<PollVote> existingVote = pollVoteRepository.findByPollId(poll.getId()).stream()
                    .filter(v -> v.getUserId().equals(userId))
                    .findFirst();
            if (existingVote.isPresent()) {
                userVote = existingVote.get().getOptionIndex();
            }
        }

        return toDto(poll, userVote);
    }

    @Transactional
    public PollDto create(Long userId, PollRequest req) {
        Poll poll = new Poll();
        poll.setPostId(req.postId());
        poll.setQuestion(req.question());
        poll.setOption1(req.option1());
        poll.setOption2(req.option2());
        poll.setOption3(req.option3());
        poll.setOption4(req.option4());

        Poll saved = pollRepository.save(poll);
        return toDto(saved, null);
    }

    @Transactional
    public PollDto vote(Long pollId, Long userId, Integer optionIndex) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "投票不存在"));

        if (optionIndex < 0 || optionIndex > 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "无效的选项索引");
        }

        if (pollVoteRepository.existsByPollIdAndUserId(pollId, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "你已经投过票了");
        }

        // 验证选项是否存在
        if (optionIndex == 2 && poll.getOption3() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "选项3不存在");
        }
        if (optionIndex == 3 && poll.getOption4() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "选项4不存在");
        }

        PollVote vote = new PollVote();
        vote.setPollId(pollId);
        vote.setUserId(userId);
        vote.setOptionIndex(optionIndex);
        pollVoteRepository.save(vote);

        switch (optionIndex) {
            case 0 -> poll.setOption1Votes(poll.getOption1Votes() + 1);
            case 1 -> poll.setOption2Votes(poll.getOption2Votes() + 1);
            case 2 -> poll.setOption3Votes(poll.getOption3Votes() + 1);
            case 3 -> poll.setOption4Votes(poll.getOption4Votes() + 1);
        }
        poll.setTotalVotes(poll.getTotalVotes() + 1);

        Poll saved = pollRepository.save(poll);
        return toDto(saved, optionIndex);
    }

    private PollDto toDto(Poll poll, Integer userVote) {
        return new PollDto(
                poll.getId(),
                poll.getPostId(),
                poll.getQuestion(),
                poll.getOption1(),
                poll.getOption2(),
                poll.getOption3(),
                poll.getOption4(),
                poll.getOption1Votes(),
                poll.getOption2Votes(),
                poll.getOption3Votes(),
                poll.getOption4Votes(),
                poll.getTotalVotes(),
                userVote,
                poll.getCreateTime());
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails ud) {
            return ((com.nbamanager.security.UserPrincipal) ud).getId();
        }
        return null;
    }
}
