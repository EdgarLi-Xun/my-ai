package cn.edgarli.web.converter;

import cn.edgarli.entity.AiCallLog;
import cn.edgarli.web.vo.AiCallLogVo;

/**
 * AiCallLog DO → AiCallLogVo 转换（ADR 0005 §6）。
 * AiCallLog DO → AiCallLogVo converter (ADR 0005 §6).
 * <p>
 * 纯手动映射，避免引入 MapStruct 依赖；所有字段从 DO 直接拷贝。
 * Pure manual mapping (no MapStruct); every field is copied directly from the DO.
 *
 * @author MyAi
 */
public final class AiCallLogConverter {

    private AiCallLogConverter() {
    }

    /**
     * 将 AI 调用日志实体转为响应 VO。
     * Convert an AI call log entity into its response VO.
     * <p>
     * 入参为 null 时直接返回 null，便于在流式管道中安全链接。
     * Returns {@code null} when the input is {@code null} so callers can chain safely.
     *
     * @param row AI 调用日志实体 / AI call log entity (may be null)
     * @return 响应 VO / response VO (null when input is null)
     */
    public static AiCallLogVo toResponse(AiCallLog row) {
        if (row == null) {
            return null;
        }
        return new AiCallLogVo(
                row.getId(),
                row.getUserId(),
                row.getConversationId(),
                row.getMessageId(),
                row.getProvider(),
                row.getModelName(),
                row.getStatus(),
                row.getLatencyMs(),
                row.getInputTokens(),
                row.getOutputTokens(),
                row.getErrorMessage(),
                row.getTraceId(),
                row.getCreatedAt());
    }
}