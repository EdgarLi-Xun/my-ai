package cn.edgarli.mapper;

import cn.edgarli.entity.Conversation;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Conversation data access interface (MyBatis-Flex mapper, ADR 0003 + ADR 0005 §8).
 * 对话数据访问接口（ADR 0003 + ADR 0005 §8）。
 * <p>
 * 业务查询的实现落在 {@code cn/edgarli/mapper/ConversationMapper.xml}，
 * 简单 CRUD 仍走 {@link BaseMapper} 默认方法。
 * <p>
 * XML 引用到的参数统一加 {@link Param} 注解，避免依赖编译期 {@code -parameters}。
 *
 * @author MyAi
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 按主键 + 用户 id 查找对话（防止跨用户读）。
     * Find a conversation by id and owning user id (prevents cross-user reads).
     *
     * @param id     对话主键 / conversation primary key
     * @param userId 所属用户 id / owning user id
     * @return 对话实体或 null / conversation entity or null
     */
    Conversation findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 列出某用户未删除的对话（侧栏主视图）。
     * List non-deleted conversations of a user (sidebar main view).
     *
     * @param userId 用户主键 / user primary key
     * @return 未软删的对话列表 / list of non-deleted conversations
     */
    List<Conversation> findActiveByUserId(@Param("userId") Long userId);

    /**
     * 列出某用户已软删的对话（回收站视图）。
     * List soft-deleted conversations of a user (trash view).
     *
     * @param userId 用户主键 / user primary key
     * @return 已软删的对话列表 / list of soft-deleted conversations
     */
    List<Conversation> findDeletedByUserId(@Param("userId") Long userId);

    /**
     * 修改对话标题。
     * Update the conversation title.
     *
     * @param id       对话主键 / conversation primary key
     * @param newTitle 新标题 / new title
     * @return 受影响行数 / affected rows
     */
    int updateTitle(@Param("id") Long id, @Param("newTitle") String newTitle);

    /**
     * 软删对话（写入 deleted_at）。
     * Soft delete a conversation (sets deleted_at).
     *
     * @param id 对话主键 / conversation primary key
     * @return 受影响行数 / affected rows
     */
    int softDelete(@Param("id") Long id);

    /**
     * 恢复已软删对话（清空 deleted_at）。
     * Restore a soft-deleted conversation (clears deleted_at).
     *
     * @param id 对话主键 / conversation primary key
     * @return 受影响行数 / affected rows
     */
    int restore(@Param("id") Long id);

    /**
     * 显式维护 {@code updated_at}（H2 不依赖 ON UPDATE）。
     * Explicitly bump {@code updated_at} (H2 does not rely on ON UPDATE).
     *
     * @param id 对话主键 / conversation primary key
     * @return 受影响行数 / affected rows
     */
    int touchUpdatedAt(@Param("id") Long id);

    /**
     * 物理删除早于截止时间的对话（保留期清理）。
     * Hard delete conversations older than the cutoff (retention cleanup).
     *
     * @param cutoff 截止时间（含）/ cutoff time (inclusive)
     * @return 受影响行数 / affected rows
     */
    int hardDeleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}