package com.nbamanager.service.mimo;

import com.nbamanager.web.dto.TimelineEventDto;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AiGroundingValidator {

    public record ValidationResult(boolean valid, String safeResponse) {}

    private static final String FALLBACK_RESPONSE = "当前 Play-by-Play 数据不足以支持这个判断。系统已找到相关比赛，但缺少可引用的事件或统计依据。";

    public ValidationResult validate(String aiOutput, List<TimelineEventDto> facts) {
        if (aiOutput == null || aiOutput.isBlank()) {
            return new ValidationResult(false, FALLBACK_RESPONSE);
        }

        // Check if output contains at least one reference to event data
        Set<String> knownPlayers = facts.stream()
                .map(TimelineEventDto::playerName)
                .filter(p -> p != null && !p.isEmpty())
                .collect(Collectors.toSet());

        Set<String> knownTeams = facts.stream()
                .map(TimelineEventDto::teamName)
                .filter(t -> t != null && !t.isEmpty())
                .collect(Collectors.toSet());

        boolean hasPlayerRef = knownPlayers.stream().anyMatch(aiOutput::contains);
        boolean hasTeamRef = knownTeams.stream().anyMatch(aiOutput::contains);
        boolean hasScoreRef = aiOutput.matches(".*\\d{2,3}[-:：]\\d{2,3}.*");
        boolean hasPeriodRef = aiOutput.contains("节") || aiOutput.contains("Q") || aiOutput.contains("加时");

        int refCount = (hasPlayerRef ? 1 : 0) + (hasTeamRef ? 1 : 0) + (hasScoreRef ? 1 : 0) + (hasPeriodRef ? 1 : 0);

        if (refCount < 1) {
            log.warn("AI output lacks grounding references");
            return new ValidationResult(false, FALLBACK_RESPONSE);
        }

        return new ValidationResult(true, aiOutput);
    }
}
