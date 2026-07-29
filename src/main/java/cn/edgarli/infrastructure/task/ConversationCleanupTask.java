package cn.edgarli.infrastructure.task;

import cn.edgarli.infrastructure.config.TrashProperties;
import cn.edgarli.mapper.ConversationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 软删对话清理任务（ADR 0003）。
 * Soft-deleted conversation cleanup task (ADR 0003).
 * <p>
 * 每天凌晨 3 点扫 {@code deleted_at < NOW() - retentionDays} 的对话，
 * Each day at 03:00 (Asia/Shanghai) scans conversations with {@code deleted_at < NOW() - retentionDays},
 * 调用 {@link ConversationMapper#hardDeleteOlderThan(LocalDateTime)} 物理删除。
 * and calls {@link ConversationMapper#hardDeleteOlderThan(LocalDateTime)} to hard-delete them.
 * H2 2.x 按 FK CASCADE 自动级联删 message，调用方无需手动删。
 * H2 2.x cascades message deletion via FK CASCADE; no manual cleanup needed.
 * <p>
 * retention 天数从 {@link TrashProperties#getRetentionDays()} 取，默认 30；
 * Retention days come from {@link TrashProperties#getRetentionDays()}, default 30;
 * 通过环境变量 {@code MYAI_TRASH_RETENTION_DAYS} 可覆盖。
 * overridable via env var {@code MYAI_TRASH_RETENTION_DAYS}.
 */
@Component
public class ConversationCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(ConversationCleanupTask.class);

    private final ConversationMapper conversationMapper;
    private final TrashProperties trashProperties;

    /**
     * 构造清理任务。
     * Construct the cleanup task.
     *
     * @param conversationMapper 对话 mapper / conversation mapper
     * @param trashProperties 回收策略配置 / trash policy properties
     */
    public ConversationCleanupTask(ConversationMapper conversationMapper, TrashProperties trashProperties) {
        this.conversationMapper = conversationMapper;
        this.trashProperties = trashProperties;
    }

    /**
     * 凌晨 3 点执行物理删除软删对话。
     * Hard-delete soft-deleted conversations at 03:00 daily.
     */
    @Scheduled(cron = "0 3 * * * *", zone = "Asia/Shanghai")
    public void purgeOldDeleted() {
        int retentionDays = trashProperties.getRetentionDays();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays); // 保留期截止时间 / retention cutoff
        int deleted = conversationMapper.hardDeleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Purged {} soft-deleted conversations older than {} days (cutoff={})",
                    deleted, retentionDays, cutoff);
        } else {
            log.debug("No conversations to purge (cutoff={}, retentionDays={})", cutoff, retentionDays);
        }
    }
}