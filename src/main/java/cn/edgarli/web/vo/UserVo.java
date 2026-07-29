package cn.edgarli.web.vo;

import java.time.LocalDateTime;

/**
 * User response DTO (DO/VO split, ADR 0005 §6).
 * 用户响应 DTO（DO/VO 分层，ADR 0005 §6）。
 * <p>
 * 不含 {@code passwordHash} 等敏感字段；持久化对象 {@link cn.edgarli.entity.User}
 * 不再直接对外返回。
 *
 * @param id           用户 ID / user ID
 * @param name         用户名 / user name
 * @param email        邮箱 / email
 * @param role         RBAC 角色（USER / ADMIN）/ RBAC role (USER / ADMIN)
 * @param defaultKeyId 默认 Key ID（可能为 null）/ default key ID (nullable)
 * @param createTime   注册时间 / registration timestamp
 */
public record UserVo(
        Long id,
        String name,
        String email,
        String role,
        Long defaultKeyId,
        LocalDateTime createTime) {
}