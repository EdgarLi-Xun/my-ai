package cn.edgarli.infrastructure.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务审计标注（ADR 0004 §7）。
 * Business audit annotation (ADR 0004 §7).
 * <p>
 * 标注在 service 方法上：方法成功返回后写 {@code audit_log} 一行。
 * Annotate on service methods: writes one {@code audit_log} row after the method returns successfully.
 * target_id 提取规则：
 * target_id extraction rules:
 * <ol>
 *   <li>返回值有 {@code getId()} 方法 → 用返回值 id（覆盖 {@code UserApiKey} / {@code Conversation} / {@code Message}）</li>
 *   <li>否则取方法参数中最后一个 {@link Long} 作为 fallback</li>
 * </ol>
 * <p>
 * 与 {@code @Transactional} 同事务回滚：{@link AuditAspect} 在 {@code proceed()} 之后写日志，
 * 若原方法事务回滚，audit_log 行也跟着回滚（同一 connection）。
 * Rolls back in the same transaction as {@code @Transactional}: {@link AuditAspect} writes the log
 * after {@code proceed()}; if the original method's transaction rolls back, the audit_log row rolls back too (same connection).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * 动作名（如 {@code "USER_API_KEY_CREATE"} / {@code "CONVERSATION_SOFT_DELETE"}）。
     * Action name (e.g. {@code "USER_API_KEY_CREATE"} / {@code "CONVERSATION_SOFT_DELETE"}).
     *
     * @return 动作名 / action name
     */
    String action();

    /**
     * 目标实体类型（如 {@code "UserApiKey"}）。可空；为空时从方法返回类型推断。
     * Target entity type (e.g. {@code "UserApiKey"}). Empty falls back to the method return type.
     *
     * @return 实体类型或空串 / entity type or empty string
     */
    String targetType() default "";
}