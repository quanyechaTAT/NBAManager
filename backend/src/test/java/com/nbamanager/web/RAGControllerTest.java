package com.nbamanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import com.nbamanager.service.RAGConversationService;
import com.nbamanager.service.RAGService;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RAGControllerTest {

    @Mock
    private RAGService ragService;

    @Mock
    private RAGConversationService conversationService;

    @Test
    void streamQueryReturnsValidJsonErrorEventWhenStartupFails() throws Exception {
        doThrow(new RuntimeException("service \"boom\"\nretry later"))
                .when(ragService)
                .ensureServerStarted();
        RAGController controller = new RAGController(ragService, conversationService);
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        controller.streamQuery("today games", 5, "s1", new MockHttpServletResponse()).writeTo(body);

        String sse = body.toString(StandardCharsets.UTF_8);
        assertThat(sse).startsWith("data: ");
        JSONObject event = new JSONObject(sse.substring("data: ".length()).trim());
        assertThat(event.getString("type")).isEqualTo("error");
        assertThat(event.getString("error")).isEqualTo("service \"boom\"\nretry later");
    }
}
