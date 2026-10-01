package com.nbamanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nbamanager.exception.ApiException;
import com.nbamanager.service.ChatMessageService;
import com.nbamanager.service.ChatPresenceService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock private ChatMessageService chatMessageService;
    @Mock private ChatPresenceService chatPresenceService;
    @Mock private WebSocketController webSocketController;
    @Mock private com.nbamanager.service.support.RedisWindowRateLimiter rateLimiter;

    @TempDir Path uploadRoot;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(chatMessageService, chatPresenceService, webSocketController, rateLimiter);
        ReflectionTestUtils.setField(controller, "uploadDir", uploadRoot.toString());
    }

    /** 上传端点在生产由鉴权层保证 user 非空；测试统一用固定 principal */
    private static com.nbamanager.security.UserPrincipal testUser() {
        com.nbamanager.domain.UserAccount account = new com.nbamanager.domain.UserAccount();
        account.setId(42L);
        account.setUsername("uploader");
        account.setRole(com.nbamanager.domain.Role.USER);
        return new com.nbamanager.security.UserPrincipal(account);
    }

    @Test
    void uploadImageRejectsHtmlDisguisedAsJpegBeforeWritingFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.jpg", "image/jpeg", "<html>not image</html>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> controller.uploadImage(file, "401881722", testUser()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("文件内容")
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(uploadRoot.resolve("chat")).doesNotExist();
    }

    @Test
    void uploadImageRejectsTraversalGameId() {
        MockMultipartFile file = new MockMultipartFile("file", "safe.png", "image/png", minimalPng());

        assertThatThrownBy(() -> controller.uploadImage(file, "..\\evil", testUser()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("比赛 ID")
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void uploadImageWritesUnderNormalizedAbsoluteRoot() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "safe.png", "image/png", minimalPng());

        var response = controller.uploadImage(file, "401881722", testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String url = response.getBody().get("url");
        assertThat(url).startsWith("/uploads/chat/401881722/");
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        Path written = uploadRoot.resolve("chat").resolve("401881722").resolve(fileName).normalize();
        assertThat(written.toAbsolutePath().normalize().startsWith(uploadRoot.toAbsolutePath().normalize())).isTrue();
        assertThat(Files.exists(written)).isTrue();
    }

    // ==================== F3 回归：gameId 必须是合法比赛 ID ====================

    @Test
    void uploadImageRejectsNonNumericGameIds() {
        // 安全报告 §九 S4-R2~R5：CON / 0 / -1 / 1e9 曾被接受并落盘
        for (String badGameId : new String[] { "CON", "0", "-1", "1e9" }) {
            MockMultipartFile file = new MockMultipartFile("file", "safe.png", "image/png", minimalPng());

            assertThatThrownBy(() -> controller.uploadImage(file, badGameId, testUser()))
                    .as("gameId=%s 应被拒", badGameId)
                    .isInstanceOfSatisfying(ApiException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ex.getMessage()).contains("比赛 ID");
                    });
        }
        assertThat(uploadRoot.resolve("chat")).as("非法 gameId 不应创建任何目录").doesNotExist();
    }

    @Test
    void uploadImageRejectsGameIdsWithWrongLength() {
        // 真实 ID 只有 9 位(ESPN) / 10 位(NBA 官方) 两种；过短过长都拒绝
        for (String badGameId : new String[] { "1", "12345678", "12345678901", "000000000000" }) {
            MockMultipartFile file = new MockMultipartFile("file", "safe.png", "image/png", minimalPng());

            assertThatThrownBy(() -> controller.uploadImage(file, badGameId, testUser()))
                    .as("gameId=%s 长度不合法，应被拒", badGameId)
                    .isInstanceOf(ApiException.class);
        }
    }

    @Test
    void uploadImageAcceptsRealEspnAndNbaGameIds() {
        MockMultipartFile espn = new MockMultipartFile("file", "safe.png", "image/png", minimalPng());
        var espnResponse = controller.uploadImage(espn, "401881722", testUser());
        assertThat(espnResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(espnResponse.getBody().get("url")).startsWith("/uploads/chat/401881722/");

        MockMultipartFile nba = new MockMultipartFile("file", "safe.png", "image/png", minimalPng());
        var nbaResponse = controller.uploadImage(nba, "0042500204", testUser());
        assertThat(nbaResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(nbaResponse.getBody().get("url")).startsWith("/uploads/chat/0042500204/");
    }
    private byte[] minimalPng() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53, (byte) 0xDE,
                0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54,
                0x78, (byte) 0x9C, 0x62, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01,
                (byte) 0xE5, 0x27, (byte) 0xDE, (byte) 0xFC,
                0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
                (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }
}
