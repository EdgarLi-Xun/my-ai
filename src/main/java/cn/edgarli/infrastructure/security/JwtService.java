package cn.edgarli.infrastructure.security;

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
 * JWT signing and parsing. Secret comes from {@code my-ai.jwt.secret}, must be ≥ 32 bytes (256 bits),
 * 否则启动失败。Token claims 含 {@code uid}（userId）与 {@code role}
 * otherwise startup fails. Token claims include {@code uid} (userId) and {@code role}
 * （{@link User#ROLE_USER} / {@link User#ROLE_ADMIN}，ADR 0004）。
 * ({@link User#ROLE_USER} / {@link User#ROLE_ADMIN}, ADR 0004).
 */
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationSeconds;

    /**
     * 构造 JwtService 并校验密钥长度。
     * Construct JwtService and validate the secret length.
     *
     * @param secret 密钥字符串 / secret string
     * @param expirationSeconds 过期秒数 / expiration in seconds
     * @throws IllegalStateException 密钥长度 < 32 字节 / when secret is shorter than 32 bytes
     */
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

    /**
     * 签发带 role 的 JWT。
     * Issue a JWT carrying the given role.
     *
     * @param userId 用户 id / user id
     * @param role 角色（USER / ADMIN），可空 / role (USER / ADMIN), nullable
     * @return 紧凑序列化 JWT / compact JWT string
     */
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
    /** Legacy overload: default role = USER (used when user.role is not yet set at register). */
    public String generate(Long userId) {
        return generate(userId, User.ROLE_USER);
    }

    /**
     * 解析 userId。role 由调用方单独读 {@link #parseRole(String)}，
     * 这样注册阶段 userId 已知但 role 由数据库或 admin 判定时不会回退默认值。
     * Parse the userId claim. Role is read separately via {@link #parseRole(String)},
     * so that during register the userId is known while role is decided by DB/admin
     * without falling back to a default.
     *
     * @param token JWT 字符串 / JWT string
     * @return userId 缺失时返回 null / userId, or null if absent
     */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("uid", Long.class);
    }

    /**
     * 解析 role claim，缺时回退 USER。
     * Parse the role claim, falling back to USER when absent.
     *
     * @param token JWT 字符串 / JWT string
     * @return 角色字符串 / role string
     */
    public String parseRole(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Object role = claims.get("role");
        return role == null ? User.ROLE_USER : role.toString();
    }

    /**
     * 校验 JWT 签名与过期时间。
     * Validate JWT signature and expiration.
     *
     * @param token JWT 字符串 / JWT string
     * @return 合法且未过期返回 true / true if signature is valid and not expired
     */
    public boolean validate(String token) {
        try {
            parseUserId(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}