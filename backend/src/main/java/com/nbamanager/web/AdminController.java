package com.nbamanager.web;

import com.nbamanager.service.NbaDataSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final NbaDataSyncService dataSyncService;

    /**
     * 获取数据同步状态
     */
    @GetMapping("/sync-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("syncing", dataSyncService.isSyncing());
        status.put("lastSyncTime", dataSyncService.getLastSyncTime());
        status.put("lastSyncStatus", dataSyncService.getLastSyncStatus());
        status.put("result", dataSyncService.getSyncResult());
        return ResponseEntity.ok(status);
    }

    /**
     * 手动触发数据同步
     */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> triggerSync() {
        if (dataSyncService.isSyncing()) {
            return ResponseEntity.ok(Map.of(
                    "message", "数据同步正在进行中，请稍后再试",
                    "status", "syncing"
            ));
        }

        dataSyncService.syncAll();

        return ResponseEntity.ok(Map.of(
                "message", "数据同步已触发，请通过 /api/admin/sync-status 查看进度",
                "status", "started"
        ));
    }
}
