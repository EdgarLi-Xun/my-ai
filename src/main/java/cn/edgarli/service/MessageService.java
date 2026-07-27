package cn.edgarli.service;

import cn.edgarli.ai.ChatClientFactory;
import cn.edgarli.common.BizException;
import cn.edgarli.entity.Conversation;
import cn.edgarli.entity.Message;
import cn.edgarli.entity.User;
import cn.edgarli.entity.UserApiKey;
import cn.edgarli.mapper.ConversationMapper;
import cn.edgarli.mapper.MessageMapper;
import cn.edgarli.mapper.UserMapper;
import cn.edgarli.web.dto.MessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 消息收发服务（ADR 0003）。
 * <p>
 * 核心 SSE 路径 {@link #streamReply} 拆 4 个事务边界：
 * <ol>
 *   <li>{@link #insertUserMessage}（短事务）</li>
 *   <li>AI 调用（无事务，长连接 / 流式）</li>
 *   <li>{@link #insertAssistantMessage}（短事务）</li>
 *   <li>副作用：{@code touchConversation}（短事务）</li>
 * </ol>
 * SSE 端点不发 {@code Result} 外壳——前端用 {@code frontend/src/lib/sse.js} 解析事件流，
 * 绕过 {@code api()} 包装。
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    /** SSE 超时：5 分钟。长回复 + 慢模型兜底。 */
    private static final long SSE_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);

    /** 自动标题最大长度。超过截断 + 省略号。 */
    private static final int AUTO_TITLE_MAX = 30;

    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final UserMapper userMapper;
    private final UserApiKeyService keyService;
    private final ChatClientFactory chatClientFactory;

    public MessageService(MessageMapper messageMapper,
                          ConversationMapper conversationMapper,
                          UserMapper userMapper,
                          UserApiKeyService keyService,
                          ChatClientFactory chatClientFactory) {
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
        this.userMapper = userMapper;
        this.keyService = keyService;
        this.chatClientFactory = chatClientFactory;
    }

    // ============ 列表 / 编辑 ============

    public List<MessageResponse> list(Long userId, Long conversationId, boolean includeOrphaned) {
        requireOwnedConversation(userId, conversationId);
        return messageMapper.findByConversationId(conversationId, includeOrphaned).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MessageResponse edit(Long userId, Long messageId, String newContent) {
        String content = requireNonBlank(newContent, "消息内容不能为空");
        Message message = requireOwnedMessage(userId, messageId);
        if (!Message.ROLE_USER.equals(message.getRole())) {
            throw BizException.messageNotUser("只能编辑 USER 消息");
        }
        requireOwnedConversation(userId, message.getConversationId());
        // UPDATE content
        message.setContent(content);
        message.setIsOrphaned(false);
        messageMapper.update(message);
        // markOrphansAfter: 把 created_at >= 当前消息 created_at 的全部标 orphan
        // （包括自己，因为新内容已经替换）
        messageMapper.markOrphansAfter(message.getConversationId(), message.getCreatedAt());
        conversationMapper.touchUpdatedAt(message.getConversationId());
        // 重新从 DB 读：markOrphansAfter 把本行也标 orphan 了，内存对象的 isOrphaned 跟 DB 不一致
        Message fresh = messageMapper.selectOneById(messageId);
        return toResponse(fresh);
    }

    // ============ 流式回复 ============

    /**
     * 主路径：用户发新消息 → 流式返回 AI 回复。
     * <p>
     * 返回 {@link SseEmitter}，客户端读取 {@code text/event-stream} 帧：
     * <ul>
     *   <li>{@code event: token} / {@code data: {"text": "..."}}</li>
     *   <li>{@code event: done} / {@code data: {"messageId": ..., "conversationId": ...}}</li>
     *   <li>{@code event: error} / {@code data: {"code": ..., "message": "..."}}</li>
     * </ul>
     */
    public SseEmitter streamReply(Long userId, Long conversationId, String content) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String validatedContent = requireNonBlank(content, "消息内容不能为空");

        // 阶段 1：准备（短事务 + 抛 BizException 风险，e.g. 4035 默认 Key 不可用）
        Message userMsg;
        List<Message> context;
        UserApiKey key;
        ChatClient client;
        try {
            userMsg = insertUserMessage(userId, conversationId, validatedContent);
            context = messageMapper.findNonOrphanedContext(conversationId);
            key = keyService.getDefaultForChat(userId);  // 可能抛 4035
            client = chatClientFactory.getClient(key);
        } catch (BizException ex) {
            sendErrorAndComplete(emitter, ex.getCode(), ex.getMessage());
            return emitter;
        } catch (Exception ex) {
            log.error("Failed to prepare stream reply for user={}, conversation={}", userId, conversationId, ex);
            sendErrorAndComplete(emitter, 5000, ex.getMessage());
            return emitter;
        }

        // 阶段 2：AI 流式（无事务）
        StringBuilder buf = new StringBuilder();
        Flux<String> flux = client.prompt()
                .messages(toSpringAiMessages(context))
                .stream()
                .content();

        Disposable subscription = flux.subscribe(
                token -> {
                    buf.append(token);
                    sendEvent(emitter, "token", Map.of("text", token));
                },
                err -> {
                    log.warn("AI stream error for user={}, conversation={}", userId, conversationId, err);
                    sendEvent(emitter, "error", Map.of(
                            "code", 5000,
                            "message", err.getMessage() == null ? "AI 调用失败" : err.getMessage()));
                    emitter.completeWithError(err);
                },
                () -> {
                    // 阶段 3：落库（短事务）
                    try {
                        Message assistant = insertAssistantMessage(conversationId, buf.toString());
                        sendEvent(emitter, "done", Map.of(
                                "messageId", assistant.getId(),
                                "conversationId", conversationId));
                    } catch (Exception ex) {
                        log.error("Failed to persist assistant message", ex);
                        sendEvent(emitter, "error", Map.of(
                                "code", 5000,
                                "message", "回复保存失败"));
                    } finally {
                        safeComplete(emitter);
                    }
                });

        // 阶段 4：清理回调
        emitter.onCompletion(() -> {
            try {
                subscription.dispose();
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
     * 重新生成 ASSISTANT：取该消息之前的未作废 history 重新调 AI。
     * <p>
     * 成功后：把目标 ASSISTANT 标 orphan，新回复作为新行插入。
     */
    public SseEmitter regenerate(Long userId, Long messageId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        Message target;
        List<Message> context;
        UserApiKey key;
        ChatClient client;
        try {
            target = requireOwnedMessage(userId, messageId);
            if (!Message.ROLE_ASSISTANT.equals(target.getRole())) {
                throw BizException.messageNotAssistant("只能重新生成 ASSISTANT 消息");
            }
            requireOwnedConversation(userId, target.getConversationId());
            context = messageMapper.findNonOrphanedContextBefore(target.getConversationId(), target.getId());
            key = keyService.getDefaultForChat(userId);
            client = chatClientFactory.getClient(key);
        } catch (BizException ex) {
            sendErrorAndComplete(emitter, ex.getCode(), ex.getMessage());
            return emitter;
        } catch (Exception ex) {
            log.error("Failed to prepare regenerate for user={}, message={}", userId, messageId, ex);
            sendErrorAndComplete(emitter, 5000, ex.getMessage());
            return emitter;
        }

        Long conversationId = target.getConversationId();
        StringBuilder buf = new StringBuilder();
        Flux<String> flux = client.prompt()
                .messages(toSpringAiMessages(context))
                .stream()
                .content();

        Disposable subscription = flux.subscribe(
                token -> {
                    buf.append(token);
                    sendEvent(emitter, "token", Map.of("text", token));
                },
                err -> {
                    log.warn("AI stream error during regenerate for user={}, message={}", userId, messageId, err);
                    sendEvent(emitter, "error", Map.of(
                            "code", 5000,
                            "message", err.getMessage() == null ? "AI 调用失败" : err.getMessage()));
                    emitter.completeWithError(err);
                },
                () -> {
                    try {
                        // 把旧 ASSISTANT 标 orphan，插新行
                        messageMapper.markOrphan(target.getId());
                        Message assistant = insertAssistantMessage(conversationId, buf.toString());
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

    // ============ 内部方法（短事务） ============

    /**
     * 插入 USER 消息 + 触发自动标题 + 触摸 conversation。
     * 该方法包在事务里，写入完成后立即释放连接。
     */
    @Transactional
    public Message insertUserMessage(Long userId, Long conversationId, String content) {
        requireOwnedConversation(userId, conversationId);
        // 仅当本对话尚未有任何非作废 USER 消息时，才触发自动标题（避免后续 USER 覆盖首条建立的标题）
        boolean isFirstUserMessage = !messageMapper.existsNonOrphanedUserMessage(conversationId);
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole(Message.ROLE_USER);
        msg.setContent(content);
        msg.setIsOrphaned(false);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
        conversationMapper.touchUpdatedAt(conversationId);
        if (isFirstUserMessage) {
            maybeAutoTitle(conversationId, content);
        }
        return msg;
    }

    /**
     * 插入 ASSISTANT 消息（流式完成时调用）。
     */
    @Transactional
    public Message insertAssistantMessage(Long conversationId, String content) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole(Message.ROLE_ASSISTANT);
        msg.setContent(content == null ? "" : content);
        msg.setIsOrphaned(false);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
        conversationMapper.touchUpdatedAt(conversationId);
        return msg;
    }

    /**
     * 当对话标题还没被用户手动改过时，用首条 USER 消息的内容截断覆盖标题。
     */
    private void maybeAutoTitle(Long conversationId, String firstUserContent) {
        Conversation conv = conversationMapper.selectOneById(conversationId);
        if (conv == null || Boolean.TRUE.equals(conv.getTitleManuallySet())) {
            return;
        }
        String title = truncate(firstUserContent, AUTO_TITLE_MAX);
        if (title.isEmpty()) {
            return;
        }
        // 注意：updateTitle 会把 title_manually_set 设为 TRUE。这里需要特例：
        // 用 UpdateChain 直接更新 title 而不动 manually_set flag。
        com.mybatisflex.core.update.UpdateChain.of(conversationMapper)
                .set(Conversation::getTitle, title)
                .where(Conversation::getId).eq(conversationId)
                .update();
    }

    // ============ helpers ============

    private User requireUser(Long userId) {
        if (userId == null) {
            throw BizException.badRequest("userId 不能为空");
        }
        User user = userMapper.findById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    private Conversation requireOwnedConversation(Long userId, Long conversationId) {
        if (conversationId == null) {
            throw BizException.conversationNotFound("对话不存在");
        }
        Conversation conversation = conversationMapper.findByIdAndUserId(conversationId, userId);
        if (conversation == null) {
            throw BizException.conversationNotFound("对话不存在");
        }
        // 软删对话不允许插入新消息 / 编辑 / 重新生成
        if (conversation.getDeletedAt() != null) {
            throw BizException.conversationNotFound("对话不存在");
        }
        return conversation;
    }

    private Message requireOwnedMessage(Long userId, Long messageId) {
        if (messageId == null) {
            throw BizException.messageNotFound("消息不存在");
        }
        Message msg = messageMapper.selectOneById(messageId);
        if (msg == null) {
            throw BizException.messageNotFound("消息不存在");
        }
        // 验证该消息属于该用户的某个对话
        Conversation conversation = conversationMapper.findByIdAndUserId(msg.getConversationId(), userId);
        if (conversation == null) {
            throw BizException.messageNotFound("消息不存在");
        }
        return msg;
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw BizException.badRequest(message);
        }
        return value.trim();
    }

    /**
     * 截断到 {@code max} 字符（按 code point 算，避免半个汉字），尾部加省略号。
     * 已是空 / 长度内则原样返回。
     */
    static String truncate(String input, int max) {
        if (input == null) {
            return "";
        }
        String trimmed = input.replaceAll("\\s+", " ").trim();
        int[] codePoints = trimmed.codePoints().toArray();
        if (codePoints.length <= max) {
            return trimmed;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            sb.appendCodePoint(codePoints[i]);
        }
        return sb + "…";
    }

    private MessageResponse toResponse(Message msg) {
        return new MessageResponse(
                msg.getId(),
                msg.getConversationId(),
                msg.getRole(),
                msg.getContent(),
                Boolean.TRUE.equals(msg.getIsOrphaned()),
                msg.getCreatedAt());
    }

    private static org.springframework.ai.chat.messages.Message[] toSpringAiMessages(List<Message> messages) {
        return messages.stream()
                .map(MessageService::toSpringAiMessage)
                .toArray(org.springframework.ai.chat.messages.Message[]::new);
    }

    private static org.springframework.ai.chat.messages.Message toSpringAiMessage(Message message) {
        String role = message.getRole() == null ? Message.ROLE_USER : message.getRole();
        return switch (role) {
            case Message.ROLE_SYSTEM -> new SystemMessage(message.getContent());
            case Message.ROLE_ASSISTANT -> new AssistantMessage(message.getContent());
            default -> new UserMessage(message.getContent());
        };
    }

    private static void sendEvent(SseEmitter emitter, String name, Map<String, ?> data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException ex) {
            // 客户端已断开，IllegalStateException 是 emitter.complete() 后再 send 的副作用
            // 不再重试，由 onCompletion 取消订阅
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

    /**
     * 安全调用 emitter.complete()：Spring 在 onCompletion / onTimeout / 客户端断网等场景已
     * 隐式调用过 complete()，再调用会抛 IllegalStateException。包 try-catch 消除日志噪音。
     */
    private static void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }
}