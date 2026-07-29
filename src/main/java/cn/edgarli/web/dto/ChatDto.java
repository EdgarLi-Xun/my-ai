package cn.edgarli.web.dto;

import cn.edgarli.service.ai.ChatMessage;

import java.util.List;

/**
 * Request body for POST /api/chat (deprecated alias for /api/conversations/{id}/messages).
 * POST /api/chat 的请求体（已废弃；改用 /api/conversations/{id}/messages）。
 *
 * @param userId   用户 ID，必须等于当前登录用户 ID，否则 ChatController 抛 4030 / user ID; must equal the current logged-in user, otherwise ChatController throws 4030
 * @param messages 按顺序排列的对话历史 / ordered conversation history
 */
public record ChatDto(Long userId, List<ChatMessage> messages) {
}