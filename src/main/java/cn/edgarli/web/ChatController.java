package cn.edgarli.web;

import cn.edgarli.ai.ChatService;
import cn.edgarli.common.BizException;
import cn.edgarli.common.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 聊天 REST API。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request == null) {
            throw BizException.badRequest("请求体不能为空");
        }
        String reply = chatService.chat(request.userId(), request.messages());
        return Result.success(new ChatResponse(reply));
    }
}
