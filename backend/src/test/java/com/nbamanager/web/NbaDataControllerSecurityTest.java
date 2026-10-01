package com.nbamanager.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nbamanager.config.SecurityConfig;
import com.nbamanager.security.JwtAuthenticationFilter;
import com.nbamanager.security.JwtService;
import com.nbamanager.security.UserDetailsServiceImpl;
import com.nbamanager.service.NbaDataService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * P1 回归（安全报告 §十三 H7）：季后赛数据同步必须仅限 ADMIN。
 *
 * 该端点触发 python 子进程 + 外部 API 拉取 + 数据库批量写入，属运维操作；
 * 此前只有路由级 authenticated（anyRequest()），任何登录用户都能触发。
 * 本测试锁定三态：匿名 401/403、USER 403、ADMIN 200。
 */
@WebMvcTest(NbaDataController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, com.nbamanager.security.UserIdentityCache.class})
class NbaDataControllerSecurityTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private NbaDataService nbaDataService;
    @MockBean private com.nbamanager.service.PlayoffService playoffService;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    @Test
    void anonymousCannotTriggerPlayoffSync() throws Exception {
        mockMvc.perform(post("/api/nba/playoff/sync"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void normalUserCannotTriggerPlayoffSync() throws Exception {
        mockMvc.perform(post("/api/nba/playoff/sync").with(user("u1").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanTriggerPlayoffSync() throws Exception {
        when(nbaDataService.fetchAndImportPlayoffData(anyString()))
                .thenReturn(Map.of("status", "ok"));

        mockMvc.perform(post("/api/nba/playoff/sync").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void syncEndpointRejectsAnonymousWithoutTouchingService() throws Exception {
        // 与 anonymous 用例互补：确认拒绝发生在到达业务服务之前
        mockMvc.perform(post("/api/nba/playoff/sync?season=2024-25"))
                .andExpect(status().is4xxClientError());
        org.mockito.Mockito.verifyNoInteractions(nbaDataService);
    }
}
