package cn.edgarli.mapper;

import cn.edgarli.entity.Message;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息数据访问接口（ADR 0003）。
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 列出某对话的消息（按 {@code created_at} 升序）。{@code includeOrphaned = false} 时
     * 只回 {@code is_orphaned = FALSE} 的消息；true 时全回（含编辑/重新生成路径作废的）。
     */
    default List<Message> findByConversationId(Long conversationId, boolean includeOrphaned) {
        QueryWrapper query = QueryWrapper.create()
                .where(Message::getConversationId).eq(conversationId)
                .orderBy(Message::getCreatedAt, true);
        if (!includeOrphaned) {
            query.and(Message::getIsOrphaned).eq(false);
        }
        return selectListByQuery(query);
    }

    /**
     * 增量拉：{@code id > sinceId} 且 {@code is_orphaned = FALSE}（默认 UI 看不到 orphan）。
     * 用于 BroadcastChannel 收到 {@code message:created} 后增量更新。
     */
    default List<Message> findByConversationIdSinceId(Long conversationId, Long sinceId) {
        return selectListByQuery(
                QueryWrapper.create()
                        .where(Message::getConversationId).eq(conversationId)
                        .and(Message::getId).gt(sinceId)
                        .and(Message::getIsOrphaned).eq(false)
                        .orderBy(Message::getId, true));
    }

    /**
     * AI 上下文：某对话全部 {@code is_orphaned = FALSE} 的消息，按 {@code created_at} 升序。
     */
    default List<Message> findNonOrphanedContext(Long conversationId) {
        return selectListByQuery(
                QueryWrapper.create()
                        .where(Message::getConversationId).eq(conversationId)
                        .and(Message::getIsOrphaned).eq(false)
                        .orderBy(Message::getCreatedAt, true));
    }

    /**
     * AI 上下文（重新生成场景）：{@code id &lt; beforeMessageId} 的未作废消息，按 {@code created_at} 升序。
     * 重新生成某条 ASSISTANT 时只用它之前的 history。
     */
    default List<Message> findNonOrphanedContextBefore(Long conversationId, Long beforeMessageId) {
        return selectListByQuery(
                QueryWrapper.create()
                        .where(Message::getConversationId).eq(conversationId)
                        .and(Message::getIsOrphaned).eq(false)
                        .and(Message::getId).lt(beforeMessageId)
                        .orderBy(Message::getCreatedAt, true));
    }

    /**
     * 编辑 USER 消息时调用：把 {@code created_at &gt;= pivot} 的所有同对话消息标 orphan。
     * 这样后续 AI 调用只看该消息之前的 history。
     */
    default int markOrphansAfter(Long conversationId, LocalDateTime pivot) {
        return UpdateChain.of(this)
                .set(Message::getIsOrphaned, Boolean.TRUE)
                .where(Message::getConversationId).eq(conversationId)
                .and(Message::getCreatedAt).ge(pivot)
                .update() ? 1 : 0;
    }

    /**
     * 重新生成 ASSISTANT 时调用：把目标 message 自身标 orphan（保留旧版本，新版本作为新 row 插入）。
     */
    default int markOrphan(Long messageId) {
        return UpdateChain.of(this)
                .set(Message::getIsOrphaned, Boolean.TRUE)
                .where(Message::getId).eq(messageId)
                .update() ? 1 : 0;
    }

    /**
     * 判断某对话是否已存在非作废的 USER 消息（用于"首条 USER 消息触发自动标题"判定）。
     */
    default boolean existsNonOrphanedUserMessage(Long conversationId) {
        return selectCountByQuery(
                QueryWrapper.create()
                        .where(Message::getConversationId).eq(conversationId)
                        .and(Message::getRole).eq(Message.ROLE_USER)
                        .and(Message::getIsOrphaned).eq(false)) > 0;
    }
}