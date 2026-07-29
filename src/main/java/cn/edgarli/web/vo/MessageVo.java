package cn.edgarli.web.vo;

import java.time.LocalDateTime;

/**
 * Message response DTO (ADR 0003).
 * 消息响应 DTO（ADR 0003）。
 * <p>
 * {@code isOrphaned = true} 的消息默认不返回（除非 query 显式带 {@code include_orphaned = true}），
 * 用于编辑/重新生成后保留旧版本以备回溯。
 *
 * @param id             消息 ID / message ID
 * @param conversationId 所属对话 ID / owning conversation ID
 * @param role           消息角色（USER / ASSISTANT / SYSTEM）/ message role (USER / ASSISTANT / SYSTEM)
 * @param content        消息正文 / message body
 * @param isOrphaned     是否被编辑/重新生成路径作废（默认不返回 true）/ orphaned flag (true ones hidden by default)
 * @param createdAt      创建时间 / creation timestamp
 */
public record MessageVo(
        Long id,
        Long conversationId,
        String role,
        String content,
        boolean isOrphaned,
        LocalDateTime createdAt) {
}