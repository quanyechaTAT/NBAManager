package com.nbamanager.security;

import com.nbamanager.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    public String generateToken(String username, Role role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(username)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(exp)
                .signWith(signingKey())
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public Role extractRole(String token) {
        String r = parseClaims(token).get("role", String.class);
        return Role.valueOf(r);
    }

    public boolean isTokenValid(String token, String expectedUsername) {
        try {
            String sub = extractUsername(token);
            Date exp = parseClaims(token).getExpiration();
            return sub.equals(expectedUsername) && exp.after(new Date());
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
