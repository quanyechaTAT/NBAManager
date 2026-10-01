package com.nbamanager.security;

import com.nbamanager.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    private static final String DEFAULT_SECRET_MARKER = "change-this-to-a-random-secret";

    /**
     * 弱密钥防护：内置的默认 secret 是公开已知的，攻击者可离线伪造任意用户（含 ADMIN）的 token。
     * 未显式配置 JWT_SECRET 时，改为进程内随机密钥并打 ERROR 提示（代价：每次重启全部 token 失效）。
     */
    @jakarta.annotation.PostConstruct
    public void guardWeakSecret() {
        if (secret == null || secret.isBlank() || secret.startsWith(DEFAULT_SECRET_MARKER)) {
            byte[] random = new byte[48];
            new java.security.SecureRandom().nextBytes(random);
            secret = java.util.Base64.getEncoder().encodeToString(random);
            log.error("检测到默认/空的 JWT secret（可被公开密钥伪造任意 token），已改用本次进程随机密钥。"
                    + "请尽快在 .env 中配置 JWT_SECRET 并重启，否则每次重启后所有登录会话失效。");
        }
    }

    public String generateToken(String username, Role role, Long userId, String pwdMarker) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(username)
                .claim("role", role.name())
                .claim("userId", userId)
                .claim("pwdm", pwdMarker == null ? "" : pwdMarker)
                .issuedAt(now)
                .expiration(exp)
                .signWith(signingKey())
                .compact();
    }

    /** 密码标记：BCrypt hash 尾 8 位。改密码后 hash 变化，旧 token 的标记即失配，实现"改密踢旧会话" */
    public static String pwdMarkerOf(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.length() < 8) {
            return "";
        }
        return encodedPassword.substring(encodedPassword.length() - 8);
    }

    public String extractPwdMarker(String token) {
        return parseClaims(token).get("pwdm", String.class);
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public Role extractRole(String token) {
        String r = parseClaims(token).get("role", String.class);
        return Role.valueOf(r);
    }

    public boolean isTokenValid(String token, String expectedUsername, String expectedPwdMarker) {
        try {
            String sub = extractUsername(token);
            Date exp = parseClaims(token).getExpiration();
            String pwdm = extractPwdMarker(token);
            return sub.equals(expectedUsername)
                    && exp.after(new Date())
                    && java.util.Objects.equals(pwdm == null ? "" : pwdm,
                            expectedPwdMarker == null ? "" : expectedPwdMarker);
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(padSecret(secret));
    }

    private static byte[] padSecret(String s) {
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (b.length >= 32) {
            return b;
        }
        byte[] out = new byte[32];
        System.arraycopy(b, 0, out, 0, b.length);
        for (int i = b.length; i < 32; i++) {
            out[i] = (byte) i;
        }
        return out;
    }
}
