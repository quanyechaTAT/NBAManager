package com.nbamanager.config;

import com.nbamanager.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("http://localhost:*");
        config.addAllowedOriginPattern("http://127.0.0.1:*");
        config.addAllowedOriginPattern("http://[::1]:*");
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
                                .requestMatchers(HttpMethod.GET, "/api/rag/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/notifications/**")
                                .permitAll()
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
