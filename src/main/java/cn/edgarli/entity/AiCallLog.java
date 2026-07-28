package cn.edgarli.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 调用日志（ADR 0004 §3）。
 * <p>
 * 每次 AI 调用留痕：{@link #status} 取 {@link #STATUS_SUCCESS} / {@link #STATUS_FAILURE}；
 * {@link #inputTokens} / {@link #outputTokens} 允许 NULL（Ollama 等 provider 不一定返回 usage）；
 * {@link #errorMessage} 仅在失败时填。
 * <p>
 * 不存明文 prompt / completion 内容——避免日志里出现敏感对话；
 * 通过 {@link #messageId} 可回查 {@code message.content}。
 */
@Data
@NoArgsConstructor
@Table("ai_call_log")
public class AiCallLog {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILURE = "FAILURE";

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("conversation_id")
    private Long conversationId;

    @Column("message_id")
    private Long messageId;

    private String provider;

    @Column("model")
    private String modelName;

    private String status;

    @Column("latency_ms")
    private Long latencyMs;

    @Column("input_tokens")
    private Integer inputTokens;

    @Column("output_tokens")
    private Integer outputTokens;

    @Column("error_message")
    private String errorMessage;

    @Column("trace_id")
    private String traceId;

    @Column("created_at")
    private LocalDateTime createdAt;
}