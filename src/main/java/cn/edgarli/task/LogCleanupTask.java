package cn.edgarli.task;

import cn.edgarli.config.LogProperties;
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
 * <p>
 * 每天凌晨 04:00（Asia/Shanghai）跑一次：
 * <ul>
 *   <li>{@code ai_call_log.created_at < now - retentionDays} → 物理删</li>
 *   <li>{@code audit_log.deleted_at IS NULL AND created_at < now - retentionDays} → 软删
 *       （{@code deleted_at = now()}，保留 30 天反悔窗口）</li>
 *   <li>{@code audit_log.deleted_at < now - retentionDays} → 物理删</li>
 * </ul>
 * <p>
 * retention 由 {@link LogProperties#getRetentionDays()} 提供，默认 30，可被
 * env var {@code MYAI_LOGS_RETENTION_DAYS} 覆盖。
 */
@Component
public class LogCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(LogCleanupTask.class);

    private final AiCallLogMapper aiCallLogMapper;
    private final AuditLogMapper auditLogMapper;
    private final LogProperties logProperties;

    public LogCleanupTask(AiCallLogMapper aiCallLogMapper,
                          AuditLogMapper auditLogMapper,
                          LogProperties logProperties) {
        this.aiCallLogMapper = aiCallLogMapper;
        this.auditLogMapper = auditLogMapper;
        this.logProperties = logProperties;
    }

    /** 凌晨 4 点（秒、分、时）。 */
    @Scheduled(cron = "0 4 * * * *", zone = "Asia/Shanghai")
    public void cleanup() {
        int retentionDays = logProperties.getRetentionDays();
        LocalDateTime softCutoff = LocalDateTime.now().minusDays(retentionDays);
        LocalDateTime hardCutoff = LocalDateTime.now().minusDays(retentionDays);

        try {
            // ai_call_log：直接物理删（无需软删窗口）
            int aiDeleted = aiCallLogMapper.deleteByQuery(
                    QueryWrapper.create().where("created_at < {0}", softCutoff));
            log.info("ai_call_log cleanup: deleted {} rows older than {} days", aiDeleted, retentionDays);
        } catch (Exception ex) {
            log.error("ai_call_log cleanup failed", ex);
        }

        try {
            // audit_log 两步：先软删，再物理删
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