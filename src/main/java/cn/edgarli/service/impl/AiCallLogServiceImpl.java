package cn.edgarli.service.impl;

import cn.edgarli.entity.AiCallLog;
import cn.edgarli.service.AiCallLogService;
import cn.edgarli.mapper.AiCallLogMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Default implementation of {@link AiCallLogService}.
 * {@link AiCallLogService} 默认实现。
 *
 * @author MyAi
 */
@Service
public class AiCallLogServiceImpl implements AiCallLogService {

    private final AiCallLogMapper mapper;

    public AiCallLogServiceImpl(AiCallLogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Record a successful AI call. Transactional: single insert.
     * 记录成功的 AI 调用。事务边界：单条 insert。
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id (optional) / 对话 ID（可空）
     * @param messageId message id (optional) / 消息 ID（可空）
     * @param provider provider name / provider 名
     * @param modelName model name / 模型名
     * @param latencyMs call latency in ms / 调用耗时（毫秒）
     * @param inputTokens input tokens (optional) / 输入 tokens（可空）
     * @param outputTokens output tokens (optional) / 输出 tokens（可空）
     * @return id of the new log row / 新建日志的 ID
     */
    @Override
    @Transactional
    public Long recordSuccess(Long userId,
                              Long conversationId,
                              Long messageId,
                              String provider,
                              String modelName,
                              long latencyMs,
                              Integer inputTokens,
                              Integer outputTokens) {
        AiCallLog row = new AiCallLog();
        // 新日志行 / new log row
        row.setUserId(userId);
        row.setConversationId(conversationId);
        row.setMessageId(messageId);
        row.setProvider(provider);
        row.setModelName(modelName);
        row.setStatus(AiCallLog.STATUS_SUCCESS);
        row.setLatencyMs(latencyMs);
        row.setInputTokens(inputTokens);
        row.setOutputTokens(outputTokens);
        row.setTraceId(MDC.get("trace_id"));
        // 从 MDC 取 trace_id（TraceIdFilter 已注入） / pull trace_id from MDC (TraceIdFilter injected it)
        row.setCreatedAt(LocalDateTime.now());
        mapper.insert(row);
        return row.getId();
    }

    /**
     * Record a failed AI call. Transactional: single insert.
     * 记录失败的 AI 调用。事务边界：单条 insert。
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id (optional) / 对话 ID（可空）
     * @param messageId message id (optional) / 消息 ID（可空）
     * @param provider provider name / provider 名
     * @param modelName model name / 模型名
     * @param latencyMs call latency in ms / 调用耗时（毫秒）
     * @param errorMessage error message / 错误信息
     * @return id of the new log row / 新建日志的 ID
     */
    @Override
    @Transactional
    public Long recordFailure(Long userId,
                              Long conversationId,
                              Long messageId,
                              String provider,
                              String modelName,
                              long latencyMs,
                              String errorMessage) {
        AiCallLog row = new AiCallLog();
        // 新日志行 / new log row
        row.setUserId(userId);
        row.setConversationId(conversationId);
        row.setMessageId(messageId);
        row.setProvider(provider);
        row.setModelName(modelName);
        row.setStatus(AiCallLog.STATUS_FAILURE);
        row.setLatencyMs(latencyMs);
        row.setErrorMessage(errorMessage);
        row.setTraceId(MDC.get("trace_id"));
        // 从 MDC 取 trace_id / pull trace_id from MDC
        row.setCreatedAt(LocalDateTime.now());
        mapper.insert(row);
        return row.getId();
    }
}