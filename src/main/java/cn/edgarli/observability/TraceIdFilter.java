package cn.edgarli.observability;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ADR 0004 §5 / §11：trace_id 注入 + HTTP 访问日志。
 * <p>
 * 路径白名单：只对 {@code /api/**} 生效（其他静态资源 / H2 console / favicon 不记）。
 * <ol>
 *   <li>请求进入：读 {@code X-Trace-Id} header（缺失则 UUID v4）→ 注入 MDC
 *       {@code trace_id / request_method / request_path / client_ip}，
 *       同步写响应头 {@code X-Trace-Id}。</li>
 *   <li>请求结束（finally）：从 {@link SecurityContextHolder} 取 user_id 注入 MDC
 *       （缺失 → {@code anonymous}），通过独立 logger {@code myai.access}
 *       把 method/path/status/latency/ip/ua 写入 {@code ./logs/access.jsonl}。</li>
 *   <li>MDC.clear() 释放，避免线程复用泄漏。</li>
 * </ol>
 * 排序：注册到 {@link cn.edgarli.config.FilterConfig}，跑在 Spring Security 之前，
 * 因此 {@code finally} 阶段 MDC user_id 已被 {@link cn.edgarli.security.JwtAuthenticationFilter}
 * 注入。
 */
@Component
public class TraceIdFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    /** 访问日志专用 logger；logback-spring.xml 把它绑定到 ./logs/access.jsonl。 */
    private static final Logger accessLog = LoggerFactory.getLogger("myai.access");

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** /api/conversations/{id}/... 或 /api/messages/{id}/... 用于提取 conversation_id MDC */
    private static final Pattern CONV_PATH = Pattern.compile("^/api/conversations/(\\d+)(?:/.*)?$");
    private static final Pattern MESSAGE_PATH = Pattern.compile("^/api/messages/(\\d+)(?:/.*)?$");

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();

        // 白名单：仅 /api/** 走 trace + access log
        if (path == null || !path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put("trace_id", traceId);
        MDC.put("request_method", request.getMethod());
        MDC.put("request_path", path);
        MDC.put("client_ip", clientIp(request));
        MDC.put("user_id", "anonymous");
        // 尝试从 path 提取 conversation_id / message_id 注入 MDC（debug 排查有用）
        Matcher conv = CONV_PATH.matcher(path);
        if (conv.matches()) {
            MDC.put("conversation_id", conv.group(1));
        } else {
            Matcher msg = MESSAGE_PATH.matcher(path);
            if (msg.matches()) {
                MDC.put("message_id", msg.group(1));
            }
        }

        response.setHeader(TRACE_ID_HEADER, traceId);

        long start = System.currentTimeMillis();
        Throwable thrown = null;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            thrown = ex;
            throw ex;
        } finally {
            long latencyMs = System.currentTimeMillis() - start;
            int status = response.getStatus();

            // 在 finally 里读 SecurityContext：此时 JwtAuthenticationFilter 已填充
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Long userId) {
                MDC.put("user_id", String.valueOf(userId));
            }

            accessLog.info("http_access method={} path={} status={} latency_ms={} ip={} ua=\"{}\"",
                    request.getMethod(),
                    path,
                    status,
                    latencyMs,
                    clientIp(request),
                    request.getHeader("User-Agent") == null ? "" : request.getHeader("User-Agent"));

            if (thrown != null) {
                log.warn("Request failed: {} {}", request.getMethod(), path, thrown);
            }
            MDC.clear();
        }
    }

    /**
     * 取客户端 IP：优先 {@code X-Forwarded-For} 第一项，否则 {@code remoteAddr}。
     * 本地单机部署基本都走 remoteAddr；X-Forwarded-For 仅供将来反代部署使用。
     */
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }
}