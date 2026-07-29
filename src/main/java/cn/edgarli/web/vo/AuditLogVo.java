package cn.edgarli.web.vo;

import java.time.LocalDateTime;

/**
 * Audit log response DTO (DO/VO split, ADR 0005 §6).
 * 审计日志响应 DTO（DO/VO 分层，ADR 0005 §6）。
 *
 * @param id         审计日志 ID / audit log ID
 * @param userId     触发动作的用户 ID（系统动作为 null）/ user ID who triggered the action (null for system actions)
 * @param action     动作名 / action name
 * @param targetType 目标实体类型 / target entity type
 * @param targetId   目标实体 ID / target entity ID
 * @param ipAddress  客户端 IP（可空）/ client IP (may be empty)
 * @param userAgent  User-Agent（可空）/ user agent (may be empty)
 * @param createdAt  创建时间 / creation timestamp
 * @param deletedAt  软删时间（null = 存活）/ soft-delete timestamp (null = alive)
 */
public record AuditLogVo(
        Long id,
        Long userId,
        String action,
        String targetType,
        Long targetId,
        String ipAddress,
        String userAgent,
        LocalDateTime createdAt,
        LocalDateTime deletedAt) {
}