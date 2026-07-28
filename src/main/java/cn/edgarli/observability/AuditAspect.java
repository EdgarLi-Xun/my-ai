package cn.edgarli.observability;

import cn.edgarli.entity.AuditLog;
import cn.edgarli.mapper.AuditLogMapper;
import cn.edgarli.security.AuthPrincipal;
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
 * 审计日志 AOP（ADR 0004 §7 / §9）。
 * <p>
 * 拦截 {@link Auditable} 方法：proceed() 成功后写 {@code audit_log}。
 * <p>
 * target_id 提取（ADR §9 选 B）：
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

    @Around("@annotation(auditable)")
    public Object around(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        Object result = pjp.proceed();
        try {
            record(pjp, auditable, result);
        } catch (Exception ex) {
            // audit_log 写失败不影响主业务；只记日志
            log.warn("Audit log write failed for action={}", auditable.action(), ex);
        }
        return result;
    }

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
        auditLogMapper.insert(row);
    }

    private static Long extractTargetId(Object result, Object[] args, Method method) {
        if (result != void.class && result != null) {
            try {
                Method getId = result.getClass().getMethod("getId");
                Object idValue = getId.invoke(result);
                if (idValue instanceof Long id) {
                    return id;
                }
            } catch (ReflectiveOperationException ignored) {
                // 返回值没有 getId()（NoSuchMethodException 是子类），继续走 fallback
            } catch (Exception ex) {
                log.warn("Failed to invoke getId() on {}", result.getClass(), ex);
            }
        }
        // fallback：方法参数最后一个 Long
        if (args != null) {
            for (int i = args.length - 1; i >= 0; i--) {
                if (args[i] instanceof Long id) {
                    return id;
                }
            }
        }
        return null;
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof AuthPrincipal principal && principal.getUserId() != null) {
            return principal.getUserId();
        }
        return null; // 系统后台动作（无 SecurityContext）允许 NULL
    }

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

    private static String currentUserAgent() {
        HttpServletRequest req = currentRequest();
        if (req == null) {
            return null;
        }
        String ua = req.getHeader("User-Agent");
        if (ua != null && ua.length() > 500) {
            // 表列宽 500，避免超长 UA 报错
            return ua.substring(0, 500);
        }
        return ua;
    }

    private static HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception ex) {
            return null;
        }
    }
}