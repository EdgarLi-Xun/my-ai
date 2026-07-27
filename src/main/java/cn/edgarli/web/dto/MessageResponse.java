package cn.edgarli.web.dto;

import java.time.LocalDateTime;

/**
 * 消息响应 DTO（ADR 0003）。
 * <p>
 * {@code isOrphaned = true} 的消息默认不返回（除非 query 显式带 {@code include_orphaned = true}），
 * 用于编辑/重新生成后保留旧版本以备回溯。
 */
public record MessageResponse(
        Long id,
        Long conversationId,
        String role,
        String content,
        boolean isOrphaned,
        LocalDateTime createdAt) {
}