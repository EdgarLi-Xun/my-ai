package cn.edgarli.web.vo;

/**
 * Response body for POST /api/chat (deprecated alias).
 * POST /api/chat 的响应体（已废弃端点）。
 *
 * @param reply AI 回复文本 / AI reply text
 */
public record ChatVo(String reply) {
}