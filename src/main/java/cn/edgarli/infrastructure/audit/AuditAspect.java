package cn.edgarli.infrastructure.audit;

import cn.edgarli.entity.AuditLog;
import cn.edgarli.mapper.AuditLogMapper;
import cn.edgarli.infrastructure.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * Audit log AOP（ADR 0004 §7 / §9）。
 * 审计日志 AOP（ADR 0004 §7 / §9）。
 * <p>
 * 拦截 {@link Auditable} 方法：proceed() 成功后写 {@code audit_log}。
 * Intercepts {@link Auditable} methods: writes {@code audit_log} after proceed() succeeds.
 * <p>
 * target_id 提取（ADR §9 选 B）：
 * target_id extraction (ADR §9 option B):
 * <ol>
 *   <li>返回值非 null 且有 {@code getId()}（无参方法）→ 反射取 id</li>
 *   <li>否则遍历方法参数，最后一个 {@link Long} 类型作为 fallback</li>
 *   <li>都取不到 → targetId = null（仍然写一行，便于追溯"调用了哪个 action"）</li>
 * </ol>
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditLogMapper auditLogMapper;

    public AuditAspect(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 环绕通知：执行原方法并按需写审计日志。
     * Around advice: execute the target method and write audit log if it succeeds.
     *
     * @param pjp 切点 / join point
     * @param auditable 审计标注 / @Auditable annotation
     * @return 原方法返回值 / original method return value
     * @throws Throwable 原方法抛出的异常 / any exception thrown by the target method
     */
    @Around("@annotation(auditable)")
    public Object around(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        Object result = pjp.proceed();
        try {
            record(pjp, auditable, result);
        } catch (Exception ex) {
            // audit_log 写失败不影响主业务；只记日志
            // audit_log write failure does not affect main business; only log it
            log.warn("Audit log write failed for action={}", auditable.action(), ex);
        }
        return result;
    }

    /**
     * 构造 audit_log 行并写入数据库。
     * Build an audit_log row and insert it into the database.
     *
     * @param pjp 切点 / join point
     * @param auditable 审计标注 / @Auditable annotation
     * @param result 原方法返回值 / original method return value
     */
    private void record(ProceedingJoinPoint pjp, Auditable auditable, Object result) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();

        Long targetId = extractTargetId(result, pjp.getArgs(), method);
        String targetType = auditable.targetType();
        if (targetType.isEmpty()) {
            targetType = method.getReturnType() == void.class || method.getReturnType() == Void.class
                    ? null
                    : method.getReturnType().getSimpleName();
        }

        AuditLog row = new AuditLog();
        row.setUserId(currentUserId());
        row.setAction(auditable.action());
        row.setTargetType(targetType);
        row.setTargetId(targetId);
        row.setIpAddress(currentIp());
        row.setUserAgent(currentUserAgent());
        row.setCreatedAt(LocalDateTime.now());
        // deleted_at 留 null（LogCleanupTask 负责软删）
        // deleted_at stays null (LogCleanupTask handles soft-delete)
        auditLogMapper.insert(row);
    }

    /**
     * 按 ADR §9 选 B 规则提取 target_id。
     * Extract target_id by ADR §9 option B rules.
     *
     * @param result 原方法返回值 / original method return value
     * @param args 方法参数 / method arguments
     * @param method 方法 / method
     * @return 提取到的目标 id，无法解析时返回 null / resolved target id, or null
     */
    private static Long extractTargetId(Object result, Object[] args, Method method) {
        if (result != void.class && result != null) {
            // 1) Lombok @Data 类：getId() / Lombok @Data class: getId()
            Long id = tryInvokeNoArg(result, "getId");
            if (id != null) {
                return id;
            }
            // 2) Java record：id() / Java record: id()
            id = tryInvokeNoArg(result, "id");
            if (id != null) {
                return id;
            }
        }
        // fallback：方法参数最后一个 Long / fallback: last Long parameter
        if (args != null) {
            for (int i = args.length - 1; i >= 0; i--) {
                if (args[i] instanceof Long id) {
                    return id;
                }
            }
        }
        return null;
    }

    /**
     * 用反射无参调用指定方法，若返回 Long 则返回，否则返回 null。
     * Invoke the given no-arg method reflectively; return its Long result or null.
     *
     * @param target 目标对象 / target object
     * @param methodName 方法名 / method name
     * @return 反射结果 Long，或 null / reflected Long value, or null
     */
    private static Long tryInvokeNoArg(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            Object value = m.invoke(target);
            if (value instanceof Long id) {
                return id;
            }
            return null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        } catch (Exception ex) {
            log.warn("Failed to invoke {}() on {}", methodName, target.getClass(), ex);
            return null;
        }
    }

    /**
     * 取当前 SecurityContext 里的 userId，缺时返回 null（系统后台作业）。
     * Read the current userId from SecurityContext; null for background/system jobs.
     *
     * @return 当前用户 id 或 null / current user id or null
     */
    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof AuthPrincipal principal && principal.getUserId() != null) {
            return principal.getUserId();
        }
        return null; // 系统后台动作（无 SecurityContext）允许 NULL
                   // system background action (no SecurityContext) allows NULL
    }

    /**
     * 取客户端 IP：优先 X-Forwarded-For 第一项，否则 remoteAddr。
     * Read client IP: prefer the first X-Forwarded-For entry, otherwise remoteAddr.
     *
     * @return 客户端 IP 字符串 / client IP string
     */
    private static String currentIp() {
        HttpServletRequest req = currentRequest();
        if (req == null) {
            return null;
        }
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }

    /**
     * 取 User-Agent 并截断到 500 字符（避免超长 UA 写不进列）。
     * Read User-Agent and truncate to 500 chars (avoid oversized UA write errors).
     *
     * @return 用户代理字符串 / user agent string
     */
    private static String currentUserAgent() {
        HttpServletRequest req = currentRequest();
        if (req == null) {
            return null;
        }
        String ua = req.getHeader("User-Agent");
        if (ua != null && ua.length() > 500) {
            // 表列宽 500，避免超长 UA 报错
            // column width 500, avoid oversized UA errors
            return ua.substring(0, 500);
        }
        return ua;
    }

    /**
     * 取当前请求对象（无 RequestContext 时返回 null）。
     * Get the current request, or null if no RequestContext is bound.
     *
     * @return HttpServletRequest 或 null / HttpServletRequest or null
     */
    private static HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception ex) {
            return null;
        }
    }
}