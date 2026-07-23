package cn.edgarli.ai;

/**
 * A single message in a chat conversation, used to pass multi-turn
 * history from the web layer to the AI service.
 *
 * @param role    "system", "user" or "assistant" (case-insensitive)
 * @param content message text
 */
public record ChatMessage(String role, String content) {
}
