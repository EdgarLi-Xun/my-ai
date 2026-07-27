package cn.edgarli.web;

import cn.edgarli.common.Result;
import cn.edgarli.service.MessageService;
import cn.edgarli.web.dto.MessageResponse;
import cn.edgarli.web.dto.UpdateMessageRequest;
import org.springframework.http.MediaType;
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
 * <p>
 * SSE 端点（{@code POST /api/conversations/{id}/messages} 与
 * {@code POST /api/messages/{id}/regenerate}）返回 {@link SseEmitter}，
 * {@code Content-Type: text/event-stream}。前端用
 * {@code frontend/src/lib/sse.js} 解析事件流，绕过 {@code api()} 包装。
 */
@RestController
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/api/conversations/{conversationId}/messages")
    public Result<List<MessageResponse>> list(
            @PathVariable Long conversationId,
            @RequestParam(name = "include_orphaned", defaultValue = "false") boolean includeOrphaned) {
        Long userId = UserController.currentUserId();
        return Result.success(messageService.list(userId, conversationId, includeOrphaned));
    }

    @PostMapping(value = "/api/conversations/{conversationId}/messages",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(
            @PathVariable Long conversationId,
            @RequestBody Map<String, String> body) {
        Long userId = UserController.currentUserId();
        String content = body == null ? null : body.get("content");
        return messageService.streamReply(userId, conversationId, content);
    }

    @PatchMapping("/api/messages/{id}")
    public Result<MessageResponse> edit(
            @PathVariable Long id,
            @RequestBody UpdateMessageRequest request) {
        Long userId = UserController.currentUserId();
        return Result.success(messageService.edit(userId, id, request.content()));
    }

    @PostMapping(value = "/api/messages/{id}/regenerate",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerate(@PathVariable Long id) {
        Long userId = UserController.currentUserId();
        return messageService.regenerate(userId, id);
    }
}