package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.UserAccount;
import com.nbamanager.event.UserIdentityUpdatedEvent;
import com.nbamanager.repository.UserAccountRepository;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private com.nbamanager.service.support.RedisWindowRateLimiter rateLimiter;

    @TempDir Path uploadRoot;

    private ProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProfileService(userAccountRepository, eventPublisher, rateLimiter);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "uploadDir", uploadRoot.toString());
    }

    @Test
    void uploadAvatarPublishesIdentityEvent() throws Exception {
        UserAccount account = new UserAccount();
        account.setId(7L);
        account.setUsername("tester");
        account.setAvatarUrl(null);

        when(userAccountRepository.findById(7L)).thenReturn(Optional.of(account));
        when(userAccountRepository.save(any())).thenReturn(account);

        // 1x1 PNG
        byte[] png = createMinimalPng();
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", png);

        Map<String, String> result = service.uploadAvatar(7L, file);

        assertThat(result.get("avatarUrl")).startsWith("/uploads/avatars/");

        ArgumentCaptor<UserIdentityUpdatedEvent> captor = ArgumentCaptor.forClass(UserIdentityUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        UserIdentityUpdatedEvent event = captor.getValue();
        assertThat(event.userId()).isEqualTo(7L);
        assertThat(event.username()).isEqualTo("tester");
        assertThat(event.avatarUrl()).startsWith("/uploads/avatars/");
    }

    // ==================== P1-4 头像文件生命周期（安全报告 §十二 V5） ====================

    @Test
    void uploadAvatarChecksUploadRateLimit() throws Exception {
        UserAccount account = new UserAccount();
        account.setId(7L);
        account.setUsername("tester");
        when(userAccountRepository.findById(7L)).thenReturn(Optional.of(account));
        when(userAccountRepository.save(any())).thenReturn(account);

        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", createMinimalPng());
        service.uploadAvatar(7L, file);

        org.mockito.Mockito.verify(rateLimiter).check(
                org.mockito.ArgumentMatchers.eq("nba:upload:rl:7"),
                org.mockito.ArgumentMatchers.eq(10),
                org.mockito.ArgumentMatchers.eq(60_000L),
                any());
    }

    @Test
    void uploadAvatarDeletesOldAvatarFileAfterSuccessfulSave() throws Exception {
        String oldName = "00000000-0000-0000-0000-000000000001.png";
        UserAccount account = new UserAccount();
        account.setId(7L);
        account.setUsername("tester");
        account.setAvatarUrl("/uploads/avatars/" + oldName);
        when(userAccountRepository.findById(7L)).thenReturn(Optional.of(account));
        when(userAccountRepository.save(any())).thenReturn(account);

        // 预置旧头像文件（模拟历史上传残留）
        java.nio.file.Files.createDirectories(uploadRoot.resolve("avatars"));
        java.nio.file.Files.write(uploadRoot.resolve("avatars").resolve(oldName), new byte[]{1});
        assertThat(java.nio.file.Files.exists(uploadRoot.resolve("avatars").resolve(oldName))).isTrue();

        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", createMinimalPng());
        service.uploadAvatar(7L, file);

        assertThat(java.nio.file.Files.exists(uploadRoot.resolve("avatars").resolve(oldName)))
                .as("上传成功后旧头像文件必须被删除（此前从不删除，20 次上传 +20 文件）")
                .isFalse();
    }

    @Test
    void nonUuidAvatarUrlIsNeverDeleted() throws Exception {
        // 防御：avatarUrl 指向非本服务生成的文件名时，绝不走删除通道
        String unsafeName = "shared-logo.png";
        UserAccount account = new UserAccount();
        account.setId(7L);
        account.setUsername("tester");
        account.setAvatarUrl("/uploads/avatars/" + unsafeName);
        when(userAccountRepository.findById(7L)).thenReturn(Optional.of(account));
        when(userAccountRepository.save(any())).thenReturn(account);

        java.nio.file.Files.createDirectories(uploadRoot.resolve("avatars"));
        java.nio.file.Files.write(uploadRoot.resolve("avatars").resolve(unsafeName), new byte[]{1});

        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", createMinimalPng());
        service.uploadAvatar(7L, file);

        assertThat(java.nio.file.Files.exists(uploadRoot.resolve("avatars").resolve(unsafeName)))
                .as("非 UUID 文件名不允许被删除通道触及")
                .isTrue();
    }

    @Test
    void resetAvatarDeletesExistingFile() throws Exception {
        String oldName = "00000000-0000-0000-0000-000000000002.png";
        UserAccount account = new UserAccount();
        account.setId(7L);
        account.setUsername("tester");
        account.setAvatarUrl("/uploads/avatars/" + oldName);
        when(userAccountRepository.findById(7L)).thenReturn(Optional.of(account));
        when(userAccountRepository.save(any())).thenReturn(account);

        java.nio.file.Files.createDirectories(uploadRoot.resolve("avatars"));
        java.nio.file.Files.write(uploadRoot.resolve("avatars").resolve(oldName), new byte[]{1});

        service.resetAvatar(7L);

        assertThat(java.nio.file.Files.exists(uploadRoot.resolve("avatars").resolve(oldName)))
                .as("resetAvatar 同样要删除现有头像文件")
                .isFalse();
    }

    @Test
    void uploadAvatarRejectsOversizeFile() throws Exception {
        byte[] oversized = new byte[3 * 1024 * 1024]; // 3MB
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", oversized);

        try {
            service.uploadAvatar(7L, file);
            assertThat(false).as("Should throw").isTrue();
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("2MB");
        }
    }

    @Test
    void resetAvatarSetsNullAndPublishesEvent() {
        UserAccount account = new UserAccount();
        account.setId(7L);
        account.setUsername("tester");
        account.setAvatarUrl("/uploads/avatars/old.png");

        when(userAccountRepository.findById(7L)).thenReturn(Optional.of(account));
        when(userAccountRepository.save(any())).thenReturn(account);

        java.util.Map<String, Object> result = service.resetAvatar(7L);

        assertThat(result.get("avatarUrl")).isNull();
        assertThat(account.getAvatarUrl()).isNull();

        ArgumentCaptor<UserIdentityUpdatedEvent> captor = ArgumentCaptor.forClass(UserIdentityUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        UserIdentityUpdatedEvent event = captor.getValue();
        assertThat(event.userId()).isEqualTo(7L);
        assertThat(event.username()).isEqualTo("tester");
        assertThat(event.avatarUrl()).isNull();
    }

    private byte[] createMinimalPng() {
        // Minimal valid 1x1 white PNG
        return new byte[]{
                (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR chunk
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, // 1x1
                0x08, 0x02, 0x00, 0x00, 0x00, (byte)0x90, 0x77, 0x53, (byte)0xDE,
                0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54, // IDAT chunk
                0x78, (byte)0x9C, 0x62, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01,
                (byte)0xE5, 0x27, (byte)0xDE, (byte)0xFC,
                0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, // IEND chunk
                (byte)0xAE, 0x42, 0x60, (byte)0x82
        };
    }
}
