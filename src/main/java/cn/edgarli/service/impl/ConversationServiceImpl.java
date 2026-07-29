package cn.edgarli.service.impl;

import cn.edgarli.common.BizException;
import cn.edgarli.entity.Conversation;
import cn.edgarli.service.ConversationService;
import cn.edgarli.entity.User;
import cn.edgarli.mapper.ConversationMapper;
import cn.edgarli.mapper.UserMapper;
import cn.edgarli.infrastructure.audit.Auditable;
import cn.edgarli.web.vo.ConversationVo;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Default implementation of {@link ConversationService}.
 * {@link ConversationService} 默认实现。
 *
 * @author MyAi
 */
@Service
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final UserMapper userMapper;

    public ConversationServiceImpl(ConversationMapper conversationMapper, UserMapper userMapper) {
        this.conversationMapper = conversationMapper;
        this.userMapper = userMapper;
    }

    /**
     * Create an empty conversation. Transactional: insert runs in one transaction.
     * 创建空对话。事务边界：插入在同一事务内。
     *
     * @param userId user id / 用户 ID
     * @return newly created conversation / 新创建的对话
     */
    @Override
    @Transactional
    @Auditable(action = "CONVERSATION_CREATE", targetType = "Conversation")
    public ConversationVo create(Long userId) {
        requireUser(userId);
        LocalDateTime now = LocalDateTime.now();
        // 创建/更新时间戳 / create and update timestamps
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle("新对话");
        conversation.setTitleManuallySet(false);
        // 首条 USER 消息可覆盖标题 / first USER message may overwrite title
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);
        return toResponse(conversation);
    }

    /**
     * List the current user's conversations. Read-only, no transaction.
     * 列当前用户的对话。只读，无需事务。
     *
     * @param userId user id / 用户 ID
     * @param includeDeleted whether to include soft-deleted / 是否包含已软删
     * @return conversation list / 对话列表
     */
    @Override
    public List<ConversationVo> list(Long userId, boolean includeDeleted) {
        requireUser(userId);
        List<Conversation> conversations = includeDeleted
                ? conversationMapper.findDeletedByUserId(userId)
                : conversationMapper.findActiveByUserId(userId);
        return conversations.stream().map(this::toResponse).toList();
    }

    /**
     * Update title. Transactional: update + post-read in one transaction.
     * 改标题。事务边界：update 与后续读在同一事务内。
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id / 对话 ID
     * @param newTitle new title / 新标题
     * @return updated conversation / 更新后的对话
     */
    @Override
    @Transactional
    @Auditable(action = "CONVERSATION_UPDATE_TITLE", targetType = "Conversation")
    public ConversationVo updateTitle(Long userId, Long conversationId, String newTitle) {
        requireUser(userId);
        requireOwnedConversation(userId, conversationId);
        String title = trimToNull(newTitle);
        // trim 后的新标题 / trimmed new title
        if (title == null) {
            throw BizException.badRequest("标题不能为空");
        }
        if (title.length() > 200) {
            throw BizException.badRequest("标题长度不能超过 200 字");
        }
        conversationMapper.updateTitle(conversationId, title);
        Conversation updated = conversationMapper.findByIdAndUserId(conversationId, userId);
        // 重读拿最新行 / re-read for latest row
        return toResponse(updated);
    }

    /**
     * Soft delete: set deleted_at = NOW(). Transactional.
     * 软删：{@code deleted_at = NOW()}。单事务。
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id / 对话 ID
     * @return updated conversation / 更新后的对话
     */
    @Override
    @Transactional
    @Auditable(action = "CONVERSATION_SOFT_DELETE", targetType = "Conversation")
    public ConversationVo softDelete(Long userId, Long conversationId) {
        requireUser(userId);
        requireOwnedConversation(userId, conversationId);
        conversationMapper.softDelete(conversationId);
        Conversation updated = conversationMapper.findByIdAndUserId(conversationId, userId);
        return toResponse(updated);
    }

    /**
     * Restore: set deleted_at = NULL. Transactional.
     * 恢复：{@code deleted_at = NULL}。单事务。
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id / 对话 ID
     * @return updated conversation / 更新后的对话
     */
    @Override
    @Transactional
    @Auditable(action = "CONVERSATION_RESTORE", targetType = "Conversation")
    public ConversationVo restore(Long userId, Long conversationId) {
        requireUser(userId);
        requireOwnedConversation(userId, conversationId);
        conversationMapper.restore(conversationId);
        Conversation updated = conversationMapper.findByIdAndUserId(conversationId, userId);
        return toResponse(updated);
    }

    /**
     * Hard delete: DELETE row directly; H2 cascades message deletion via FK CASCADE. Transactional.
     * 永久删除：直接 DELETE 行，H2 按 FK CASCADE 自动级联删 message。单事务。
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id / 对话 ID
     */
    @Override
    @Transactional
    @Auditable(action = "CONVERSATION_HARD_DELETE", targetType = "Conversation")
    public void hardDelete(Long userId, Long conversationId) {
        requireUser(userId);
        requireOwnedConversation(userId, conversationId);
        int affected = conversationMapper.deleteByQuery(
                QueryWrapper.create()
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

    private ConversationVo toResponse(Conversation conversation) {
        return new ConversationVo(
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