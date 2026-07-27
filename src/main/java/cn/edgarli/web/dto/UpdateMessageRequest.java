package cn.edgarli.web.dto;

/**
 * 更新 USER 消息内容的请求体（ADR 0003）。
 * <p>
 * PATCH 调用时 service 把该消息及之后所有消息标 {@code is_orphaned = TRUE}，
 * AI 不自动重跑（需用户主动发新消息或重新生成）。
 */
public record UpdateMessageRequest(String content) {
}