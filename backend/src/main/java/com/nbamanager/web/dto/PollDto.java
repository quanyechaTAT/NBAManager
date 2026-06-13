package com.nbamanager.web.dto;

import java.time.LocalDateTime;

public record PollDto(
        Long id,
        Long postId,
        String question,
        String option1,
        String option2,
        String option3,
        String option4,
        Integer option1Votes,
        Integer option2Votes,
        Integer option3Votes,
        Integer option4Votes,
        Integer totalVotes,
        Integer userVote,
        LocalDateTime createTime) {}
