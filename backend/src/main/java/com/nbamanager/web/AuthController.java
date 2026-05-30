package com.nbamanager.web;

import com.nbamanager.domain.Role;
import com.nbamanager.domain.UserAccount;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.security.JwtService;
import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.NbaDataSyncService;
import com.nbamanager.web.dto.ChangePasswordRequest;
import com.nbamanager.web.dto.LoginRequest;
import com.nbamanager.web.dto.LoginResponse;
import com.nbamanager.web.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final NbaDataSyncService dataSyncService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication auth =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        String token = jwtService.generateToken(principal.getUsername(), principal.getRole());

        // 管理员登录时触发数据同步
        if (principal.getRole() == Role.ADMIN) {
            log.info("管理员 {} 登录成功，触发NBA数据同步", principal.getUsername());
            dataSyncService.syncAll(); // 异步执行，不会阻塞登录
        }

        return new LoginResponse(token, principal.getUsername(), principal.getRole());
    }

    @PostMapping("/register")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        if (userAccountRepository.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "用户名已存在");
        }

        UserAccount user = new UserAccount();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        userAccountRepository.save(user);

        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getUsername(), user.getRole());
    }

    @PutMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public void changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {

        UserAccount user = userAccountRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "用户不存在"));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "原密码错误");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "新密码不能与原密码相同");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userAccountRepository.save(user);
    }
}
