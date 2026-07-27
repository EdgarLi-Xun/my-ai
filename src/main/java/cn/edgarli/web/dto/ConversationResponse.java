package cn.edgarli.web.dto;

import java.time.LocalDateTime;

/**
 * 对话响应 DTO（ADR 0003）。
 */
public record ConversationResponse(
        Long id,
        Long userId,
        String title,
        boolean titleManuallySet,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt) {
}