package cn.edgarli.service;

import cn.edgarli.web.vo.MessageVo;

import java.util.List;

/**
 * Message query service (ADR 0005 §3).
 * 消息查询服务（ADR 0005 §3）。
 * <p>
 * Read-only operations; separated from {@link MessageCommandService}.
 * 只读操作，与 {@link MessageCommandService} 分离。
 *
 * @author MyAi
 */
public interface MessageQueryService {

    /**
     * List conversation messages.
     * 列对话消息。
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id / 对话 ID
     * @param includeOrphaned whether to include messages with {@code is_orphaned = TRUE} / 是否包含 {@code is_orphaned = TRUE} 的消息
     * @return message list (ordered by {@code created_at} asc) / 消息列表（按 {@code created_at} 升序）
     */
    List<MessageVo> list(Long userId, Long conversationId, boolean includeOrphaned);
}