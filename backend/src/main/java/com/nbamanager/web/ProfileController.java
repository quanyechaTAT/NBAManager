package com.nbamanager.web;

import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.ProfileService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @PostMapping("/avatar")
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal user) throws Exception {
        return ResponseEntity.ok(profileService.uploadAvatar(user.getId(), file));
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<Map<String, Object>> resetAvatar(
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(profileService.resetAvatar(user.getId()));
    }
}
