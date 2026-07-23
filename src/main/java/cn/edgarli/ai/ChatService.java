package cn.edgarli.ai;

import cn.edgarli.common.BizException;
import cn.edgarli.entity.UserApiKey;
import cn.edgarli.service.UserApiKeyService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 按用户默认 Key 执行多轮聊天。
 */
@Service
public class ChatService {

    private final ChatClientFactory chatClientFactory;
    private final UserApiKeyService keyService;

    public ChatService(ChatClientFactory chatClientFactory, UserApiKeyService keyService) {
        this.chatClientFactory = chatClientFactory;
        this.keyService = keyService;
    }

    public String chat(Long userId, List<ChatMessage> messages) {
        validateMessages(messages);
        UserApiKey key = keyService.getDefaultForChat(userId);
        ChatClient chatClient = chatClientFactory.getClient(key);
        Message[] aiMessages = messages.stream()
                .map(ChatService::toSpringAiMessage)
                .toArray(Message[]::new);
        return chatClient.prompt()
                .messages(aiMessages)
                .call()
                .content();
    }

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

    private static Message toSpringAiMessage(ChatMessage message) {
        String role = message.role() == null ? "user" : message.role().toLowerCase();
        return switch (role) {
            case "system" -> new SystemMessage(message.content());
            case "assistant" -> new AssistantMessage(message.content());
            default -> new UserMessage(message.content());
        };
    }
}
