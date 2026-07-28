package cn.edgarli.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
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

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("user_id")
    private Long userId;

    private String action;

    @Column("target_type")
    private String targetType;

    @Column("target_id")
    private Long targetId;

    @Column("ip_address")
    private String ipAddress;

    @Column("user_agent")
    private String userAgent;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("deleted_at")
    private LocalDateTime deletedAt;
}