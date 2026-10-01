package com.nbamanager.web;

import static com.nbamanager.domain.replay.ReplayPageState.PREPARING;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nbamanager.config.SecurityConfig;
import com.nbamanager.security.JwtAuthenticationFilter;
import com.nbamanager.security.JwtService;
import com.nbamanager.security.UserDetailsServiceImpl;
import com.nbamanager.service.AiReplayService;
import com.nbamanager.service.ReplayPreparationService;
import com.nbamanager.service.ReplayReadService;
import com.nbamanager.web.dto.ReplayPreparationDto;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AiReplayController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, com.nbamanager.security.UserIdentityCache.class})
class AiReplaySecurityTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AiReplayService aiReplayService;
    @MockBean private ReplayReadService replayReadService;
    @MockBean private ReplayPreparationService replayPreparationService;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    @Test
    void prepareIsPublicButStillReturnsRetryAfterContract() throws Exception {
        LocalDateTime startedAt = LocalDateTime.parse("2026-07-21T10:15:00");
        when(replayPreparationService.prepare("0042500405"))
                .thenReturn(new ReplayPreparationDto(
                        "0042500405",
                        PREPARING,
                        "operation-1",
                        false,
                        3,
                        startedAt,
                        startedAt.plusSeconds(90),
                        null));

        mockMvc.perform(post("/api/ai-replay/games/0042500405/prepare"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Retry-After", "3"));
    }

    @Test
    void summaryGenerationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/ai-replay/games/0042500405/summary"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(aiReplayService);
    }

    @Test
    void eventExplanationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/ai-replay/games/0042500405/events/123/explain"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(aiReplayService);
    }
}
