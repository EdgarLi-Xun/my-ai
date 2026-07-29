package cn.edgarli.infrastructure.task;

import cn.edgarli.infrastructure.config.LogProperties;
import cn.edgarli.mapper.AiCallLogMapper;
import cn.edgarli.mapper.AuditLogMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 日志清理任务（ADR 0004 §10 / §19）。
 * Log cleanup task (ADR 0004 §10 / §19).
 * <p>
 * 每天凌晨 04:00（Asia/Shanghai）跑一次：
 * Runs once per day at 04:00 (Asia/Shanghai):
 * <ul>
 *   <li>{@code ai_call_log.created_at < now - retentionDays} → 物理删</li>
 *   <li>{@code audit_log.deleted_at IS NULL AND created_at < now - retentionDays} → 软删
 *       （{@code deleted_at = now()}，保留 30 天反悔窗口）</li>
 *   <li>{@code audit_log.deleted_at < now - retentionDays} → 物理删</li>
 * </ul>
 * <p>
 * retention 由 {@link LogProperties#getRetentionDays()} 提供，默认 30，可被
 * Retention comes from {@link LogProperties#getRetentionDays()}, default 30, overridable via
 * env var {@code MYAI_LOGS_RETENTION_DAYS} 覆盖。
 * env var {@code MYAI_LOGS_RETENTION_DAYS}.
 */
@Component
public class LogCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(LogCleanupTask.class);

    private final AiCallLogMapper aiCallLogMapper;
    private final AuditLogMapper auditLogMapper;
    private final LogProperties logProperties;

    /**
     * 构造日志清理任务。
     * Construct the log cleanup task.
     *
     * @param aiCallLogMapper AI 调用日志 mapper / AI call log mapper
     * @param auditLogMapper 审计日志 mapper / audit log mapper
     * @param logProperties 日志保留配置 / log retention properties
     */
    public LogCleanupTask(AiCallLogMapper aiCallLogMapper,
                          AuditLogMapper auditLogMapper,
                          LogProperties logProperties) {
        this.aiCallLogMapper = aiCallLogMapper;
        this.auditLogMapper = auditLogMapper;
        this.logProperties = logProperties;
    }

    /** 凌晨 4 点（秒、分、时）。 */
    /** Runs at 04:00 daily (second, minute, hour). */
    @Scheduled(cron = "0 4 * * * *", zone = "Asia/Shanghai")
    public void cleanup() {
        int retentionDays = logProperties.getRetentionDays();
        LocalDateTime softCutoff = LocalDateTime.now().minusDays(retentionDays); // 软删截止时间 / soft-delete cutoff
        LocalDateTime hardCutoff = LocalDateTime.now().minusDays(retentionDays); // 物理删截止时间 / hard-delete cutoff

        try {
            // ai_call_log：直接物理删（无需软删窗口）
            // ai_call_log: hard-delete directly (no soft-delete window)
            int aiDeleted = aiCallLogMapper.deleteByQuery(
                    QueryWrapper.create().where("created_at < {0}", softCutoff));
            log.info("ai_call_log cleanup: deleted {} rows older than {} days", aiDeleted, retentionDays);
        } catch (Exception ex) {
            log.error("ai_call_log cleanup failed", ex);
        }

        try {
            // audit_log 两步：先软删，再物理删
            // audit_log two-step: soft-delete first, then hard-delete
            // TODO MyBatis-Flex 1.11.8 UpdateChain.update() 仅返回 boolean；
            // TODO 拿不到真实软删行数，目前只记 0/1。改用 mapper 层 API 可拿到 int rowsAffected。
            // TODO MyBatis-Flex 1.11.8 UpdateChain.update() returns boolean only,
            // TODO so we can't get the real soft-deleted row count; reports 0/1.
            // TODO Switch to a mapper-level API to get the int rowsAffected.
            int softDeleted = UpdateChain.of(auditLogMapper)
                    .set("deleted_at", LocalDateTime.now())
                    .where("deleted_at IS NULL")
                    .and("created_at < {0}", softCutoff)
                    .update() ? 1 : 0;
            int hardDeleted = auditLogMapper.deleteByQuery(
                    QueryWrapper.create()
                            .where("deleted_at IS NOT NULL")
                            .and("deleted_at < {0}", hardCutoff));
            log.info("audit_log cleanup: soft-deleted {} rows, hard-deleted {} rows older than {} days",
                    softDeleted, hardDeleted, retentionDays);
        } catch (Exception ex) {
            log.error("audit_log cleanup failed", ex);
        }
    }
}