package cn.edgarli.web;

import cn.edgarli.ai.ChatService;
import cn.edgarli.common.BizException;
import cn.edgarli.common.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <strong>已废弃（deprecated）</strong>：自 ADR 0003 落地起，无状态聊天端点
 * {@code POST /api/chat} 已被 {@code POST /api/conversations/{id}/messages}
 * （流式 SSE）替换。本控制器保留是为兼容老 curl 脚本与外部调用，响应头带
 * {@code Deprecation: true} 提醒调用方迁移。下一轮会删除。
 *
 * <p>行为变化：
 * <ul>
 *   <li>调用 {@link ChatService#chat}（无状态、不落库）</li>
 *   <li>默认 Key 不可用 → 抛 {@link BizException#defaultKeyUnavailable} (4035)</li>
 *   <li>仍要求 userId 与登录主体一致 → 抛 4030</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<Result<ChatResponse>> chat(@RequestBody ChatRequest request) {
        if (request == null || request.userId() == null) {
            throw BizException.badRequest("请求体不能为空");
        }
        Long principalUserId = UserController.currentUserId();
        if (!principalUserId.equals(request.userId())) {
            throw BizException.forbidden("只能使用自己的 Key 聊天");
        }
        String reply = chatService.chat(request.userId(), request.messages());
        return ResponseEntity.ok()
                .header("Deprecation", "true")
                .header("Warning", "299 - \"POST /api/chat is deprecated; use POST /api/conversations/{id}/messages instead\"")
                .body(Result.success(new ChatResponse(reply)));
    }
}