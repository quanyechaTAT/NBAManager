package com.nbamanager.service.deepseek;

import static org.assertj.core.api.Assertions.assertThat;

import com.nbamanager.web.dto.TimelineEventDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiGroundingValidatorTest {

    private final AiGroundingValidator validator = new AiGroundingValidator();

    @Test
    void acceptsStructuredClaimsOnlyWhenEveryCitationKeyMatchesKnownFacts() {
        String output = "{\"claims\":[{\"claimId\":\"c1\",\"text\":\"The shot mattered.\",\"citationKeys\":[\"event:42\"]}]}";

        AiGroundingValidator.ValidationResult result = validator.validate(output, List.of(event(42L)));

        assertThat(result.valid()).isTrue();
        assertThat(result.safeResponse()).isEqualTo("The shot mattered.");
        assertThat(result.citedEventIdsJson()).isEqualTo("[42]");
        assertThat(result.citedStatKeysJson()).isNull();
    }

    @Test
    void rejectsUnknownCitationKeys() {
        String output = "{\"claims\":[{\"claimId\":\"c1\",\"text\":\"Unsupported.\",\"citationKeys\":[\"event:99\"]}]}";

        AiGroundingValidator.ValidationResult result = validator.validate(output, List.of(event(42L)));

        assertThat(result.valid()).isFalse();
        assertThat(result.citedEventIdsJson()).isNull();
    }

    @Test
    void rejectsPlainProseBecauseItCannotBeAudited() {
        AiGroundingValidator.ValidationResult result = validator.validate("This was a huge moment.", List.of(event(42L)));

        assertThat(result.valid()).isFalse();
        assertThat(result.citedEventIdsJson()).isNull();
    }

    private TimelineEventDto event(Long id) {
        return new TimelineEventDto(
                id,
                4,
                "01:23",
                "made_shot",
                "jump_shot",
                "Player made a shot",
                "Player made a shot",
                "Player made a shot",
                "Player",
                1L,
                "Team",
                2L,
                101,
                99,
                2,
                true,
                true,
                42,
                "NBA_PLAY_BY_PLAY");
    }
}
