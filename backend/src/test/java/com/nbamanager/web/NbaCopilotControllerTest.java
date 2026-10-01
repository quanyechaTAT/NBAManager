package com.nbamanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.Role;
import com.nbamanager.domain.UserAccount;
import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.CopilotService;
import com.nbamanager.service.CopilotConversationAccessService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class NbaCopilotControllerTest {

    @Mock private CopilotService copilotService;
    @Mock private CopilotConversationAccessService conversationAccess;

    private NbaCopilotController controller;

    private static UserPrincipal principal(long id, Role role) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setUsername("u" + id);
        account.setRole(role);
        return new UserPrincipal(account);
    }

    @BeforeEach
    void setUp() {
        controller = new NbaCopilotController(copilotService, conversationAccess);
    }

    @Test
    void chatRejectsOneHundredKbMessageWithNonOkStatus() {
        String body = "{\"message\":\"" + "x".repeat(100_000) + "\"}";

        var response = controller.chat(body, principal(1, Role.USER));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).contains("请求体过大");
        verifyNoInteractions(copilotService);
    }

    @Test
    void chatRejectsMessageOverTwoThousandCharactersBeforeStartingCopilot() {
        String body = "{\"message\":\"" + "x".repeat(2_001) + "\"}";

        var response = controller.chat(body, principal(1, Role.USER));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).contains("2000");
        verifyNoInteractions(copilotService);
    }

    // ==================== P0-1 会话归属（安全报告 §十二 V1） ====================

    @Test
    void anonymousChatIsRejectedBeforeAnyProxying() {
        var response = controller.chat("{\"message\":\"hi\"}", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(copilotService);
    }

    @Test
    void chatWithConversationIdOwnedBySomeoneElseReturns404() {
        when(conversationAccess.accessible(eq(1L), anyBoolean(), eq("conv-xyz"))).thenReturn(false);

        var response = controller.chat(
                "{\"message\":\"hi\",\"conversationId\":\"conv-xyz\"}", principal(1, Role.USER));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(copilotService);
    }

    @Test
    void getConversationNotOwnedReturns404WithoutTouchingPython() {
        when(conversationAccess.accessible(eq(1L), anyBoolean(), eq("conv-A"))).thenReturn(false);

        var response = controller.getConversation("conv-A", principal(1, Role.USER));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(copilotService);
    }

    @Test
    void deleteConversationNotOwnedReturns404WithoutTouchingPython() {
        when(conversationAccess.accessible(eq(2L), anyBoolean(), eq("conv-A"))).thenReturn(false);

        var response = controller.deleteConversation("conv-A", principal(2, Role.USER));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(copilotService);
    }

    private NbaCopilotController spiedController() {
        return org.mockito.Mockito.spy(controller);
    }

    @Test
    void listConversationsFiltersToOwnedOnes() {
        var controller = spiedController();
        org.mockito.Mockito.doReturn(
                "[{\"id\":\"mine-1\",\"messageCount\":1},"
                + "{\"id\":\"other-A\",\"messageCount\":2},"
                + "{\"id\":\"legacy\",\"messageCount\":3}]")
                .when(controller).proxyGet(anyString());
        when(conversationAccess.ownedIds(1L)).thenReturn(Set.of("mine-1"));

        var response = controller.listConversations(principal(1, Role.USER));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        // python 全量列表含：我的、别人的（已归属）、存量无归属的 -> 只保留我的
        assertThat(response.getBody()).contains("mine-1").doesNotContain("other-A").doesNotContain("legacy");
    }

    @Test
    void adminListAlsoSeesLegacyUnownedButNotOthersOwned() {
        var controller = spiedController();
        org.mockito.Mockito.doReturn(
                "[{\"id\":\"admin-own\",\"messageCount\":1},"
                + "{\"id\":\"other-A\",\"messageCount\":2},"
                + "{\"id\":\"legacy\",\"messageCount\":3}]")
                .when(controller).proxyGet(anyString());
        when(conversationAccess.ownedIds(9L)).thenReturn(Set.of("admin-own"));
        when(conversationAccess.allMappedIds()).thenReturn(Set.of("admin-own", "other-A"));

        var response = controller.listConversations(principal(9, Role.ADMIN));

        assertThat(response.getBody())
                .contains("admin-own")
                .contains("legacy")   // 存量无归属：ADMIN 迁移期可见
                .doesNotContain("other-A"); // 他人已归属：对 ADMIN 也不可见
    }

    @Test
    void getConversationOwnedByCallerProxiesToPython() {
        var controller = spiedController();
        when(conversationAccess.accessible(eq(1L), anyBoolean(), eq("conv-A"))).thenReturn(true);
        org.mockito.Mockito.doReturn("{\"id\":\"conv-A\"}").when(controller).proxyGet(anyString());

        var response = controller.getConversation("conv-A", principal(1, Role.USER));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("conv-A");
    }

    @Test
    void anonymousListIsEmpty() {
        var response = controller.listConversations(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(copilotService);
    }

    @Test
    void extractConversationIdHandlesMissingAndMalformed() {
        assertThat(NbaCopilotController.extractConversationId("{\"message\":\"hi\"}")).isNull();
        assertThat(NbaCopilotController.extractConversationId("{\"conversationId\":123}")).isNull();
        assertThat(NbaCopilotController.extractConversationId("not-json")).isNull();
        assertThat(NbaCopilotController.extractConversationId(
                "{\"conversationId\":\"abc\"}")).isEqualTo("abc");
    }
}
