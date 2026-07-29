package cn.edgarli.mapper;

import cn.edgarli.entity.Message;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Message data access interface (MyBatis-Flex mapper, ADR 0003 + ADR 0005 §8).
 * 消息数据访问接口（ADR 0003 + ADR 0005 §8）。
 * <p>
 * 业务查询的实现落在 {@code cn/edgarli/mapper/MessageMapper.xml}，
 * 简单 CRUD 仍走 {@link BaseMapper} 默认方法。
 * <p>
 * XML 引用到的参数统一加 {@link Param} 注解，避免依赖编译期 {@code -parameters}。
 *
 * @author MyAi
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 按对话 id 拉取全部消息，可选择是否包含已 orphan 的。
     * Load all messages of a conversation, optionally including orphaned ones.
     *
     * @param conversationId  对话主键 / conversation primary key
     * @param includeOrphaned 是否包含已 orphan 的消息 / whether to include orphaned messages
     * @return 消息列表（按 created_at 升序）/ messages ordered by created_at ascending
     */
    List<Message> findByConversationId(@Param("conversationId") Long conversationId,
                                       @Param("includeOrphaned") boolean includeOrphaned);

    /**
     * 取某对话中 id 大于 sinceId 的消息（增量拉取用）。
     * Load messages of a conversation with id greater than sinceId (incremental fetch).
     *
     * @param conversationId 对话主键 / conversation primary key
     * @param sinceId        起始 id（不含）/ starting id (exclusive)
     * @return 增量消息列表 / incremental messages
     */
    List<Message> findByConversationIdSinceId(@Param("conversationId") Long conversationId,
                                              @Param("sinceId") Long sinceId);

    /**
     * 取某对话未 orphan 的全部消息（AI 上下文拼接用）。
     * Load non-orphaned messages of a conversation (used to assemble AI context).
     *
     * @param conversationId 对话主键 / conversation primary key
     * @return 未 orphan 的消息按 created_at 升序 / non-orphaned messages ordered by created_at ascending
     */
    List<Message> findNonOrphanedContext(@Param("conversationId") Long conversationId);

    /**
     * 取某对话中指定消息之前的未 orphan 上下文（regenerate 限窗用）。
     * Load non-orphaned context before a given message in a conversation (used for regenerate windowing).
     *
     * @param conversationId  对话主键 / conversation primary key
     * @param beforeMessageId 边界消息 id（不含）/ pivot message id (exclusive)
     * @return 限窗后的上下文消息 / windowed context messages
     */
    List<Message> findNonOrphanedContextBefore(@Param("conversationId") Long conversationId,
                                               @Param("beforeMessageId") Long beforeMessageId);

    /**
     * 标记某对话中 created_at &gt; pivot 的所有消息为 orphan（regenerate 时回滚后续）。
     * Mark all messages of a conversation with created_at strictly after pivot as orphaned (used by regenerate rollback).
     *
     * @param conversationId 对话主键 / conversation primary key
     * @param pivot          枢轴时间 / pivot time
     * @return 受影响行数 / affected rows
     */
    int markOrphansAfter(@Param("conversationId") Long conversationId,
                         @Param("pivot") LocalDateTime pivot);

    /**
     * 单条消息标记为 orphan（edit / regenerate 替换单条时使用）。
     * Mark a single message as orphaned (used when a single message is replaced by edit / regenerate).
     *
     * @param messageId 消息主键 / message primary key
     * @return 受影响行数 / affected rows
     */
    int markOrphan(@Param("messageId") Long messageId);

    /**
     * 判断某对话是否存在未 orphan 的用户消息（用于空对话守卫）。
     * Check whether a conversation has any non-orphaned user message (used by empty-conversation guard).
     *
     * @param conversationId 对话主键 / conversation primary key
     * @return true 表示存在 / true if exists
     */
    boolean existsNonOrphanedUserMessage(@Param("conversationId") Long conversationId);
}