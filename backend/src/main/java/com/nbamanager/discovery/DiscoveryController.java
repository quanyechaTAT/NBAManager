package com.nbamanager.discovery;

import com.nbamanager.discovery.DiscoveryTypes.*;
import com.nbamanager.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/discovery")
@PreAuthorize("hasAnyRole('USER','ADMIN')")
@RequiredArgsConstructor
public class DiscoveryController {
    private final DiscoveryService service;
    private final DiscoveryStore store;
    private final DiscoveryCatalog catalog;
    @GetMapping("/search") public Response search(@AuthenticationPrincipal UserPrincipal user,@RequestParam Map<String,String> params) {
        return service.fetch(user.getId(),"search",params);
    }
    @GetMapping("/suggest") public Response suggest(@AuthenticationPrincipal UserPrincipal user,@RequestParam Map<String,String> params) {
        return service.fetch(user.getId(),"suggest",params);
    }
    @GetMapping("/recommendations") public Response recommendations(@AuthenticationPrincipal UserPrincipal user,@RequestParam Map<String,String> params) {
        return service.fetch(user.getId(),"recommendations",params);
    }
    @PostMapping("/events") public EventReceipt events(@AuthenticationPrincipal UserPrincipal user,@Valid @RequestBody EventBatch batch) {
        return store.events(user.getId(),batch.events());
    }
    @PostMapping("/feedback") public Map<String,Boolean> feedback(@AuthenticationPrincipal UserPrincipal user,@Valid @RequestBody Feedback feedback) {
        if(feedback.active()&&!catalog.hydrate(java.util.List.of(feedback.itemKey())).containsKey(feedback.itemKey()))
            throw DiscoveryTypes.bad("内容已不存在");
        store.feedback(user.getId(),feedback.itemKey(),feedback.active()); return Map.of("active",feedback.active());
    }
    @GetMapping("/preferences") public Preferences preferences(@AuthenticationPrincipal UserPrincipal user) { return store.preferences(user.getId()); }
    @PutMapping("/preferences") public Preferences preferences(@AuthenticationPrincipal UserPrincipal user,@Valid @RequestBody PreferenceUpdate body) {
        return store.preferences(user.getId(),body.personalized());
    }
    @DeleteMapping("/preferences/history") public Preferences clear(@AuthenticationPrincipal UserPrincipal user) { return store.clearPreferences(user.getId()); }
}
