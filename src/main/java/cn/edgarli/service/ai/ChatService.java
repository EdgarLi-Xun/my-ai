package cn.edgarli.service.ai;

import cn.edgarli.common.BizException;
import cn.edgarli.entity.UserApiKey;
import cn.edgarli.service.UserApiKeyService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Compatibility-layer chat service for the deprecated {@code /api/chat}
 * (response header {@code Deprecation: true}).
 * 废弃 {@code /api/chat} 兼容层的聊天服务（响应头带 {@code Deprecation: true}）。
 * <p>
 * 通过 {@link AiService} 统一入口调用，自身只负责入参校验与消息角色映射。
 * Delegates to the unified {@link AiService} entry point; this class only
 * validates input and maps message roles.
 *
 * @author MyAi
 */
@Service
public class ChatService {

    private final AiService aiService;
    private final UserApiKeyService keyService;

    public ChatService(AiService aiService, UserApiKeyService keyService) {
        this.aiService = aiService;
        this.keyService = keyService;
    }

    /**
     * 兼容层聊天入口：校验 → 取默认 Key → 角色映射 → 同步调 AI。
     * Compatibility-layer chat entry: validate → fetch default Key → map roles → sync call.
     *
     * @param userId 当前用户 id / current user id
     * @param messages 多轮消息列表 / multi-turn message list
     * @return AI 回复文本 / AI reply text
     */
    public String chat(Long userId, List<ChatMessage> messages) {
        validateMessages(messages);
        UserApiKey key = keyService.getDefaultForChat(userId); // 默认 Key（可能抛 BizException 4035）/ default Key (may throw BizException 4035)
        Message[] aiMessages = messages.stream()
                .map(ChatService::toSpringAiMessage)
                .toArray(Message[]::new); // 转换后的 Spring AI 消息数组 / converted Spring AI message array
        return aiService.chat(key, aiMessages);
    }

    /**
     * 校验消息非空且每条 content 非空白；违反抛 BizException（4000）。
     * Validate that messages are non-empty and each content is non-blank; otherwise throw BizException (4000).
     *
     * @param messages 待校验的消息列表 / message list to validate
     */
    private static void validateMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw BizException.badRequest("消息不能为空");
        }
        if (messages.stream().anyMatch(message -> message == null
                || message.content() == null
                || message.content().isBlank())) {
            throw BizException.badRequest("消息内容不能为空");
        }
    }

    /**
     * 单条角色 → Spring AI 消息映射；未知 / null 角色回落 UserMessage。
     * Map a single role to a Spring AI message; unknown / null roles fall back to UserMessage.
     *
     * @param message 兼容层消息 / compatibility-layer message
     * @return 对应角色的 Spring AI 消息 / Spring AI message of the corresponding role
     */
    private static Message toSpringAiMessage(ChatMessage message) {
        String role = message.role() == null ? "user" : message.role().toLowerCase(); // 归一化角色字符串 / normalized role string
        return switch (role) {
            case "system" -> new SystemMessage(message.content());
            case "assistant" -> new AssistantMessage(message.content());
            default -> new UserMessage(message.content());
        };
    }
}