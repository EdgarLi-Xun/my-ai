package cn.edgarli.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
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

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("user_id")
    private Long userId;

    private String title;

    @Column("title_manually_set")
    private Boolean titleManuallySet;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("deleted_at")
    private LocalDateTime deletedAt;
}