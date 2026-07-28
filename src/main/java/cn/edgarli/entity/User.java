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
 * 用户实体。
 * <p>
 * {@code defaultKeyId} 指向 {@link UserApiKey#getId()}，当默认 Key 被禁用或删除时
 * 通过 {@code ON DELETE SET NULL} 置 null（参见 schema.sql）。{@code passwordHash}
 * 用 BCrypt 散列，加 {@link JsonIgnore} / {@link ToString.Exclude} 不外露。
 * {@code role} 是 RBAC 字段（ADR 0004），取值 {@link #ROLE_USER} / {@link #ROLE_ADMIN}，
 * 由 {@link cn.edgarli.service.AuthService} 在 register / login 时根据
 * {@link cn.edgarli.config.AdminProperties} 判定写入。
 */
@Data
@NoArgsConstructor
@Table("user")
public class User {

    /** 普通用户角色，可访问除 {@code /api/logs/**} 之外的所有受保护接口。 */
    public static final String ROLE_USER = "USER";

    /** 管理员角色，可访问 {@code /api/logs/**} 查询 AI 调用日志与审计日志。 */
    public static final String ROLE_ADMIN = "ADMIN";

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String name;

    private String email;

    @Column("default_key_id")
    private Long defaultKeyId;

    @Column("create_time")
    private LocalDateTime createTime;

    @Column("password_hash")
    @JsonIgnore
    @ToString.Exclude
    private String passwordHash;

    /**
     * RBAC 角色。默认 {@link #ROLE_USER}；命中 admin email 列表时为 {@link #ROLE_ADMIN}。
     */
    private String role = ROLE_USER;
}
