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
                                // 季后赛数据修正（临时）
                                .requestMatchers(HttpMethod.POST, "/api/playoff/fix-data")
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
                                .requestMatchers(HttpMethod.POST, "/api/ai-replay/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/nba/playoff/sync")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/rag/**")
                                .permitAll()
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
