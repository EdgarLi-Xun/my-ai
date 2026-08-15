package cn.edgarli.web;

import cn.edgarli.common.Result;
import cn.edgarli.service.MessageCommandService;
import cn.edgarli.service.MessageQueryService;
import cn.edgarli.web.dto.UpdateMessageDto;
import cn.edgarli.web.vo.MessageVo;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 消息 REST API（ADR 0003，需登录）。
 * Message REST API (ADR 0003, login required).
 * <p>
 * SSE 端点（{@code POST /api/conversations/{id}/messages} 与
 * {@code POST /api/messages/{id}/regenerate}）返回 {@link SseEmitter}，
 * {@code Content-Type: text/event-stream}。前端用
 * {@code frontend/src/lib/sse.js} 解析事件流，绕过 {@code api()} 包装。
 * <p>
 * Event protocol: {@code event: token} streams tokens,
 * {@code event: done} signals normal completion,
 * {@code event: error} surfaces server-side failures.
 * Front-end parses via {@code frontend/src/lib/sse.js} and bypasses
 * the standard {@code api()} wrapper.
 * <p>
 * Query 与 Command 分别注入，避免出现"组合 facade 空壳"层（ADR 0005 §3）。
 * Query and command services are injected separately to avoid a pass-through
 * facade layer (ADR 0005 §3).
 */
@RestController
public class MessageController {

    private final MessageQueryService messageQueryService;
    private final MessageCommandService messageCommandService;

    public MessageController(MessageQueryService messageQueryService,
                             MessageCommandService messageCommandService) {
        // 注入查询与命令两个 service / inject query + command services
        this.messageQueryService = messageQueryService;
        this.messageCommandService = messageCommandService;
    }

    /**
     * 列出某对话下的全部消息（按 created_at 升序）。
     * List messages in a conversation (ordered by created_at asc).
     *
     * @param conversationId 对话 ID / conversation id
     * @param includeOrphaned 是否包含被编辑/重生覆盖的旧消息 / include soft-orphaned rows
     * @return 消息列表 / list of messages
     */
    @GetMapping("/api/conversations/{conversationId}/messages")
    public Result<List<MessageVo>> list(
            @PathVariable Long conversationId,
            @RequestParam(name = "include_orphaned", defaultValue = "false") boolean includeOrphaned) {
        Long userId = UserController.currentUserId();
        return Result.success(messageQueryService.list(userId, conversationId, includeOrphaned));
    }

    /**
     * 在指定对话下发送用户消息，并以 SSE 流式返回 AI 回复。
     * Send a user message in a conversation and stream the AI reply via SSE.
     * <p>
     * 事件协议：{@code event: token} 逐 token 推送；{@code event: done} 正常结束；
     * {@code event: error} 服务端失败。默认 Key 不可用会先抛 4035 才会建立连接。
     *
     * @param conversationId 对话 ID / conversation id
     * @param body 请求体，含 {@code content} 字段 / body containing the "content" field
     * @return 用于流式输出的 {@link SseEmitter} / SSE emitter for streaming output
     */
    @PostMapping(value = "/api/conversations/{conversationId}/messages",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(
            @PathVariable Long conversationId,
            @RequestBody Map<String, String> body) {
        Long userId = UserController.currentUserId();
        String content = body == null ? null : body.get("content");
        return messageCommandService.streamReply(userId, conversationId, content);
    }

    /**
     * 修改用户消息内容（旧消息被标 is_orphaned=TRUE）。
     * Edit a user message (the previous row is soft-marked is_orphaned=TRUE).
     *
     * @param id 消息 ID / message id
     * @param request 新内容 / new content payload
     * @return 修改后的消息 / edited message
     */
    @PatchMapping("/api/messages/{id}")
    public Result<MessageVo> edit(
            @PathVariable Long id,
            @RequestBody UpdateMessageDto request) {
        Long userId = UserController.currentUserId();
        return Result.success(messageCommandService.edit(userId, id, request.content()));
    }

    /**
     * 取单条消息（通过所属对话校验 owner）。
     * Fetch a single message (owner validated via owning conversation).
     *
     * @param id 消息 ID / message id
     * @return 消息 / message
     */
    @GetMapping("/api/messages/{id}")
    public Result<MessageVo> get(@PathVariable Long id) {
        Long userId = UserController.currentUserId();
        return Result.success(messageQueryService.getById(userId, id));
    }

    /**
     * 软删单条消息（{@code deleted_at = NOW()}，幂等）。
     * Soft-delete a single message ({@code deleted_at = NOW()}, idempotent).
     *
     * @param id 消息 ID / message id
     * @return 空结果 / empty result
     */
    @DeleteMapping("/api/messages/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = UserController.currentUserId();
        messageCommandService.delete(userId, id);
        return Result.success(null);
    }

    /**
     * 重新生成 AI 回复（流式 SSE）。
     * Regenerate the assistant reply for a given message (SSE).
     * <p>
     * 原回复被标 is_orphaned=TRUE；事件协议同 {@link #send}。
     *
     * @param id 消息 ID / message id
     * @return 用于流式输出的 {@link SseEmitter} / SSE emitter for streaming output
     */
    @PostMapping(value = "/api/messages/{id}/regenerate",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerate(@PathVariable Long id) {
        Long userId = UserController.currentUserId();
        return messageCommandService.regenerate(userId, id);
    }
}