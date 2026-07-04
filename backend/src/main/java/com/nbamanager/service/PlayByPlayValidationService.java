package com.nbamanager.service;

import com.nbamanager.domain.provider.PlayByPlayEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PlayByPlayValidationService {

    private static final Pattern GAME_CLOCK_PATTERN = Pattern.compile("^\\d{1,2}:\\d{2}$");

    public record ValidationResult(boolean valid, List<String> errors) {
    }

    public ValidationResult validate(List<PlayByPlayEvent> events) {
        List<String> errors = new ArrayList<>();

        if (events == null || events.isEmpty()) {
            return new ValidationResult(true, errors);
        }

        for (int i = 0; i < events.size(); i++) {
            PlayByPlayEvent event = events.get(i);
            String prefix = "Event[" + i + "] ";

            // Validate period (1-10)
            if (event.period() < 1 || event.period() > 10) {
                errors.add(prefix + "Invalid period: " + event.period() + " (must be 1-10)");
            }

            // Validate gameClock format ("M:SS" or "MM:SS")
            if (event.gameClock() == null || !GAME_CLOCK_PATTERN.matcher(event.gameClock()).matches()) {
                errors.add(prefix + "Invalid gameClock format: " + event.gameClock() + " (must be M:SS or MM:SS)");
            }

            // Validate non-negative scores
            if (event.homeScore() < 0) {
                errors.add(prefix + "Negative homeScore: " + event.homeScore());
            }
            if (event.awayScore() < 0) {
                errors.add(prefix + "Negative awayScore: " + event.awayScore());
            }
        }

        // Log warnings for score decreases
        for (int i = 1; i < events.size(); i++) {
            PlayByPlayEvent prev = events.get(i - 1);
            PlayByPlayEvent curr = events.get(i);

            if (curr.homeScore() < prev.homeScore()) {
                log.warn("Home score decreased from {} to {} at event[{}]", prev.homeScore(), curr.homeScore(), i);
            }
            if (curr.awayScore() < prev.awayScore()) {
                log.warn("Away score decreased from {} to {} at event[{}]", prev.awayScore(), curr.awayScore(), i);
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }
}
