package cn.edgarli.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对话回收策略配置（ADR 0003）。
 * Conversation recycle policy configuration (ADR 0003).
 *
 * <p>绑定到 {@code application.yml} 的 {@code my-ai.trash.*} 段，控制
 * Bound to {@code application.yml} {@code my-ai.trash.*}; controls how many days
 * {@link cn.edgarli.infrastructure.task.ConversationCleanupTask} 软删对话保留多少天后被 hard delete。
 * soft-deleted conversations are kept before being hard-deleted by
 * {@link cn.edgarli.infrastructure.task.ConversationCleanupTask}.
 */
@ConfigurationProperties(prefix = "my-ai.trash")
public class TrashProperties {

    /**
     * 软删对话保留天数。过期后由 {@code @Scheduled} 任务清理（CASCADE 删 message）。
     * 默认 30 天，可通过环境变量 {@code MYAI_TRASH_RETENTION_DAYS} 覆盖。
     * Retention days for soft-deleted conversations. After expiry a {@code @Scheduled} task
     * hard-deletes them (CASCADE deletes messages). Default 30; override via env var
     * {@code MYAI_TRASH_RETENTION_DAYS}.
     */
    private int retentionDays = 30;

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
}