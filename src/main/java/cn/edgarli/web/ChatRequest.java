package cn.edgarli.web;

import cn.edgarli.ai.ChatMessage;

import java.util.List;

/**
 * POST /api/chat 的请求体。
 *
 * @param userId   用户 ID，后端使用该用户的默认 Key
 * @param messages 按顺序排列的对话历史
 */
public record ChatRequest(Long userId, List<ChatMessage> messages) {
}
