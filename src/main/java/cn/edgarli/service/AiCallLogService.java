package cn.edgarli.service;

import cn.edgarli.entity.AiCallLog;
import cn.edgarli.mapper.AiCallLogMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * AI 调用日志写入（ADR 0004 §3）。
 * <p>
 * 调用方约定：{@link #recordSuccess} / {@link #recordFailure} 由
 * {@link cn.edgarli.service.MessageService} 在 AI 流式 onComplete / onError
 * 时调用；trace_id 从 MDC 取（{@link TraceIdFilter} 已注入）。
 */
@Service
public class AiCallLogService {

    private final AiCallLogMapper mapper;

    public AiCallLogService(AiCallLogMapper mapper) {
        this.mapper = mapper;
    }

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
        row.setCreatedAt(LocalDateTime.now());
        mapper.insert(row);
        return row.getId();
    }

    @Transactional
    public Long recordFailure(Long userId,
                              Long conversationId,
                              Long messageId,
                              String provider,
                              String modelName,
                              long latencyMs,
                              String errorMessage) {
        AiCallLog row = new AiCallLog();
        row.setUserId(userId);
        row.setConversationId(conversationId);
        row.setMessageId(messageId);
        row.setProvider(provider);
        row.setModelName(modelName);
        row.setStatus(AiCallLog.STATUS_FAILURE);
        row.setLatencyMs(latencyMs);
        row.setErrorMessage(errorMessage);
        row.setTraceId(MDC.get("trace_id"));
        row.setCreatedAt(LocalDateTime.now());
        mapper.insert(row);
        return row.getId();
    }
}