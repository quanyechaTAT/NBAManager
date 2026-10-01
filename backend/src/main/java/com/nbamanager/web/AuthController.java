package com.nbamanager.web;

import com.nbamanager.domain.Role;
import com.nbamanager.domain.UserAccount;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.security.JwtService;
import com.nbamanager.security.UserPrincipal;
import com.nbamanager.event.UserIdentityUpdatedEvent;
import com.nbamanager.web.dto.ChangePasswordRequest;
import com.nbamanager.web.dto.ChangeUsernameRequest;
import com.nbamanager.web.dto.LoginRequest;
import com.nbamanager.web.dto.LoginResponse;
import com.nbamanager.web.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.annotation.Transactional;
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
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate stringRedisTemplate;
    private final com.nbamanager.security.UserIdentityCache userIdentityCache;

    @Value("${app.security.trusted-proxies:}")
    private String trustedProxies;

    // 登录 IP 限流：BCrypt 密集，50 并发即可把 CPU 打满（压测实测），公网部署必须防爆破
    private static final int LOGIN_RL_LIMIT = 20;
    private static final int LOGIN_RL_WINDOW_MS = 60_000;
    /** INCR + 首次 PEXPIRE 的原子窗口（登录/注册共用同一脚本，仅 key 与阈值不同） */
    private static final String WINDOW_RL_LUA =
            "local v = redis.call('INCR', KEYS[1]) " +
            "if v == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end " +
            "return v";
    // 注册 IP 限流（安全报告 §十二 V3 / 压测 P6：300 注册 50 并发全部落库）：
    // 注册同样执行 BCrypt 且每注册落一行持久数据，公网是灌库 + CPU 放大点
    private static final int REGISTER_RL_LIMIT = 10;
    private static final int REGISTER_RL_WINDOW_MS = 60_000;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        checkLoginRateLimit(httpRequest);
        Authentication auth =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        String token = jwtService.generateToken(principal.getUsername(), principal.getRole(), principal.getId(),
                com.nbamanager.security.JwtService.pwdMarkerOf(principal.getPassword()));

        // 管理员登录时发布事件，异步检查遗漏调度补偿
        if (principal.getRole() == Role.ADMIN) {
            log.info("管理员 {} 登录成功", principal.getUsername());
            eventPublisher.publishEvent(new com.nbamanager.event.AdminLoginEvent(
                    principal.getUsername(),
                    java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))));
        }

        String avatarUrl = userAccountRepository.findById(principal.getId())
                .map(UserAccount::getAvatarUrl)
                .orElse(null);
        return new LoginResponse(token, principal.getUsername(), principal.getRole(), avatarUrl);
    }

    /** Redis 原子窗口限流：单 IP 60s 内超过 20 次登录尝试返回 429；Redis 不可用时放行保底 */
    void checkLoginRateLimit(jakarta.servlet.http.HttpServletRequest request) {
        try {
            String ip = clientIp(request);
            Long count = stringRedisTemplate.execute(
                    new DefaultRedisScript<>(WINDOW_RL_LUA, Long.class),
                    java.util.List.of("nba:login:rl:" + ip),
                    String.valueOf(LOGIN_RL_WINDOW_MS));
            if (count != null && count > LOGIN_RL_LIMIT) {
                log.warn("登录限流触发: ip={} count={}", ip, count);
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("登录限流 Redis 不可用，放行: {}", e.getMessage());
        }
    }

    /** 注册限流：同 IP 60s 内超过 10 次返回 429；Redis 不可用时放行（与登录限流同型 fail-open） */
    void checkRegisterRateLimit(jakarta.servlet.http.HttpServletRequest request) {
        try {
            String ip = clientIp(request);
            Long count = stringRedisTemplate.execute(
                    new DefaultRedisScript<>(WINDOW_RL_LUA, Long.class),
                    java.util.List.of("nba:register:rl:" + ip),
                    String.valueOf(REGISTER_RL_WINDOW_MS));
            if (count != null && count > REGISTER_RL_LIMIT) {
                log.warn("注册限流触发: ip={} count={}", ip, count);
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "注册过于频繁，请稍后再试");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("注册限流 Redis 不可用，放行: {}", e.getMessage());
        }
    }

    String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        String remoteAddr = normalizeIpToken(request.getRemoteAddr());
        if (isTrustedProxy(remoteAddr)) {
            String xff = request.getHeader("X-Forwarded-For");
            String forwarded = firstForwardedFor(xff);
            if (forwarded != null) {
                return forwarded;
            }
        }
        return remoteAddr;
    }

    boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || trustedProxies == null || trustedProxies.isBlank()) {
            return false;
        }
        for (String trusted : trustedProxies.split(",")) {
            String rule = trusted.trim();
            if (rule.isEmpty()) {
                continue;
            }
            if (rule.equals(remoteAddr) || cidrMatches(remoteAddr, rule)) {
                return true;
            }
        }
        return false;
    }

    private String firstForwardedFor(String xff) {
        if (xff == null || xff.isBlank()) {
            return null;
        }
        String candidate = normalizeIpToken(xff.split(",")[0]);
        if (candidate.isBlank() || "unknown".equalsIgnoreCase(candidate) || candidate.length() > 64) {
            return null;
        }
        return candidate;
    }

    private String normalizeIpToken(String value) {
        if (value == null) {
            return "unknown";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length() > 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.isBlank() ? "unknown" : trimmed;
    }

    private boolean cidrMatches(String remoteAddr, String cidrRule) {
        if (!cidrRule.contains("/")) {
            return false;
        }
        try {
            String[] parts = cidrRule.split("/", 2);
            java.net.InetAddress remote = java.net.InetAddress.getByName(remoteAddr);
            java.net.InetAddress network = java.net.InetAddress.getByName(parts[0].trim());
            int prefix = Integer.parseInt(parts[1].trim());
            byte[] remoteBytes = remote.getAddress();
            byte[] networkBytes = network.getAddress();
            if (remoteBytes.length != networkBytes.length || prefix < 0 || prefix > remoteBytes.length * 8) {
                return false;
            }
            int fullBytes = prefix / 8;
            int remainingBits = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (remoteBytes[i] != networkBytes[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (remoteBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
        } catch (Exception e) {
            return false;
        }
    }


    @PostMapping("/register")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        checkRegisterRateLimit(httpRequest);
        if (userAccountRepository.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "用户名已存在");
        }

        UserAccount user = new UserAccount();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        userAccountRepository.save(user);

        String token = jwtService.generateToken(user.getUsername(), user.getRole(), user.getId(),
                com.nbamanager.security.JwtService.pwdMarkerOf(user.getPassword()));
        return new LoginResponse(token, user.getUsername(), user.getRole(), user.getAvatarUrl());
    }

    @PutMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public LoginResponse changePassword(
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
        // 改密立即失效该用户缓存的身份（pwdm 变化 → 旧 token 下一次请求即 401）
        userIdentityCache.evict(user.getUsername());
        // 改密即换发新 token（携带新密码标记）：全部旧 token 因标记失配而失效
        String freshToken = jwtService.generateToken(user.getUsername(), user.getRole(), user.getId(),
                com.nbamanager.security.JwtService.pwdMarkerOf(user.getPassword()));
        return new LoginResponse(freshToken, user.getUsername(), user.getRole(), user.getAvatarUrl());
    }

    @PutMapping("/username")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public LoginResponse changeUsername(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangeUsernameRequest request) {

        UserAccount user = userAccountRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "用户不存在"));

        if (request.newUsername().equals(user.getUsername())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "新用户名不能与原用户名相同");
        }

        if (userAccountRepository.existsByUsername(request.newUsername())) {
            throw new ApiException(HttpStatus.CONFLICT, "用户名已被占用");
        }

        String oldUsername = user.getUsername();
        user.setUsername(request.newUsername());
        userAccountRepository.save(user);
        // 改名：新旧两个键都要失效——旧键残留会让"改名后旧 token 仍可用 ≤45s"，
        // 破坏既有"改名即踢旧会话"的行为契约
        userIdentityCache.evict(oldUsername);
        userIdentityCache.evict(request.newUsername());

        // 发布身份变更事件（事务提交后广播）
        eventPublisher.publishEvent(new UserIdentityUpdatedEvent(
                user.getId(), user.getUsername(), user.getAvatarUrl()));

        // 生成新的token
        String token = jwtService.generateToken(user.getUsername(), user.getRole(), user.getId(),
                com.nbamanager.security.JwtService.pwdMarkerOf(user.getPassword()));
        return new LoginResponse(token, user.getUsername(), user.getRole(), user.getAvatarUrl());
    }
}
