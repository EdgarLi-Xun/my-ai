package cn.edgarli.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Conversation entity (ADR 0003).
 * 对话实体（ADR 0003）。
 * <p>
 * 一个对话是 1..N 条 {@link Message} 的有序集合，AI 调取上下文时只看
 * {@code is_orphaned = FALSE} 的消息。{@code titleManuallySet} 为 true 时，
 * 后续"首条 USER 消息覆盖标题"逻辑不再生效。
 * <p>
 * 软删：{@code deletedAt} 不为 null 表示已软删，{@code ConversationCleanupTask}
 * 在保留天数过期后 hard delete。
 */
@Data
@NoArgsConstructor
@Table("conversation")
public class Conversation {

    /** 对话 ID（主键，自增）/ conversation ID (PK, auto-increment) */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 所属用户 ID（FK → user.id）/ owning user ID (FK → user.id) */
    @Column("user_id")
    private Long userId;

    /** 对话标题（首条 USER 消息触发自动生成；titleManuallySet=true 后不再覆盖）/ conversation title (auto-set from first USER message unless titleManuallySet=true) */
    private String title;

    /** 标题是否被用户手动设置过（true 后不再被自动覆盖）/ whether the title was manually set (once true, auto-overwrite is disabled) */
    @Column("title_manually_set")
    private Boolean titleManuallySet;

    /** 创建时间 / creation timestamp */
    @Column("created_at")
    private LocalDateTime createdAt;

    /** 最后更新时间（USER/ASSISTANT 消息插入、edit/regenerate 标 orphan 时显式 touch）/ last-update timestamp (touched on USER/ASSISTANT insert and on edit/regenerate orphan marking) */
    @Column("updated_at")
    private LocalDateTime updatedAt;

    /** 软删时间（null = 未删）；保留天数到期后由 ConversationCleanupTask hard delete / soft-delete timestamp (null = alive); hard-deleted by ConversationCleanupTask after retention */
    @Column("deleted_at")
    private LocalDateTime deletedAt;
}