package cn.edgarli.service.impl;

import cn.edgarli.common.BizException;
import cn.edgarli.entity.Conversation;
import cn.edgarli.entity.Message;
import cn.edgarli.entity.UserApiKey;
import cn.edgarli.mapper.ConversationMapper;
import cn.edgarli.mapper.MessageMapper;
import cn.edgarli.service.ai.AiService;
import cn.edgarli.service.AiCallLogService;
import cn.edgarli.service.MessageCommandService;
import cn.edgarli.service.UserApiKeyService;
import cn.edgarli.web.vo.MessageVo;
import com.mybatisflex.core.update.UpdateChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of {@link MessageCommandService} (ADR 0005 §3).
 * {@link MessageCommandService} 默认实现（ADR 0005 §3）。
 * <p>
 * The core SSE path {@link #streamReply} is split across 4 transaction boundaries:
 * 核心 SSE 路径 {@link #streamReply} 拆 4 个事务边界：
 * <ol>
 *   <li>{@link #insertUserMessage} (short transaction) / （短事务）</li>
 *   <li>AI call (no transaction; long-running / streaming) / AI 调用（无事务，长连接 / 流式）</li>
 *   <li>{@link #insertAssistantMessage} (short transaction) / （短事务）</li>
 *   <li>Side effect: {@code touchConversation} (short transaction) / 副作用：{@code touchConversation}（短事务）</li>
 * </ol>
 *
 * @author MyAi
 */
@Service
public class MessageCommandServiceImpl implements MessageCommandService {

    private static final Logger log = LoggerFactory.getLogger(MessageCommandServiceImpl.class);

    /** SSE 超时：5 分钟。 */
    private static final long SSE_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);

    /** 自动标题最大长度。 */
    private static final int AUTO_TITLE_MAX = 30;

    /** AI 标题生成 prompt。 */
    private static final String AUTO_TITLE_PROMPT =
            "为下列对话生成一个简洁的中文标题，长度不超过 30 个字符（不包含标点符号、引号或前后缀）。"
                    + "只返回标题文本本身，不要任何解释。\n\n用户：%s";

    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final MessageSupport support;
    private final UserApiKeyService keyService;
    private final AiService aiService;
    private final AiCallLogService aiCallLogService;

    public MessageCommandServiceImpl(MessageMapper messageMapper,
                                     ConversationMapper conversationMapper,
                                     MessageSupport support,
                                     UserApiKeyService keyService,
                                     AiService aiService,
                                     AiCallLogService aiCallLogService) {
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
        this.support = support;
        this.keyService = keyService;
        this.aiService = aiService;
        this.aiCallLogService = aiCallLogService;
    }

    // ============ 编辑 ============

    /**
     * Edit a USER message's content and mark downstream messages as orphans.
     * 编辑 USER 消息内容，并把下游消息标 orphan。
     * <p>
     * Transaction boundary: full edit flow (owner check + content update + orphan marking + touch conversation) is one transaction.
     * 事务边界：owner 校验、改内容、标 orphan、touch conversation 全部在同一事务内。
     *
     * @param userId user id / 用户 ID
     * @param messageId USER message id / USER 消息 ID
     * @param newContent new content / 新内容
     * @return edited message / 编辑后的消息
     * @throws BizException 4000 blank; 4032 message not found; 4033 not USER
     *                      4000 内容为空；4032 消息不存在；4033 非 USER 消息
     */
    @Override
    @Transactional
    public MessageVo edit(Long userId, Long messageId, String newContent) {
        String content = MessageSupport.requireNonBlank(newContent, "消息内容不能为空");
        // 校验 + trim 后的新内容 / validated and trimmed new content
        Message message = support.requireOwnedMessage(userId, messageId);
        // 校验消息属于该用户 / validate message belongs to user
        if (!Message.ROLE_USER.equals(message.getRole())) {
            throw BizException.messageNotUser("只能编辑 USER 消息");
        }
        support.requireOwnedConversation(userId, message.getConversationId());
        message.setContent(content);
        message.setIsOrphaned(false);
        // 编辑后置为非孤儿 / reset orphan flag after edit
        messageMapper.update(message);
        messageMapper.markOrphansAfter(message.getConversationId(), message.getCreatedAt());
        // 把同对话后续消息全部标 orphan（含自身） / mark downstream messages as orphans (incl. self)
        conversationMapper.touchUpdatedAt(message.getConversationId());
        Message fresh = messageMapper.selectOneById(messageId);
        // 重读拿最新行 / re-read for latest row
        // 响应给前端时强制 isOrphaned=false：用户刚编辑的内容不应被前端视为孤儿
        // Override isOrphaned=false in response: front-end should not treat just-edited content as orphan
        if (fresh != null) {
            fresh.setIsOrphaned(false);
        }
        return support.toResponse(fresh);
    }

    // ============ 流式回复 ============

    /**
     * Stream back an AI reply via SSE. The emitter is returned immediately; the caller subscribes via the SSE protocol.
     * 通过 SSE 流式返回 AI 回复。emitter 立即返回，调用方按 SSE 协议订阅。
     * <p>
     * Flow:
     * <ol>
     *   <li>Insert USER message (short tx) / 插入 USER 消息（短事务）</li>
     *   <li>Load context + default key (no tx) / 加载上下文 + 默认 Key（无事务）</li>
     *   <li>Stream from AI provider; each token pushed to client; usage accumulated / 从 AI 厂家流式读取，每 token 推送给客户端，usage 累加</li>
     *   <li>On complete: insert ASSISTANT message (short tx) + record ai_call_log success / 完成时：插入 ASSISTANT 消息（短事务）+ 写 ai_call_log 成功行</li>
     *   <li>On error: record ai_call_log failure + push error event / 出错时：写 ai_call_log 失败行 + 推 error 事件</li>
     *   <li>Side effect: if first USER message, kick off async AI title generation / 副作用：若首条 USER 消息，异步触发 AI 标题生成</li>
     * </ol>
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id / 对话 ID
     * @param content user message content / 用户消息内容
     * @return SSE emitter / SSE 发射器
     */
    @Override
    public SseEmitter streamReply(Long userId, Long conversationId, String content) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // SSE 发射器，5 分钟超时 / SSE emitter with 5-min timeout
        String validatedContent = MessageSupport.requireNonBlank(content, "消息内容不能为空");
        // 校验 + trim 后的用户内容 / validated and trimmed user content

        Message userMsg;
        // 刚插入的 USER 消息 / just-inserted USER message
        java.util.List<Message> context;
        // 非孤儿消息上下文 / non-orphaned message context
        UserApiKey key;
        // 默认 Key / default key
        try {
            userMsg = insertUserMessage(userId, conversationId, validatedContent);
            context = messageMapper.findNonOrphanedContext(conversationId);
            // 加载非孤儿消息作为 AI 输入 / load non-orphaned messages as AI input
            key = keyService.getDefaultForChat(userId);
            // 取聊天用的默认 Key（会自动校验配置有效） / fetch default key for chat (validates config)
        } catch (BizException ex) {
            sendErrorAndComplete(emitter, ex.getCode(), ex.getMessage());
            return emitter;
        } catch (Exception ex) {
            log.error("Failed to prepare stream reply for user={}, conversation={}", userId, conversationId, ex);
            sendErrorAndComplete(emitter, 5000, ex.getMessage());
            return emitter;
        }

        boolean isFirstUserMessage = context.size() == 1
                && Message.ROLE_USER.equals(context.get(0).getRole());
        // 是否首条 USER 消息（决定是否触发 AI 自动标题） / whether this is the first USER message (triggers AI auto-title)

        StringBuilder buf = new StringBuilder();
        // 累积完整 AI 回复 / accumulate full AI reply
        AtomicLong callStart = new AtomicLong(System.currentTimeMillis());
        // 调用开始时间戳 / call-start timestamp
        AtomicReference<Integer> inputTokensRef = new AtomicReference<>();
        // 累计输入 tokens（异步读） / accumulated input tokens (read async)
        AtomicReference<Integer> outputTokensRef = new AtomicReference<>();
        // 累计输出 tokens（异步读） / accumulated output tokens (read async)
        org.springframework.ai.chat.messages.Message[] springMessages = aiService.toSpringAiMessages(context);
        // 转为 Spring AI 消息数组 / convert to Spring AI message array
        Flux<ChatResponse> flux = aiService.streamChat(key, springMessages);
        // 发起流式 AI 调用 / start streaming AI call

        Disposable subscription = flux.subscribe(
                chatResponse -> {
                    String token = aiService.extractTokenText(chatResponse);
                    // 当前 chunk 的文本 / current chunk text
                    if (token != null && !token.isEmpty()) {
                        buf.append(token);
                        sendEvent(emitter, "token", Map.of("text", token));
                    }
                    Usage usage = chatResponse.getMetadata() == null ? null : chatResponse.getMetadata().getUsage();
                    // 元数据中的 usage（部分 provider 可能为 null） / usage in metadata (may be null for some providers)
                    if (usage != null) {
                        if (usage.getPromptTokens() != null) {
                            inputTokensRef.set(usage.getPromptTokens());
                        }
                        if (usage.getCompletionTokens() != null) {
                            outputTokensRef.set(usage.getCompletionTokens());
                        }
                    }
                },
                err -> {
                    long latency = System.currentTimeMillis() - callStart.get();
                    // 错误时延 / error latency
                    log.warn("AI stream error for user={}, conversation={}", userId, conversationId, err);
                    try {
                        aiCallLogService.recordFailure(userId, conversationId, null,
                                key.getProvider(), key.getModelName(), latency,
                                err.getMessage() == null ? "AI 调用失败" : err.getMessage());
                        // 写失败 ai_call_log 行 / record ai_call_log failure row
                    } catch (Exception logEx) {
                        log.warn("Failed to record ai_call_log (failure)", logEx);
                    }
                    sendEvent(emitter, "error", Map.of(
                            "code", 5000,
                            "message", err.getMessage() == null ? "AI 调用失败" : err.getMessage()));
                    safeComplete(emitter);
                    // 正常关闭 emitter：避免异常冒泡到 Spring MVC 全局异常处理器
                    // （CLAUDE.md §4.12：SSE 端点错误应通过 event: error 事件传递，而不是触发 GlobalExceptionHandler）
                },
                () -> {
                    long latency = System.currentTimeMillis() - callStart.get();
                    // 完成时延 / completion latency
                    Message assistant = null;
                    // 新插入的 ASSISTANT 消息 / newly inserted ASSISTANT message
                    try {
                        assistant = insertAssistantMessage(conversationId, buf.toString());
                        aiCallLogService.recordSuccess(userId, conversationId, assistant.getId(),
                                key.getProvider(), key.getModelName(), latency,
                                inputTokensRef.get(), outputTokensRef.get());
                        // 写成功 ai_call_log 行 / record ai_call_log success row
                        sendEvent(emitter, "done", Map.of(
                                "messageId", assistant.getId(),
                                "conversationId", conversationId));
                    } catch (Exception ex) {
                        log.error("Failed to persist assistant message", ex);
                        sendEvent(emitter, "error", Map.of(
                                "code", 5000,
                                "message", "回复保存失败"));
                    } finally {
                        if (isFirstUserMessage && assistant != null) {
                            // 首条 USER 消息完成 → 异步生成标题 / first USER message done → async title generation
                            final String userContent = validatedContent;
                            CompletableFuture.runAsync(
                                    () -> maybeAutoTitle(userId, conversationId, userContent));
                        }
                        safeComplete(emitter);
                    }
                });

        emitter.onCompletion(() -> {
            try {
                subscription.dispose();
                // 客户端断开 → 释放 flux / client disconnected → release flux
            } catch (Exception ignored) {
            }
        });
        emitter.onTimeout(() -> {
            log.info("SSE timeout for user={}, conversation={}", userId, conversationId);
            try {
                subscription.dispose();
            } catch (Exception ignored) {
            }
            safeComplete(emitter);
        });
        emitter.onError(t -> {
            try {
                subscription.dispose();
            } catch (Exception ignored) {
            }
        });
        return emitter;
    }

    /**
     * Regenerate an ASSISTANT reply. Same SSE pattern as {@link #streamReply},
     * 但额外把目标消息标 orphan。
     * <p>
     * Flow: validate target → load history before target → stream → mark target orphan → insert new ASSISTANT.
     * 流程：校验目标 → 加载目标前 history → 流式调用 → 标目标 orphan → 插入新 ASSISTANT。
     *
     * @param userId user id / 用户 ID
     * @param messageId target ASSISTANT message id / 目标 ASSISTANT 消息 ID
     * @return SSE emitter / SSE 发射器
     */
    @Override
    public SseEmitter regenerate(Long userId, Long messageId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // SSE 发射器，5 分钟超时 / SSE emitter with 5-min timeout

        Message target;
        // 目标 ASSISTANT 消息 / target ASSISTANT message
        java.util.List<Message> context;
        // 目标之前的非孤儿 history / non-orphaned history before target
        UserApiKey key;
        // 默认 Key / default key
        try {
            target = support.requireOwnedMessage(userId, messageId);
            if (!Message.ROLE_ASSISTANT.equals(target.getRole())) {
                throw BizException.messageNotAssistant("只能重新生成 ASSISTANT 消息");
            }
            support.requireOwnedConversation(userId, target.getConversationId());
            context = messageMapper.findNonOrphanedContextBefore(target.getConversationId(), target.getId());
            // 取目标之前（不含）的 history / load history strictly before target
            key = keyService.getDefaultForChat(userId);
        } catch (BizException ex) {
            sendErrorAndComplete(emitter, ex.getCode(), ex.getMessage());
            return emitter;
        } catch (Exception ex) {
            log.error("Failed to prepare regenerate for user={}, message={}", userId, messageId, ex);
            sendErrorAndComplete(emitter, 5000, ex.getMessage());
            return emitter;
        }

        Long conversationId = target.getConversationId();
        // 闭包用 conversationId / conversationId for closure
        StringBuilder buf = new StringBuilder();
        // 累积完整 AI 回复 / accumulate full AI reply
        AtomicLong callStart = new AtomicLong(System.currentTimeMillis());
        // 调用开始时间戳 / call-start timestamp
        AtomicReference<Integer> inputTokensRef = new AtomicReference<>();
        // 累计输入 tokens / accumulated input tokens
        AtomicReference<Integer> outputTokensRef = new AtomicReference<>();
        // 累计输出 tokens / accumulated output tokens
        org.springframework.ai.chat.messages.Message[] springMessages = aiService.toSpringAiMessages(context);
        Flux<ChatResponse> flux = aiService.streamChat(key, springMessages);

        Disposable subscription = flux.subscribe(
                chatResponse -> {
                    String token = aiService.extractTokenText(chatResponse);
                    if (token != null && !token.isEmpty()) {
                        buf.append(token);
                        sendEvent(emitter, "token", Map.of("text", token));
                    }
                    Usage usage = chatResponse.getMetadata() == null ? null : chatResponse.getMetadata().getUsage();
                    if (usage != null) {
                        if (usage.getPromptTokens() != null) {
                            inputTokensRef.set(usage.getPromptTokens());
                        }
                        if (usage.getCompletionTokens() != null) {
                            outputTokensRef.set(usage.getCompletionTokens());
                        }
                    }
                },
                err -> {
                    long latency = System.currentTimeMillis() - callStart.get();
                    log.warn("AI stream error during regenerate for user={}, message={}", userId, messageId, err);
                    try {
                        aiCallLogService.recordFailure(userId, conversationId, target.getId(),
                                key.getProvider(), key.getModelName(), latency,
                                err.getMessage() == null ? "AI 调用失败" : err.getMessage());
                        // 写失败 ai_call_log（含 messageId） / record ai_call_log failure (with messageId)
                    } catch (Exception logEx) {
                        log.warn("Failed to record ai_call_log (regenerate failure)", logEx);
                    }
                    sendEvent(emitter, "error", Map.of(
                            "code", 5000,
                            "message", err.getMessage() == null ? "AI 调用失败" : err.getMessage()));
                    safeComplete(emitter);
                    // 正常关闭 emitter：避免异常冒泡到 Spring MVC 全局异常处理器
                    // （CLAUDE.md §4.12：SSE 端点错误应通过 event: error 事件传递，而不是触发 GlobalExceptionHandler）
                },
                () -> {
                    long latency = System.currentTimeMillis() - callStart.get();
                    try {
                        messageMapper.markOrphan(target.getId());
                        // 标原 ASSISTANT 为 orphan / mark original ASSISTANT as orphan
                        Message assistant = insertAssistantMessage(conversationId, buf.toString());
                        aiCallLogService.recordSuccess(userId, conversationId, assistant.getId(),
                                key.getProvider(), key.getModelName(), latency,
                                inputTokensRef.get(), outputTokensRef.get());
                        // 写成功 ai_call_log / record ai_call_log success
                        sendEvent(emitter, "done", Map.of(
                                "messageId", assistant.getId(),
                                "conversationId", conversationId));
                    } catch (Exception ex) {
                        log.error("Failed to persist regenerated assistant message", ex);
                        sendEvent(emitter, "error", Map.of(
                                "code", 5000,
                                "message", "回复保存失败"));
                    } finally {
                        safeComplete(emitter);
                    }
                });

        emitter.onCompletion(() -> {
            try {
                subscription.dispose();
            } catch (Exception ignored) {
            }
        });
        emitter.onTimeout(() -> {
            try {
                subscription.dispose();
            } catch (Exception ignored) {
            }
            safeComplete(emitter);
        });
        emitter.onError(t -> {
            try {
                subscription.dispose();
            } catch (Exception ignored) {
            }
        });
        return emitter;
    }

    // ============ 内部短事务方法 ============

    /**
     * Insert USER message + touch conversation (short transaction).
     * 插入 USER 消息 + 触摸 conversation（短事务）。
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id / 对话 ID
     * @param content content / 内容
     * @return newly inserted message / 新插入的消息
     */
    @Override
    @Transactional
    public Message insertUserMessage(Long userId, Long conversationId, String content) {
        support.requireOwnedConversation(userId, conversationId);
        // owner 校验 / owner check
        Message msg = new Message();
        // 新 USER 消息 / new USER message
        msg.setConversationId(conversationId);
        msg.setRole(Message.ROLE_USER);
        msg.setContent(content);
        msg.setIsOrphaned(false);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
        conversationMapper.touchUpdatedAt(conversationId);
        // 触摸 conversation.updated_at / touch conversation.updated_at
        return msg;
    }

    /**
     * Insert ASSISTANT message (called on stream completion; short transaction).
     * 插入 ASSISTANT 消息（流式完成时调用，短事务）。
     *
     * @param conversationId conversation id / 对话 ID
     * @param content AI reply content / AI 回复内容
     * @return newly inserted message / 新插入的消息
     */
    @Override
    @Transactional
    public Message insertAssistantMessage(Long conversationId, String content) {
        Message msg = new Message();
        // 新 ASSISTANT 消息 / new ASSISTANT message
        msg.setConversationId(conversationId);
        msg.setRole(Message.ROLE_ASSISTANT);
        msg.setContent(content == null ? "" : content);
        // null content fallback to empty string / null content 兜底为空串
        msg.setIsOrphaned(false);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
        conversationMapper.touchUpdatedAt(conversationId);
        return msg;
    }

    /**
     * v2 自动标题：用 AI 摘要首条 USER 消息生成 ≤30 字标题。
     * <p>
     * 失败回退截断。在异步线程执行。
     * <p>
     * Conditions to skip: blank content; conversation not found or title already manually set; not owner.
     * 跳过条件：内容为空 / 对话不存在 / 标题已手动设置 / 非 owner。
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id / 对话 ID
     * @param firstUserContent first USER message content / 首条 USER 消息内容
     */
    private void maybeAutoTitle(Long userId, Long conversationId, String firstUserContent) {
        if (firstUserContent == null || firstUserContent.isBlank()) {
            return;
        }
        Conversation conv = conversationMapper.selectOneById(conversationId);
        // 重新读 conversation 拿最新 title_manually_set / re-read for latest title_manually_set
        if (conv == null || Boolean.TRUE.equals(conv.getTitleManuallySet())) {
            // 标题已被用户手动改过，跳过自动生成 / user manually set title → skip auto
            return;
        }
        if (!Objects.equals(conv.getUserId(), userId)) {
            // 防御：异步线程再次校验 owner / defense-in-depth: re-check ownership in async thread
            log.warn("Auto-title aborted: conversation {} not owned by user {}", conversationId, userId);
            return;
        }

        String title = generateAiTitle(userId, firstUserContent);
        if (title == null || title.isBlank()) {
            // AI 生成失败 → 截断首条 USER 消息作兜底 / AI failed → truncate first USER content as fallback
            title = MessageSupport.truncate(firstUserContent, AUTO_TITLE_MAX);
        }
        if (title.isEmpty()) {
            return;
        }

        UpdateChain.of(conversationMapper)
                .set(Conversation::getTitle, title)
                .where(Conversation::getId).eq(conversationId)
                .update();
        // 直接 UPDATE 标题（不在事务里，跑在异步线程） / direct UPDATE (no transaction; runs on async thread)
        log.debug("Auto-title for conversation {}: '{}'", conversationId, title);
    }

    private String generateAiTitle(Long userId, String firstUserContent) {
        try {
            UserApiKey key = keyService.getDefaultForChat(userId);
            // 再取一次默认 Key（异步线程，无 caller 上下文） / re-fetch default key (async thread, no caller ctx)
            String prompt = String.format(AUTO_TITLE_PROMPT, firstUserContent);
            String generated = aiService.chat(key, new org.springframework.ai.chat.messages.Message[]{
                    new org.springframework.ai.chat.messages.UserMessage(prompt)
            });
            if (generated == null) {
                return null;
            }
            String cleaned = generated.trim().replaceAll("[\"''`「」『』《》]", "");
            // 去前后空白 + 去除常见前后缀符号 / strip whitespace and common quote/bookend punctuation
            if (cleaned.isEmpty()) {
                return null;
            }
            return MessageSupport.truncate(cleaned, AUTO_TITLE_MAX);
        } catch (Exception ex) {
            log.warn("AI title generation failed for user={}: {}", userId, ex.toString());
            return null;
        }
    }

    // ============ SSE helpers ============

    private static void sendEvent(SseEmitter emitter, String name, Map<String, ?> data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException ex) {
            // 客户端已断开 / emitter 已关闭 → 静默忽略 / client disconnected / emitter closed → silently ignore
            if (log.isDebugEnabled()) {
                log.debug("Failed to send SSE event {}: {}", name, ex.getMessage());
            }
        }
    }

    private static void sendErrorAndComplete(SseEmitter emitter, int code, String message) {
        sendEvent(emitter, "error", Map.of(
                "code", code,
                "message", message == null ? "" : message));
        safeComplete(emitter);
    }

    private static void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
            // 可能已被 SSE 容器关闭过，吞 IllegalStateException / may already be closed; swallow IllegalStateException
        } catch (Exception ignored) {
        }
    }
}