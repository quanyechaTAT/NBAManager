package com.nbamanager.discovery;

import com.nbamanager.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/discovery")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class DiscoveryAdminController {
    private final JdbcTemplate jdbc;
    private final DiscoveryStore store;
    public record JobRequest(@NotBlank String kind, String version, @Min(0) @Max(50) Integer traffic) {}
    @GetMapping("/status") public Map<String,Object> status() {
        Map<String,Object> result=new HashMap<>();
        result.put("controls",jdbc.queryForList("SELECT control_key,value_json,updated_at FROM discovery_control"));
        result.put("models",jdbc.queryForList("SELECT version,status,feature_version,metrics_json,created_at FROM discovery_models ORDER BY created_at DESC LIMIT 20"));
        result.put("pendingEmbeddings",jdbc.queryForObject("SELECT COUNT(*) FROM discovery_embedding_jobs",Long.class));
        result.put("indexedItems",jdbc.queryForObject("SELECT COUNT(*) FROM discovery_index_state WHERE deleted=FALSE",Long.class));
        return result;
    }
    @GetMapping("/jobs") public List<Map<String,Object>> jobs() {
        return jdbc.queryForList("SELECT * FROM discovery_jobs ORDER BY created_at DESC LIMIT 50");
    }
    @PostMapping("/jobs") public Map<String,String> create(@AuthenticationPrincipal UserPrincipal user,@Valid @RequestBody JobRequest request) {
        if(!Set.of("REINDEX","SNAPSHOT","ITEMCF","TRAIN","PUBLISH","ROLLBACK_INDEX","ROLLBACK_MODEL").contains(request.kind()))
            throw DiscoveryTypes.bad("任务类型无效");
        Map<String,Object> detail=new HashMap<>();
        if(request.kind().equals("PUBLISH")) {
            if(request.version()==null||!request.version().matches("[A-Za-z0-9-]{1,80}")) throw DiscoveryTypes.bad("模型版本无效");
            int traffic=request.traffic()==null?0:request.traffic();
            if(!Set.of(0,5,20,50).contains(traffic)) throw DiscoveryTypes.bad("灰度比例须为 0、5、20 或 50");
            detail.put("version",request.version()); detail.put("traffic",traffic);
        }
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO discovery_jobs(id,kind,status,requested_by,detail_json,created_at) VALUES(?,?,'QUEUED',?,?,UTC_TIMESTAMP(6))",
                id,request.kind(),user.getId(),store.encode(detail));
        return Map.of("id",id,"status","QUEUED");
    }
    @PostMapping("/jobs/{id}/cancel") public Map<String,Boolean> cancel(@PathVariable String id) {
        int count=jdbc.update("UPDATE discovery_jobs SET cancel_requested=TRUE WHERE id=? AND status IN ('QUEUED','RUNNING')",id);
        return Map.of("cancelRequested",count>0);
    }
}
