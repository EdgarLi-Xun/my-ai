package cn.edgarli.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Business audit log (ADR 0004 §4).
 * 业务审计日志（ADR 0004 §4）。
 * <p>
 * 用户增删改 Key / 对话 / 消息等"业务动作"留痕。{@link #userId} 允许 NULL
 * （系统后台动作，例如 {@code ConversationCleanupTask} 30 天后 hard delete）。
 * <p>
 * 默认查询 {@code WHERE deleted_at IS NULL}；{@code LogCleanupTask} 在
 * {@code created_at + retentionDays} 之后做软删，retentionDays 之后再物理删。
 */
@Data
@NoArgsConstructor
@Table("audit_log")
public class AuditLog {

    /** 审计日志 ID（主键，自增）/ audit log ID (PK, auto-increment) */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 触发动作的用户 ID（系统动作为 NULL，如定时清理任务）/ user ID who triggered the action (NULL for system actions like cleanup tasks) */
    @Column("user_id")
    private Long userId;

    /** 动作名（如 CREATE_KEY / UPDATE_KEY / DELETE_CONVERSATION）/ action name (e.g. CREATE_KEY / UPDATE_KEY / DELETE_CONVERSATION) */
    private String action;

    /** 目标实体类型（如 USER_API_KEY / CONVERSATION / MESSAGE）/ target entity type (e.g. USER_API_KEY / CONVERSATION / MESSAGE) */
    @Column("target_type")
    private String targetType;

    /** 目标实体 ID（与 target_type 组合定位目标行）/ target entity ID (combined with target_type to locate the row) */
    @Column("target_id")
    private Long targetId;

    /** 客户端 IP（来自 HTTP request，可空）/ client IP (from HTTP request, may be empty) */
    @Column("ip_address")
    private String ipAddress;

    /** User-Agent（来自 HTTP request，可空）/ user agent (from HTTP request, may be empty) */
    @Column("user_agent")
    private String userAgent;

    /** 创建时间 / creation timestamp */
    @Column("created_at")
    private LocalDateTime createdAt;

    /** 软删时间（LogCleanupTask 第一阶段设置）；保留天数到后物理删 / soft-delete timestamp (set by LogCleanupTask phase 1); hard-deleted after retention window */
    @Column("deleted_at")
    private LocalDateTime deletedAt;
}