package cn.edgarli.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Message entity (ADR 0003).
 * 消息实体（ADR 0003）。
 * <p>
 * {@code role} 取值由 schema.sql 的 {@code message_role_check} 约束为
 * {@code USER} / {@code ASSISTANT} / {@code SYSTEM} 之一，本类用 String
 * 字段保持与 {@link UserApiKey#getProtocol()} 相同的写法（service 层做 enum 转换）。
 * <p>
 * {@code isOrphaned} 为 true 时表示该消息已被编辑/重新生成路径作废，AI 取上下文时跳过。
 */
@Data
@NoArgsConstructor
@Table("message")
public class Message {

    /** 用户消息 / user message */
    public static final String ROLE_USER = "USER";
    /** AI 助手消息 / AI assistant message */
    public static final String ROLE_ASSISTANT = "ASSISTANT";
    /** 系统提示消息（v1 UI 不暴露输入）/ system prompt message (UI does not expose input in v1) */
    public static final String ROLE_SYSTEM = "SYSTEM";

    /** 消息 ID（主键，自增）/ message ID (PK, auto-increment) */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 所属对话 ID（FK → conversation.id）/ owning conversation ID (FK → conversation.id) */
    @Column("conversation_id")
    private Long conversationId;

    /** 消息角色（USER / ASSISTANT / SYSTEM，schema 层 CHECK 约束）/ message role (USER / ASSISTANT / SYSTEM; enforced by schema CHECK constraint) */
    private String role;

    /** 消息正文（USER / SYSTEM 由前端写入；ASSISTANT 由 AI 流式回填）/ message body (USER / SYSTEM written by client; ASSISTANT filled in by AI streaming) */
    private String content;

    /** 是否被编辑/重新生成路径作废（true 时 AI 取上下文跳过；保留供回溯）/ orphaned flag (true means skipped when building AI context; kept for history) */
    @Column("is_orphaned")
    private Boolean isOrphaned;

    /** 创建时间 / creation timestamp */
    @Column("created_at")
    private LocalDateTime createdAt;
}