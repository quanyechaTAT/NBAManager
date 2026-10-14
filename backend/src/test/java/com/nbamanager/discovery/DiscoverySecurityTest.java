package com.nbamanager.discovery;

import com.nbamanager.config.SecurityConfig;
import com.nbamanager.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@WebMvcTest({DiscoveryController.class,DiscoveryAdminController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,UserIdentityCache.class})
class DiscoverySecurityTest {
    @Autowired MockMvc mvc;
    @MockBean DiscoveryService service;
    @MockBean DiscoveryStore store;
    @MockBean DiscoveryCatalog catalog;
    @MockBean JdbcTemplate jdbc;
    @MockBean JwtService jwt;
    @MockBean UserDetailsServiceImpl users;

    @Test void anonymousUsersCannotQueryPersonalizedEndpoints() throws Exception {
        for(String path:new String[]{"search","suggest","recommendations","preferences"})
            mvc.perform(get("/api/discovery/"+path)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/discovery/events").contentType("application/json").content("{\"events\":[]}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service,store);
    }
    @Test @WithMockUser(roles="USER") void regularUsersCannotOperateModelsOrIndexes() throws Exception {
        mvc.perform(get("/api/admin/discovery/status")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/discovery/jobs").contentType("application/json").content("{\"kind\":\"REINDEX\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(jdbc);
    }
    @Test @WithMockUser(roles="ADMIN") void adminsCanInspectStatus() throws Exception {
        mvc.perform(get("/api/admin/discovery/status")).andExpect(status().isOk());
    }
}
