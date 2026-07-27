package cn.edgarli.mapper;

import cn.edgarli.entity.Conversation;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话数据访问接口（ADR 0003）。
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 按 id + owner 查对话（含已删），用于 owner 校验。
     */
    default Conversation findByIdAndUserId(Long id, Long userId) {
        return selectOneByQuery(
                QueryWrapper.create()
                        .where(Conversation::getId).eq(id)
                        .and(Conversation::getUserId).eq(userId));
    }

    /**
     * 当前用户的活跃对话（{@code deleted_at IS NULL}），按 {@code updated_at DESC}。
     */
    default List<Conversation> findActiveByUserId(Long userId) {
        return selectListByQuery(
                QueryWrapper.create()
                        .where(Conversation::getUserId).eq(userId)
                        .and(Conversation::getDeletedAt).isNull()
                        .orderBy(Conversation::getUpdatedAt, false));
    }

    /**
     * 当前用户软删中的对话（{@code deleted_at IS NOT NULL}），按 {@code deleted_at DESC}。
     */
    default List<Conversation> findDeletedByUserId(Long userId) {
        return selectListByQuery(
                QueryWrapper.create()
                        .where(Conversation::getUserId).eq(userId)
                        .and(Conversation::getDeletedAt).isNotNull()
                        .orderBy(Conversation::getDeletedAt, false));
    }

    /**
     * 改标题，同时必须翻转 {@code title_manually_set}，避免下次首条 USER 消息覆盖用户改过的标题。
     */
    default int updateTitle(Long id, String newTitle) {
        return UpdateChain.of(this)
                .set(Conversation::getTitle, newTitle)
                .set(Conversation::getTitleManuallySet, true)
                .where(Conversation::getId).eq(id)
                .update() ? 1 : 0;
    }

    /**
     * 软删：{@code deleted_at = NOW()}。
     */
    default int softDelete(Long id) {
        return UpdateChain.of(this)
                .set(Conversation::getDeletedAt, LocalDateTime.now())
                .where(Conversation::getId).eq(id)
                .update() ? 1 : 0;
    }

    /**
     * 恢复：{@code deleted_at = NULL}。
     */
    default int restore(Long id) {
        return UpdateChain.of(this)
                .set(Conversation::getDeletedAt, (LocalDateTime) null)
                .where(Conversation::getId).eq(id)
                .update() ? 1 : 0;
    }

    /**
     * 显式维护 {@code updated_at}：H2 不依赖 {@code ON UPDATE CURRENT_TIMESTAMP}，
     * 由 service 层在消息插入/编辑/重新生成后调一次。
     */
    default int touchUpdatedAt(Long id) {
        return UpdateChain.of(this)
                .set(Conversation::getUpdatedAt, LocalDateTime.now())
                .where(Conversation::getId).eq(id)
                .update() ? 1 : 0;
    }

    /**
     * {@code ConversationCleanupTask} 用：删掉 {@code deleted_at < cutoff} 的所有行
     * （H2 自动按 FK CASCADE 删 message）。
     */
    default int hardDeleteOlderThan(LocalDateTime cutoff) {
        return deleteByQuery(
                QueryWrapper.create()
                        .where(Conversation::getDeletedAt).isNotNull()
                        .and(Conversation::getDeletedAt).lt(cutoff));
    }
}