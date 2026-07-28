package cn.edgarli.service;

import cn.edgarli.common.BizException;
import cn.edgarli.entity.Conversation;
import cn.edgarli.entity.User;
import cn.edgarli.mapper.ConversationMapper;
import cn.edgarli.mapper.UserMapper;
import cn.edgarli.observability.Auditable;
import cn.edgarli.web.dto.ConversationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话管理服务（ADR 0003）。
 * <p>
 * 所有方法先 {@code findByIdAndUserId} 验 owner，owner 不匹配或对话不存在抛
 * {@link BizException#conversationNotFound(String)} (4031)。
 */
@Service
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final UserMapper userMapper;

    public ConversationService(ConversationMapper conversationMapper, UserMapper userMapper) {
        this.conversationMapper = conversationMapper;
        this.userMapper = userMapper;
    }

    /**
     * 创建空对话，标题占位为 "新对话"（前端可用首条 USER 消息自动覆盖，
     * 或用户主动改名）。
     */
    @Transactional
    @Auditable(action = "CONVERSATION_CREATE", targetType = "Conversation")
    public ConversationResponse create(Long userId) {
        requireUser(userId);
        LocalDateTime now = LocalDateTime.now();
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle("新对话");
        conversation.setTitleManuallySet(false);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);
        return toResponse(conversation);
    }

    /**
     * 列当前用户的对话。{@code includeDeleted = false} 返回活跃对话（{@code deleted_at IS NULL}），
     * true 返回 trash 区（{@code deleted_at IS NOT NULL}）。
     */
    public List<ConversationResponse> list(Long userId, boolean includeDeleted) {
        requireUser(userId);
        List<Conversation> conversations = includeDeleted
                ? conversationMapper.findDeletedByUserId(userId)
                : conversationMapper.findActiveByUserId(userId);
        return conversations.stream().map(this::toResponse).toList();
    }

    /**
     * 改标题。强制设 {@code title_manually_set = TRUE}，避免下次首条 USER 消息覆盖用户改过的标题。
     */
    @Transactional
    @Auditable(action = "CONVERSATION_UPDATE_TITLE", targetType = "Conversation")
    public ConversationResponse updateTitle(Long userId, Long conversationId, String newTitle) {
        requireUser(userId);
        requireOwnedConversation(userId, conversationId);
        String title = trimToNull(newTitle);
        if (title == null) {
            throw BizException.badRequest("标题不能为空");
        }
        if (title.length() > 200) {
            throw BizException.badRequest("标题长度不能超过 200 字");
        }
        conversationMapper.updateTitle(conversationId, title);
        Conversation updated = conversationMapper.findByIdAndUserId(conversationId, userId);
        return toResponse(updated);
    }

    /**
     * 软删：{@code deleted_at = NOW()}。如果对话已软删过则幂等返回当前状态。
     */
    @Transactional
    @Auditable(action = "CONVERSATION_SOFT_DELETE", targetType = "Conversation")
    public ConversationResponse softDelete(Long userId, Long conversationId) {
        requireUser(userId);
        requireOwnedConversation(userId, conversationId);
        conversationMapper.softDelete(conversationId);
        Conversation updated = conversationMapper.findByIdAndUserId(conversationId, userId);
        return toResponse(updated);
    }

    /**
     * 恢复软删对话：{@code deleted_at = NULL}。
     */
    @Transactional
    @Auditable(action = "CONVERSATION_RESTORE", targetType = "Conversation")
    public ConversationResponse restore(Long userId, Long conversationId) {
        requireUser(userId);
        requireOwnedConversation(userId, conversationId);
        conversationMapper.restore(conversationId);
        Conversation updated = conversationMapper.findByIdAndUserId(conversationId, userId);
        return toResponse(updated);
    }

    /**
     * 永久删除：直接 DELETE 行，H2 按 FK CASCADE 自动级联删 message。
     */
    @Transactional
    @Auditable(action = "CONVERSATION_HARD_DELETE", targetType = "Conversation")
    public void hardDelete(Long userId, Long conversationId) {
        requireUser(userId);
        requireOwnedConversation(userId, conversationId);
        int affected = conversationMapper.deleteByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .where(Conversation::getId).eq(conversationId)
                        .and(Conversation::getUserId).eq(userId));
        if (affected == 0) {
            throw BizException.conversationNotFound("对话不存在");
        }
    }

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
        return conversation;
    }

    private ConversationResponse toResponse(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getUserId(),
                conversation.getTitle(),
                Boolean.TRUE.equals(conversation.getTitleManuallySet()),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                conversation.getDeletedAt());
    }

    private static String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}