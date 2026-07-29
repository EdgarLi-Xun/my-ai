package cn.edgarli.service;

import cn.edgarli.web.vo.ConversationVo;

import java.util.List;

/**
 * Conversation management service (ADR 0003).
 * 对话管理服务（ADR 0003）。
 * <p>
 * Every method first calls {@code findByIdAndUserId} to verify ownership; mismatched owner or missing conversation throws
 * 所有方法先 {@code findByIdAndUserId} 验 owner，owner 不匹配或对话不存在抛
 * {@link cn.edgarli.common.BizException#conversationNotFound(String)} (4031).
 *
 * @author MyAi
 */
public interface ConversationService {

    /**
     * Create an empty conversation with placeholder title "新对话".
     * 创建空对话，标题占位为 "新对话"。
     *
     * @param userId user id / 用户 ID
     * @return newly created conversation / 新创建的对话
     */
    ConversationVo create(Long userId);

    /**
     * List the current user's conversations. {@code includeDeleted = false} returns active conversations;
     * 列当前用户的对话。{@code includeDeleted = false} 返回活跃对话，
     * {@code true} returns the trash (where {@code deleted_at IS NOT NULL}).
     * true 返回 trash 区（{@code deleted_at IS NOT NULL}）。
     *
     * @param userId user id / 用户 ID
     * @param includeDeleted whether to include soft-deleted conversations / 是否包含已软删对话
     * @return conversation list / 对话列表
     */
    List<ConversationVo> list(Long userId, boolean includeDeleted);

    /**
     * Update title. Forces {@code title_manually_set = TRUE} so the next first USER message will not overwrite it.
     * 改标题。强制设 {@code title_manually_set = TRUE}，避免下次首条 USER 消息覆盖。
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id / 对话 ID
     * @param newTitle new title / 新标题
     * @return updated conversation / 更新后的对话
     */
    ConversationVo updateTitle(Long userId, Long conversationId, String newTitle);

    /**
     * Soft delete: {@code deleted_at = NOW()}.
     * 软删：{@code deleted_at = NOW()}。
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id / 对话 ID
     * @return updated conversation / 更新后的对话
     */
    ConversationVo softDelete(Long userId, Long conversationId);

    /**
     * Restore a soft-deleted conversation: {@code deleted_at = NULL}.
     * 恢复软删对话：{@code deleted_at = NULL}。
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id / 对话 ID
     * @return updated conversation / 更新后的对话
     */
    ConversationVo restore(Long userId, Long conversationId);

    /**
     * Hard delete: DELETE row directly; H2 cascades message deletion via FK CASCADE.
     * 永久删除：直接 DELETE 行，H2 按 FK CASCADE 自动级联删 message。
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id / 对话 ID
     */
    void hardDelete(Long userId, Long conversationId);
}