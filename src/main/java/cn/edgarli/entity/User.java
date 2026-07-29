package cn.edgarli.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * User entity.
 * 用户实体。
 * <p>
 * {@code defaultKeyId} 指向 {@link UserApiKey#getId()}，当默认 Key 被禁用或删除时
 * 通过 {@code ON DELETE SET NULL} 置 null（参见 schema.sql）。{@code passwordHash}
 * 用 BCrypt 散列，加 {@link JsonIgnore} / {@link ToString.Exclude} 不外露。
 * {@code role} 是 RBAC 字段（ADR 0004），取值 {@link #ROLE_USER} / {@link #ROLE_ADMIN}，
 * 由 {@link cn.edgarli.service.AuthService} 在 register / login 时根据
 * {@link cn.edgarli.infrastructure.config.AdminProperties} 判定写入。
 */
@Data
@NoArgsConstructor
@Table("user")
public class User {

    /** 普通用户角色，可访问除 {@code /api/logs/**} 之外的所有受保护接口 / Regular user role; can access every protected endpoint except {@code /api/logs/**}. */
    public static final String ROLE_USER = "USER";

    /** 管理员角色，可访问 {@code /api/logs/**} 查询 AI 调用日志与审计日志 / Admin role; can access {@code /api/logs/**} to query AI call and audit logs. */
    public static final String ROLE_ADMIN = "ADMIN";

    /** 用户 ID（主键，自增）/ user ID (PK, auto-increment) */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 用户名（注册时设置，不允许重复）/ user name (set on register, must be unique) */
    private String name;

    /** 邮箱（登录身份）/ email (login identifier) */
    private String email;

    /** 默认 Key 的 ID，指向 user_api_key.id / default key ID, references {@code user_api_key.id} */
    @Column("default_key_id")
    private Long defaultKeyId;

    /** 注册时间 / registration timestamp */
    @Column("create_time")
    private LocalDateTime createTime;

    /** BCrypt 散列后的密码（不外露，JsonIgnore + ToString.Exclude）/ BCrypt-hashed password (never serialized; JsonIgnore + ToString.Exclude) */
    @Column("password_hash")
    @JsonIgnore
    @ToString.Exclude
    private String passwordHash;

    /** RBAC 角色。默认 {@link #ROLE_USER}；命中 admin email 列表时为 {@link #ROLE_ADMIN} / RBAC role; defaults to {@link #ROLE_USER}, set to {@link #ROLE_ADMIN} when the email matches the admin allowlist */
    private String role = ROLE_USER;
}