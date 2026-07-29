package cn.edgarli.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI call log (ADR 0004 §3).
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

    /** 成功状态 / success status */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 失败状态 / failure status */
    public static final String STATUS_FAILURE = "FAILURE";

    /** 日志 ID（主键，自增）/ log ID (PK, auto-increment) */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 触发调用的用户 ID / triggering user ID */
    @Column("user_id")
    private Long userId;

    /** 所属对话 ID（可空——非对话场景）/ conversation ID (nullable for non-conversation calls) */
    @Column("conversation_id")
    private Long conversationId;

    /** 所属消息 ID（ASSISTANT 消息生成时回填；非对话场景可空）/ message ID (filled when generating an ASSISTANT message; nullable otherwise) */
    @Column("message_id")
    private Long messageId;

    /** 厂家名（与 UserApiKey.provider 对齐）/ provider name (matches UserApiKey.provider) */
    private String provider;

    /** 模型名 / model name */
    @Column("model")
    private String modelName;

    /** 调用状态：SUCCESS / FAILURE / call status: SUCCESS / FAILURE */
    private String status;

    /** 端到端延迟（毫秒，从 stream 开始到 onComplete/onError）/ end-to-end latency in ms (from stream start to onComplete/onError) */
    @Column("latency_ms")
    private Long latencyMs;

    /** 输入 token 数（允许 NULL——Ollama 等不一定返回）/ input token count (nullable; some providers like Ollama don't return usage) */
    @Column("input_tokens")
    private Integer inputTokens;

    /** 输出 token 数（允许 NULL）/ output token count (nullable) */
    @Column("output_tokens")
    private Integer outputTokens;

    /** 错误信息（仅 FAILURE 时填；不存完整堆栈避免日志膨胀）/ error message (FAILURE only; stack trace intentionally omitted to keep logs compact) */
    @Column("error_message")
    private String errorMessage;

    /** TraceId（与 TraceIdFilter 注入到 MDC 的 trace_id 对齐，便于跨日志关联）/ trace ID (matches TraceIdFilter's MDC trace_id for cross-log correlation) */
    @Column("trace_id")
    private String traceId;

    /** 创建时间 / creation timestamp */
    @Column("created_at")
    private LocalDateTime createdAt;
}