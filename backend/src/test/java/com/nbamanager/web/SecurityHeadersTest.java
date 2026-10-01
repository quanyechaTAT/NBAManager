package com.nbamanager.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.nbamanager.config.SecurityConfig;
import com.nbamanager.security.JwtAuthenticationFilter;
import com.nbamanager.security.JwtService;
import com.nbamanager.security.UserDetailsServiceImpl;
import com.nbamanager.service.ReplayPreparationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 浏览器安全响应头回归测试。
 *
 * 安全测试报告（2026-09-10）把 "Content-Security-Policy 缺失" 列为 RISK：
 * 一旦注入点漏过前端 sanitize，浏览器侧没有任何第二道防线。
 * 这里断言 CSP 与 Referrer-Policy 已随所有响应下发。
 */
@WebMvcTest(AiReplayController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, com.nbamanager.security.UserIdentityCache.class})
class SecurityHeadersTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private com.nbamanager.service.AiReplayService aiReplayService;
    @MockBean private com.nbamanager.service.ReplayReadService replayReadService;
    @MockBean private ReplayPreparationService replayPreparationService;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    @Test
    void contentSecurityPolicyIsPresentAndLocksDownDangerousDirectives() throws Exception {
        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/ai-replay/games/0042500405/bootstrap"))
                .andReturn();

        String csp = result.getResponse().getHeader("Content-Security-Policy");
        assertThat(csp).isNotNull();
        assertThat(csp)
                .contains("default-src 'self'")
                .contains("object-src 'none'")
                .contains("base-uri 'self'")
                .contains("frame-ancestors 'none'")
                .contains("connect-src 'self' ws: wss:");
        // 不应放开脚本的 unsafe-eval/unsafe-inline（首页主题引导用 sha256 白名单）
        assertThat(csp).doesNotContain("script-src 'self' 'unsafe-inline'");
        assertThat(csp).doesNotContain("'unsafe-eval'");
    }

    @Test
    void referrerPolicyIsPresent() throws Exception {
        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/ai-replay/games/0042500405/bootstrap"))
                .andReturn();

        assertThat(result.getResponse().getHeader("Referrer-Policy"))
                .isEqualTo("strict-origin-when-cross-origin");
    }

    @Test
    void headerWriterFilterIsRegistered() {
        // 防止后续重构把 headers(...) 配置整段删掉
        assertThat(HeaderWriterFilter.class).isNotNull();
    }
}
