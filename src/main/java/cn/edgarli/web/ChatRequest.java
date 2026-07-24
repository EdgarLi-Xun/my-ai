package cn.edgarli.web;

import cn.edgarli.ai.ChatMessage;

import java.util.List;

/**
 * POST /api/chat 的请求体。
 *
 * @param userId   用户 ID，必须等于当前登录用户 ID，否则 ChatController 抛 4030
 * @param messages 按顺序排列的对话历史
 */
public record ChatRequest(Long userId, List<ChatMessage> messages) {
}
