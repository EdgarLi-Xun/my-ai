package cn.edgarli.service.ai.impl;

import cn.edgarli.entity.Message;
import cn.edgarli.entity.UserApiKey;
import cn.edgarli.service.ai.AiService;
import cn.edgarli.service.ai.ChatClientFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Default implementation of {@link AiService}.
 * {@link AiService} 默认实现。
 * <p>
 * 依赖 {@link ChatClientFactory} 构建客户端；本类负责 prompt 转换与流式订阅。
 * Relies on {@link ChatClientFactory} to build the client; this class handles
 * prompt conversion and stream subscription.
 *
 * @author MyAi
 */
@Service
public class AiServiceImpl implements AiService {

    private final ChatClientFactory chatClientFactory;

    public AiServiceImpl(ChatClientFactory chatClientFactory) {
        this.chatClientFactory = chatClientFactory;
    }

    /**
     * 委托给 {@link ChatClientFactory} 构建 ChatClient。
     * Delegate ChatClient construction to {@link ChatClientFactory}.
     *
     * @param key 用户 API Key配置 / user API Key configuration
     * @return 新建的 ChatClient / the newly built ChatClient
     */
    @Override
    public ChatClient getClient(UserApiKey key) {
        return chatClientFactory.getClient(key);
    }

    /**
     * 同步非流式聊天：拼 prompt → 调用 → 返回纯文本。
     * Synchronous non-streaming chat: build prompt → call → return plain text.
     *
     * @param key 用户 API Key配置 / user API Key configuration
     * @param messages Spring AI 消息数组 / Spring AI message array
     * @return AI 回复文本 / AI reply text
     */
    @Override
    public String chat(UserApiKey key, org.springframework.ai.chat.messages.Message[] messages) {
        ChatClient client = getClient(key); // ChatClient（含 advisor 链）/ ChatClient with advisor chain
        return client.prompt()
                .messages(messages)
                .call()
                .content();
    }

    /**
     * 流式聊天：返回 ChatResponse Flux，由调用方订阅并发送 SSE 事件。
     * Streaming chat: returns a ChatResponse Flux; the caller subscribes and emits SSE events.
     *
     * @param key 用户 API Key配置 / user API Key configuration
     * @param messages Spring AI 消息数组 / Spring AI message array
     * @return ChatResponse 流 / ChatResponse stream
     */
    @Override
    public Flux<ChatResponse> streamChat(UserApiKey key, org.springframework.ai.chat.messages.Message[] messages) {
        ChatClient client = getClient(key); // ChatClient（含 advisor 链）/ ChatClient with advisor chain
        return client.prompt()
                .messages(messages)
                .stream()
                .chatResponse();
    }

    /**
     * 将持久化 {@link Message} 列表转为 Spring AI 消息数组（角色映射见接口 Javadoc）。
     * Convert the persisted {@link Message} list into a Spring AI message array (see interface Javadoc for role mapping).
     *
     * @param messages 持久化消息列表 / persisted message list
     * @return Spring AI 消息数组 / Spring AI message array
     */
    @Override
    public org.springframework.ai.chat.messages.Message[] toSpringAiMessages(List<Message> messages) {
        return messages.stream()
                .map(this::toSpringAiMessage)
                .toArray(org.springframework.ai.chat.messages.Message[]::new);
    }

    /**
     * 单条角色 → Spring AI 消息的映射；未知 / null 角色回落 UserMessage。
     * Map a single role to a Spring AI message; unknown / null roles fall back to UserMessage.
     *
     * @param message 持久化消息 / persisted message
     * @return 对应角色的 Spring AI 消息 / Spring AI message of the corresponding role
     */
    private org.springframework.ai.chat.messages.Message toSpringAiMessage(Message message) {
        String role = message.getRole() == null ? Message.ROLE_USER : message.getRole(); // 角色字符串（兜底 USER）/ role string (fallback USER)
        return switch (role) {
            case Message.ROLE_SYSTEM -> new SystemMessage(message.getContent());
            case Message.ROLE_ASSISTANT -> new AssistantMessage(message.getContent());
            default -> new UserMessage(message.getContent());
        };
    }

    /**
     * 从 Spring AI {@link ChatResponse} 提取增量 token 文本（不可用返回 null）。
     * Extract the incremental token text from a Spring AI {@link ChatResponse} (returns null when unavailable).
     *
     * @param chatResponse 单条流式响应（Spring AI ChatResponse，非 web VO）/ a single streaming response (Spring AI ChatResponse, not the web VO)
     * @return token 文本；不可用返回 {@code null} / token text; returns {@code null} when unavailable
     */
    @Override
    public String extractTokenText(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return null;
        }
        Generation generation = chatResponse.getResult(); // 流式生成结果 / streaming generation result
        if (generation == null) {
            return null;
        }
        AssistantMessage output = generation.getOutput(); // Assistant 输出（含增量文本）/ Assistant output (with incremental text)
        if (output == null) {
            return null;
        }
        return output.getText(); // 增量 token 文本 / incremental token text
    }
}