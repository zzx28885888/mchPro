package com.excelai.auth;

import com.excelai.config.AppProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
/**
 * 中文：负责签发与验签 JWT。密钥来自 app.jwt.secret，至少需要 32 字节用于 HMAC-SHA。
 * English: Issues and verifies JWTs. app.jwt.secret must contain at least 32 bytes for HMAC-SHA signing.
 */
public class JwtService {
    private final SecretKey key;
    private final AppProperties p;

    public JwtService(AppProperties p) {
        this.p = p;
        if (p.jwt().secret().getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("JWT secret too short");
        key = Keys.hmacShaKeyFor(p.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Long id, String email, String plan) {
        // 中文：subject 保存用户主键；email/plan 是便于 API 使用的声明，权限仍需由服务端重新查询。
        // English: The subject stores the user ID; email/plan are convenience claims, while authorization is reloaded server-side.
        var now = Instant.now();
        return Jwts.builder().subject(id.toString()).claim("email", email).claim("plan", plan)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(p.jwt().expirationHours() * 3600)))
                .signWith(key).compact();
    }

    public Long userId(String token) {
        return Long.valueOf(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject());
    }
}
