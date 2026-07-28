package cn.edgarli.security;

import cn.edgarli.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 签发与解析。密钥来源 {@code my-ai.jwt.secret}，必须 ≥ 32 字节（256 bits），
 * 否则启动失败。Token claims 含 {@code uid}（userId）与 {@code role}
 * （{@link User#ROLE_USER} / {@link User#ROLE_ADMIN}，ADR 0004）。
 */
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationSeconds;

    public JwtService(
            @Value("${my-ai.jwt.secret}") String secret,
            @Value("${my-ai.jwt.expiration:604800}") long expirationSeconds) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("my-ai.jwt.secret must be at least 32 bytes (256 bits)");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationSeconds = expirationSeconds;
    }

    public String generate(Long userId, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000);
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        claims.put("role", role == null ? User.ROLE_USER : role);
        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /** 兼容老调用：默认 role = USER（保留 register 时 user.role 还没填的场景）。 */
    public String generate(Long userId) {
        return generate(userId, User.ROLE_USER);
    }

    /**
     * 解析 userId。role 由调用方单独读 {@link #parseRole(String)}，
     * 这样注册阶段 userId 已知但 role 由数据库或 admin 判定时不会回退默认值。
     */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("uid", Long.class);
    }

    public String parseRole(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Object role = claims.get("role");
        return role == null ? User.ROLE_USER : role.toString();
    }

    public boolean validate(String token) {
        try {
            parseUserId(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}