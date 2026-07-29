package cn.edgarli.service.ai;

/**
 * A single message in a chat conversation, used to pass multi-turn
 * history from the web layer to the AI service.
 * 单条对话消息，用于把多轮历史从 web 层传到 AI 服务。
 *
 * @param role    "system", "user" or "assistant" (case-insensitive) / 角色，区分大小写均可
 * @param content message text / 消息正文
 */
public record ChatMessage(String role, String content) {
}
