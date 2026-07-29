package cn.edgarli.web.vo;

import java.time.LocalDateTime;

/**
 * Conversation response DTO (ADR 0003).
 * 对话响应 DTO（ADR 0003）。
 *
 * @param id                对话 ID / conversation ID
 * @param userId            所属用户 ID / owning user ID
 * @param title             对话标题 / conversation title
 * @param titleManuallySet  标题是否被手动设置过 / whether the title was manually set
 * @param createdAt         创建时间 / creation timestamp
 * @param updatedAt         最后更新时间（侧栏排序依据）/ last-update timestamp (used for sidebar ordering)
 * @param deletedAt         软删时间（null = 存活）/ soft-delete timestamp (null = alive)
 */
public record ConversationVo(
        Long id,
        Long userId,
        String title,
        boolean titleManuallySet,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt) {
}