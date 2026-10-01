package com.nbamanager.config;

import com.nbamanager.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Value("${app.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*,http://[::1]:*}")
    private String corsAllowedOrigins;

    /**
     * 内容安全策略。
     *
     * 安全测试（2026-09-10/09-17 报告）把"缺少 CSP"列为风险：缺少该头时，
     * 一旦某个注入点漏过前端消毒，浏览器没有任何第二道防线。
     *
     * 指令覆盖本应用真实使用的外部资源：
     * - script: 仅同源 + 首页主题引导内联脚本的 sha256（Vue 模板在构建期编译，不需要 unsafe-eval）
     * - style:  Element Plus / ECharts 会在运行时写内联样式，需要 unsafe-inline
     * - font:   Google Fonts（CSS 与字体文件分属两个域）
     * - img:    球队 Logo（a.espncdn.com）、新闻外链图、/uploads 头像与聊天图
     * - connect: 同源 HTTP + SockJS/STOMP 的 ws/wss
     * 置空可关闭（便于个别部署按需回退）。
     */
    @Value("${app.security.content-security-policy:"
            + "default-src 'self'; "
            + "script-src 'self' 'sha256-kvN8ZcNKC/JQ7pjSVc+ze2YJ3bmd3+cZWpOXpCtOQcE='; "
            + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
            + "font-src 'self' data: https://fonts.gstatic.com; "
            + "img-src 'self' data: blob: https:; "
            + "connect-src 'self' ws: wss:; "
            + "worker-src 'self' blob:; "
            + "frame-src 'self'; "
            + "object-src 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'; "
            + "frame-ancestors 'none'}")
    private String contentSecurityPolicy;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        for (String origin : corsAllowedOrigins.split(",")) {
            config.addAllowedOriginPattern(origin.trim());
        }
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(c -> c.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> {
                    headers.referrerPolicy(
                            referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    if (contentSecurityPolicy != null && !contentSecurityPolicy.isBlank()) {
                        headers.contentSecurityPolicy(
                                csp -> csp.policyDirectives(contentSecurityPolicy));
                    }
                })
                .authorizeHttpRequests(
                        auth -> auth.requestMatchers("/api/auth/**")
                                .permitAll()
                                // WebSocket 放行
                                .requestMatchers("/ws/**")
                                .permitAll()
                                .requestMatchers("/ws")
                                .permitAll()
                                // 上传文件静态资源
                                .requestMatchers("/uploads/**")
                                .permitAll()
                                // 前端静态资源和 SPA 路由（非 /api/、非 /ws/ 的路径放行，由前端路由处理）
                                .requestMatchers("/", "/index.html", "/favicon.svg", "/sw.js",
                                        "/assets/**", "/images/**")
                                .permitAll()
                                .requestMatchers(request -> {
                                    String path = request.getRequestURI();
                                    return !path.startsWith("/api/") && !path.startsWith("/ws");
                                })
                                .permitAll()
                                // 前端实际调用的 GET 端点（公开可读）
                                .requestMatchers(HttpMethod.GET, "/api/dashboard/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/news/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/polls/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/players/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/players")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/teams/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/teams")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/posts/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/posts")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/drafts/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/drafts")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/historical/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/historical")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/match-records/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/match-records")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/match-detail/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/comments/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/playoff/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/nba/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/shot-chart/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/ai-replay/**")
                                .permitAll()
                                // prepare 是幂等的数据准备触发器（单飞+租约+Retry-After 熔断），
                                // 公开是有测试契约保障的设计（AiReplaySecurityTest）
                                .requestMatchers(HttpMethod.POST, "/api/ai-replay/games/*/prepare")
                                .permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/ai-replay/**")
                                .authenticated()
                                // RAG 需登录（stream/query/stats/conversations）；仅分享会话公开。
                                // 此前匿名可用会导致 recentHistory 按 sessionId 加载历史时无用户绑定
                                .requestMatchers(HttpMethod.GET, "/api/rag/shared/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/rag/**")
                                .authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/notifications/**")
                                .permitAll()
                                // 聊天历史接口（公开可读）
                                .requestMatchers(HttpMethod.GET, "/api/chat/**")
                                .permitAll()
                                // 聊天图片上传（需认证）
                                .requestMatchers(HttpMethod.POST, "/api/chat/upload")
                                .authenticated()
                                // 聊天消息删除（需认证）
                                .requestMatchers(HttpMethod.DELETE, "/api/chat/**")
                                .authenticated()
                                // 其他 GET 请求需要认证
                                .requestMatchers(HttpMethod.GET, "/api/**")
                                .authenticated()
                                // 写操作需要认证
                                .anyRequest()
                                .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
