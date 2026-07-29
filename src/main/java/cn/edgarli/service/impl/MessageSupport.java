package cn.edgarli.service.impl;

import cn.edgarli.common.BizException;
import cn.edgarli.entity.Conversation;
import cn.edgarli.entity.Message;
import cn.edgarli.entity.User;
import cn.edgarli.mapper.ConversationMapper;
import cn.edgarli.mapper.MessageMapper;
import cn.edgarli.mapper.UserMapper;
import cn.edgarli.web.vo.MessageVo;
import org.springframework.stereotype.Component;

/**
 * Internal shared helper for MessageCommandService / MessageQueryService (ADR 0005 §3).
 * MessageCommandService / MessageQueryService 内部共享 helper（ADR 0005 §3）。
 * <p>
 * Query / Command implementations depend on this for owner checks, message conversion, etc.
 * Query / Command 实现依赖此类取 owner 校验、消息转换等共用逻辑，
 * avoiding duplication between the two implementations.
 * 避免在两个实现里重复。
 *
 * @author MyAi
 */
@Component
public class MessageSupport {

    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final UserMapper userMapper;

    public MessageSupport(MessageMapper messageMapper,
                          ConversationMapper conversationMapper,
                          UserMapper userMapper) {
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
        this.userMapper = userMapper;
    }

    /**
     * Validate that a user exists.
     * 校验用户存在。
     *
     * @param userId user id / 用户 ID
     * @return user entity / 用户实体
     * @throws BizException 4000 userId null; 4040 user not found
     *                      4000 userId 为空；4040 用户不存在
     */
    public User requireUser(Long userId) {
        if (userId == null) {
            throw BizException.badRequest("userId 不能为空");
        }
        User user = userMapper.findById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    /**
     * Validate that a conversation is owned by the user and not soft-deleted.
     * 校验对话属于该用户且未被软删。
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id / 对话 ID
     * @return conversation entity / 对话实体
     * @throws BizException 4031 conversation not found / 4031 对话不存在
     */
    public Conversation requireOwnedConversation(Long userId, Long conversationId) {
        if (conversationId == null) {
            throw BizException.conversationNotFound("对话不存在");
        }
        Conversation conversation = conversationMapper.findByIdAndUserId(conversationId, userId);
        if (conversation == null) {
            throw BizException.conversationNotFound("对话不存在");
        }
        if (conversation.getDeletedAt() != null) {
            // 软删对话不可访问 / soft-deleted conversations are inaccessible
            throw BizException.conversationNotFound("对话不存在");
        }
        return conversation;
    }

    /**
     * Validate that a message belongs to the user (via owning conversation).
     * 校验消息属于该用户（通过其所属对话校验）。
     *
     * @param userId user id / 用户 ID
     * @param messageId message id / 消息 ID
     * @return message entity / 消息实体
     * @throws BizException 4032 message not found / 4032 消息不存在
     */
    public Message requireOwnedMessage(Long userId, Long messageId) {
        if (messageId == null) {
            throw BizException.messageNotFound("消息不存在");
        }
        Message msg = messageMapper.selectOneById(messageId);
        if (msg == null) {
            throw BizException.messageNotFound("消息不存在");
        }
        Conversation conversation = conversationMapper.findByIdAndUserId(msg.getConversationId(), userId);
        if (conversation == null) {
            throw BizException.messageNotFound("消息不存在");
        }
        return msg;
    }

    /**
     * Validate that a string is non-blank (non-null after trim).
     * 校验非空字符串（trim 后非 null）。
     *
     * @param value input / 输入
     * @param message error message / 错误消息
     * @return trimmed value / trim 后的值
     * @throws BizException 4000 blank / 4000 内容为空
     */
    public static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw BizException.badRequest(message);
        }
        return value.trim();
    }

    /**
     * Truncate to {@code max} characters (by code point to avoid splitting a CJK char), append ellipsis.
     * 截断到 {@code max} 字符（按 code point 算，避免半个汉字），尾部加省略号。
     *
     * @param input input / 输入
     * @param max max character count / 最大字符数
     * @return truncated string / 截断结果
     */
    public static String truncate(String input, int max) {
        if (input == null) {
            return "";
        }
        String trimmed = input.replaceAll("\\s+", " ").trim();
        // 合并空白并 trim / collapse whitespace and trim
        int[] codePoints = trimmed.codePoints().toArray();
        // 按 code point 切分，避免切半个汉字 / split by code point to avoid splitting CJK chars
        if (codePoints.length <= max) {
            return trimmed;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            sb.appendCodePoint(codePoints[i]);
        }
        return sb + "…";
    }

    /**
     * Map a persistent {@link Message} to a {@link MessageVo}.
     * 持久化 Message → MessageVo。
     *
     * @param msg persistent message / 持久化消息
     * @return response DTO / 响应 DTO
     */
    public MessageVo toResponse(Message msg) {
        return new MessageVo(
                msg.getId(),
                msg.getConversationId(),
                msg.getRole(),
                msg.getContent(),
                Boolean.TRUE.equals(msg.getIsOrphaned()),
                msg.getCreatedAt());
    }
}