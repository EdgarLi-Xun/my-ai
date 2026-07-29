package cn.edgarli.service;

/**
 * AI call log writer (ADR 0004 §3).
 * AI 调用日志写入（ADR 0004 §3）。
 * <p>
 * Called by message send/recv services on AI stream onComplete / onError;
 * trace_id is read from MDC (already injected by {@link cn.edgarli.infrastructure.observability.TraceIdFilter}).
 * 由消息收发服务在 AI 流式 onComplete / onError 时调用；
 * trace_id 从 MDC 取（{@link cn.edgarli.infrastructure.observability.TraceIdFilter} 已注入）。
 *
 * @author MyAi
 */
public interface AiCallLogService {

    /**
     * Record a successful AI call.
     * 记录成功的 AI 调用。
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id (optional) / 对话 ID（可空）
     * @param messageId message id (optional) / 消息 ID（可空）
     * @param provider provider name / provider 名
     * @param modelName model name / 模型名
     * @param latencyMs call latency in milliseconds / 调用耗时（毫秒）
     * @param inputTokens input tokens (optional; some providers don't return usage) / 输入 tokens（可空，部分 provider 不返回）
     * @param outputTokens output tokens (optional; some providers don't return usage) / 输出 tokens（可空，部分 provider 不返回）
     * @return id of the new log row / 新建日志的 ID
     */
    Long recordSuccess(Long userId,
                       Long conversationId,
                       Long messageId,
                       String provider,
                       String modelName,
                       long latencyMs,
                       Integer inputTokens,
                       Integer outputTokens);

    /**
     * Record a failed AI call.
     * 记录失败的 AI 调用。
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id (optional) / 对话 ID（可空）
     * @param messageId message id (optional) / 消息 ID（可空）
     * @param provider provider name / provider 名
     * @param modelName model name / 模型名
     * @param latencyMs call latency in milliseconds / 调用耗时（毫秒）
     * @param errorMessage error message / 错误信息
     * @return id of the new log row / 新建日志的 ID
     */
    Long recordFailure(Long userId,
                       Long conversationId,
                       Long messageId,
                       String provider,
                       String modelName,
                       long latencyMs,
                       String errorMessage);
}