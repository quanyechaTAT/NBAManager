package com.nbamanager.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.security.JwtService;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private com.nbamanager.security.UserIdentityCache userIdentityCache;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authenticationManager, jwtService, userAccountRepository,
                passwordEncoder, eventPublisher, stringRedisTemplate, userIdentityCache);
        ReflectionTestUtils.setField(controller, "trustedProxies", "");
    }

    // ==================== P1-1 注册频控（安全报告 §十二 V3 / 压测 P6） ====================

    @Test
    void registerRateLimitRejectsWhenCountExceedsLimit() {
        MockHttpServletRequest request = request("10.0.0.77", "203.0.113.1");
        org.mockito.Mockito.when(stringRedisTemplate.execute(
                org.mockito.ArgumentMatchers.<org.springframework.data.redis.core.script.DefaultRedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.eq(java.util.List.of("nba:register:rl:10.0.0.77")),
                org.mockito.ArgumentMatchers.eq("60000")))
                .thenReturn(11L);

        try {
            controller.checkRegisterRateLimit(request);
            assertThat(false).as("第 11 次注册必须被 429 拒绝").isTrue();
        } catch (com.nbamanager.exception.ApiException e) {
            assertThat(e.getStatus()).isEqualTo(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    @Test
    void registerRateLimitAllowsRequestsUnderLimit() {
        MockHttpServletRequest request = request("10.0.0.77", "203.0.113.1");
        org.mockito.Mockito.when(stringRedisTemplate.execute(
                org.mockito.ArgumentMatchers.<org.springframework.data.redis.core.script.DefaultRedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(3L);

        controller.checkRegisterRateLimit(request); // 不抛异常即通过
    }

    @Test
    void registerRateLimitFailsOpenWhenRedisUnavailable() {
        MockHttpServletRequest request = request("10.0.0.77", "203.0.113.1");
        org.mockito.Mockito.when(stringRedisTemplate.execute(
                org.mockito.ArgumentMatchers.<org.springframework.data.redis.core.script.DefaultRedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("redis down"));

        // fail-open：Redis 不可用不能挡住注册（与登录限流同型取舍）
        controller.checkRegisterRateLimit(request);
    }

    @Test
    void registerRateLimitKeyUsesRemoteAddrNotSpoofedForwardedFor() {
        MockHttpServletRequest request = request("10.0.0.77", "203.0.113.99");
        org.mockito.Mockito.when(stringRedisTemplate.execute(
                org.mockito.ArgumentMatchers.<org.springframework.data.redis.core.script.DefaultRedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(1L);

        controller.checkRegisterRateLimit(request);

        org.mockito.Mockito.verify(stringRedisTemplate).execute(
                org.mockito.ArgumentMatchers.<org.springframework.data.redis.core.script.DefaultRedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.eq(java.util.List.of("nba:register:rl:10.0.0.77")),
                org.mockito.ArgumentMatchers.eq("60000"));
    }

    @Test
    void directRequestIgnoresFixedSpoofedForwardedFor() {
        MockHttpServletRequest request = request("10.0.0.50", "203.0.113.10");

        assertThat(controller.clientIp(request)).isEqualTo("10.0.0.50");
    }

    @Test
    void directRequestsResolveRotatingForwardedForToSameRemoteAddress() {
        Set<String> resolved = IntStream.range(0, 25)
                .mapToObj(i -> controller.clientIp(request("10.0.0.50", "203.0.113." + i)))
                .collect(Collectors.toSet());

        assertThat(resolved).containsExactly("10.0.0.50");
    }

    @Test
    void trustedProxyUsesFirstForwardedForAddress() {
        ReflectionTestUtils.setField(controller, "trustedProxies", "10.0.0.1,192.168.0.0/16");

        assertThat(controller.clientIp(request("10.0.0.1", "203.0.113.10, 10.0.0.1")))
                .isEqualTo("203.0.113.10");
        assertThat(controller.clientIp(request("192.168.10.8", "203.0.113.11")))
                .isEqualTo("203.0.113.11");
    }

    private MockHttpServletRequest request(String remoteAddr, String xff) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        request.addHeader("X-Forwarded-For", xff);
        return request;
    }
}
