package cn.edgarli.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志保留配置（ADR 0004 §10）。
 * Log retention configuration (ADR 0004 §10).
 * <p>
 * 单一来源：{@code my-ai.logs.retention-days}（默认 30，env var
 * Single source of truth: {@code my-ai.logs.retention-days} (default 30, env var
 * {@code MYAI_LOGS_RETENTION_DAYS}）。同时控制：
 * {@code MYAI_LOGS_RETENTION_DAYS}). It controls:
 * <ul>
 *   <li>{@code LogCleanupTask} 清理 {@code ai_call_log} / {@code audit_log}（hard delete）</li>
 *   <li>logback {@code RollingFileAppender} 的 {@code maxHistory}</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "my-ai.logs")
public class LogProperties {

    /**
     * DB 日志保留天数（AI 调用 + 审计）。默认 30。
     * DB log retention days (AI call + audit). Default 30.
     */
    private int retentionDays = 30;

    /**
     * 访问日志（access.jsonl）保留天数。默认 30；与 DB 日志可独立配置。
     * Access log retention days (access.jsonl). Default 30; independent of DB log retention.
     */
    private int accessRetentionDays = 30;

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public int getAccessRetentionDays() {
        return accessRetentionDays;
    }

    public void setAccessRetentionDays(int accessRetentionDays) {
        this.accessRetentionDays = accessRetentionDays;
    }
}