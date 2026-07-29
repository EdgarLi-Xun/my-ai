package cn.edgarli.service.ai;

import cn.edgarli.entity.UserApiKey;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Unified entry point for AI calls (ADR 0005 §4: AI sub-package home).
 * AI 调用统一入口（ADR 0005 §4：AI 子包归宿）。
 * <p>
 * 封装 ChatClient 拼装、prompt 转换、token 提取，对 service 层隐藏 Spring AI 细节。
 * Hides Spring AI details from the service layer by wrapping ChatClient assembly,
 * prompt conversion, and token extraction.
 * <p>
 * 调用方约定：{@link #chat} 走同步路径；{@link #streamChat} 返回 {@link Flux}
 * 用于 SSE 流式。
 * Caller contract: {@link #chat} goes through the synchronous path;
 * {@link #streamChat} returns a {@link Flux} used for SSE streaming.
 *
 * @author MyAi
 */
public interface AiService {

    /**
     * 按用户 Key 构建 {@link ChatClient}（不缓存，每次新建——保证配置变更立即生效）。
     * Build a {@link ChatClient} per user Key (no caching, new instance each time —
     * ensures configuration changes take effect immediately).
     *
     * @param key 用户 API Key配置 / user API Key configuration
     * @return 新建的 ChatClient / the newly built ChatClient
     */
    ChatClient getClient(UserApiKey key);

    /**
     * 同步非流式聊天：拼 prompt → 调用 → 返回纯文本。
     * Synchronous non-streaming chat: build prompt → call → return plain text.
     *
     * @param key 用户 API Key配置 / user API Key configuration
     * @param messages Spring AI 消息数组（已转换为 USER / ASSISTANT / SYSTEM）/ Spring AI message array (already converted to USER / ASSISTANT / SYSTEM)
     * @return AI 回复文本 / AI reply text
     */
    String chat(UserApiKey key, Message[] messages);

    /**
     * 流式聊天：返回 {@link ChatResponse} 流，由调用方订阅并发送 SSE 事件。
     * Streaming chat: returns a {@link ChatResponse} stream; the caller subscribes and emits SSE events.
     * <p>
     * 每条 ChatResponse 含增量 token 文本（{@link #extractTokenText(ChatResponse)}）
     * 与可能的 usage 元数据（{@link org.springframework.ai.chat.metadata.ChatResponse#getMetadata()}）。
     * Each ChatResponse carries an incremental token text (see {@link #extractTokenText(ChatResponse)})
     * and possibly usage metadata (see {@link org.springframework.ai.chat.metadata.ChatResponse#getMetadata()}).
     *
     * @param key 用户 API Key配置 / user API Key configuration
     * @param messages Spring AI 消息数组 / Spring AI message array
     * @return ChatResponse 流 / ChatResponse stream
     */
    Flux<ChatResponse> streamChat(UserApiKey key, Message[] messages);

    /**
     * 将持久化 {@link cn.edgarli.entity.Message} 列表转为 Spring AI {@link Message} 数组。
     * Convert the persisted {@link cn.edgarli.entity.Message} list into a Spring AI {@link Message} array.
     * <p>
     * 角色映射：{@code USER} → {@link org.springframework.ai.chat.messages.UserMessage}，
     * {@code ASSISTANT} → {@link org.springframework.ai.chat.messages.AssistantMessage}，
     * {@code SYSTEM} → {@link org.springframework.ai.chat.messages.SystemMessage}，
     * 其他 / null → {@link org.springframework.ai.chat.messages.UserMessage}。
     * Role mapping: {@code USER} → {@link org.springframework.ai.chat.messages.UserMessage},
     * {@code ASSISTANT} → {@link org.springframework.ai.chat.messages.AssistantMessage},
     * {@code SYSTEM} → {@link org.springframework.ai.chat.messages.SystemMessage},
     * other / null → {@link org.springframework.ai.chat.messages.UserMessage}.
     *
     * @param messages 持久化消息列表 / persisted message list
     * @return Spring AI 消息数组 / Spring AI message array
     */
    Message[] toSpringAiMessages(List<cn.edgarli.entity.Message> messages);

    /**
     * 从单条 {@link ChatResponse} 提取增量 token 文本。
     * Extract the incremental token text from a single {@link ChatResponse}.
     * <p>
     * Spring AI 2.x：{@code chatResponse.getResult().getOutput().getText()}。
     * Spring AI 2.x: {@code chatResponse.getResult().getOutput().getText()}.
     *
     * @param chatResponse 单条流式响应 / a single streaming response
     * @return token 文本；不可用返回 {@code null} / token text; returns {@code null} when unavailable
     */
    String extractTokenText(ChatResponse chatResponse);
}