package cn.edgarli.service;

import cn.edgarli.entity.Message;
import cn.edgarli.web.vo.MessageVo;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Message command service (ADR 0005 §3).
 * 消息命令服务（ADR 0005 §3）。
 * <p>
 * Write operations + streaming reply; separated from {@link MessageQueryService}.
 * 写操作 + 流式回复；与 {@link MessageQueryService} 分离。
 *
 * @author MyAi
 */
public interface MessageCommandService {

    /**
     * User sends a new message → stream back AI reply.
     * 用户发新消息 → 流式返回 AI 回复。
     * <p>
     * Returns an {@link SseEmitter}; the client reads {@code text/event-stream} frames:
     * 返回 {@link SseEmitter}，客户端读取 {@code text/event-stream} 帧：
     * <ul>
     *   <li>{@code event: token} / {@code data: {"text": "..."}}</li>
     *   <li>{@code event: done} / {@code data: {"messageId": ..., "conversationId": ...}}</li>
     *   <li>{@code event: error} / {@code data: {"code": ..., "message": "..."}}</li>
     * </ul>
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id / 对话 ID
     * @param content user message content / 用户消息内容
     * @return SSE emitter / SSE 发射器
     */
    SseEmitter streamReply(Long userId, Long conversationId, String content);

    /**
     * Regenerate an ASSISTANT reply: take non-orphaned history up to (and not including) this message and re-call AI.
     * 重新生成 ASSISTANT：取该消息之前的未作废 history 重新调 AI。
     *
     * @param userId user id / 用户 ID
     * @param messageId target ASSISTANT message id / 目标 ASSISTANT 消息 ID
     * @return SSE emitter / SSE 发射器
     */
    SseEmitter regenerate(Long userId, Long messageId);

    /**
     * Edit a USER message's content. Marks orphans (does not auto re-run AI).
     * 编辑 USER 消息内容。标 orphan（不自动重跑 AI）。
     *
     * @param userId user id / 用户 ID
     * @param messageId USER message id / USER 消息 ID
     * @param newContent new content / 新内容
     * @return edited message / 编辑后的消息
     */
    MessageVo edit(Long userId, Long messageId, String newContent);

    /**
     * Insert USER message + touch conversation (short transaction).
     * 插入 USER 消息 + 触摸 conversation（短事务）。
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id / 对话 ID
     * @param content content / 内容
     * @return newly inserted message / 新插入的消息
     */
    Message insertUserMessage(Long userId, Long conversationId, String content);

    /**
     * Insert ASSISTANT message (called when stream completes; short transaction).
     * 插入 ASSISTANT 消息（流式完成时调用，短事务）。
     *
     * @param conversationId conversation id / 对话 ID
     * @param content AI reply content / AI 回复内容
     * @return newly inserted message / 新插入的消息
     */
    Message insertAssistantMessage(Long conversationId, String content);
}