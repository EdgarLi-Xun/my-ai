package cn.edgarli.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
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

    /** 用户消息 */
    public static final String ROLE_USER = "USER";
    /** AI 助手消息 */
    public static final String ROLE_ASSISTANT = "ASSISTANT";
    /** 系统提示消息（v1 UI 不暴露输入） */
    public static final String ROLE_SYSTEM = "SYSTEM";

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("conversation_id")
    private Long conversationId;

    private String role;

    private String content;

    @Column("is_orphaned")
    private Boolean isOrphaned;

    @Column("created_at")
    private LocalDateTime createdAt;
}