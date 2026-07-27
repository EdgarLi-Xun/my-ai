package cn.edgarli.web.dto;

/**
 * 更新对话标题的请求体（ADR 0003）。
 * <p>
 * PATCH 标题时 service 会强制把 {@code title_manually_set} 设为 TRUE，
 * 防止后续"首条 USER 消息覆盖标题"逻辑误覆盖用户手动改过的标题。
 */
public record UpdateConversationRequest(String title) {
}