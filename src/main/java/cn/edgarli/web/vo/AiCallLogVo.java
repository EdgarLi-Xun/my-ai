package cn.edgarli.web.vo;

import java.time.LocalDateTime;

/**
 * AI call log response DTO (DO/VO split, ADR 0005 §6).
 * AI 调用日志响应 DTO（DO/VO 分层，ADR 0005 §6）。
 *
 * @param id             日志 ID / log ID
 * @param userId         触发调用的用户 ID / triggering user ID
 * @param conversationId 所属对话 ID（可空）/ conversation ID (nullable)
 * @param messageId      所属消息 ID（可空）/ message ID (nullable)
 * @param provider       厂家名 / provider name
 * @param modelName      模型名 / model name
 * @param status         调用状态：SUCCESS / FAILURE / call status (SUCCESS / FAILURE)
 * @param latencyMs      端到端延迟（毫秒）/ end-to-end latency in ms
 * @param inputTokens    输入 token 数（可空——Ollama 等不一定返回）/ input token count (nullable)
 * @param outputTokens   输出 token 数（可空）/ output token count (nullable)
 * @param errorMessage   错误信息（FAILURE 时填；SUCCESS 为 null）/ error message (FAILURE only; null on SUCCESS)
 * @param traceId        TraceId（与 MDC trace_id 对齐）/ trace ID (matches MDC trace_id)
 * @param createdAt      创建时间 / creation timestamp
 */
public record AiCallLogVo(
        Long id,
        Long userId,
        Long conversationId,
        Long messageId,
        String provider,
        String modelName,
        String status,
        Long latencyMs,
        Integer inputTokens,
        Integer outputTokens,
        String errorMessage,
        String traceId,
        LocalDateTime createdAt) {
}