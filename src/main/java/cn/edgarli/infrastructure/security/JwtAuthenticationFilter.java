package cn.edgarli.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从 Authorization: Bearer <token> 解析 JWT 并设置 SecurityContext。
 * Parse JWT from Authorization: Bearer <token> and populate SecurityContext.
 * <p>
 * 解析两个 claim：{@code uid} 与 {@code role}（ADR 0004）。旧 token 没有
 * Parses two claims: {@code uid} and {@code role} (ADR 0004). Older tokens without
 * {@code role} 字段时回退 {@link cn.edgarli.entity.User#ROLE_USER}。
 * {@code role} fall back to {@link cn.edgarli.entity.User#ROLE_USER}.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    /**
     * 构造过滤器，注入 JwtService。
     * Construct the filter with the JwtService dependency.
     *
     * @param jwtService JWT 工具 / JWT service
     */
    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * 解析 Authorization 头，校验 JWT 并设置 SecurityContext。
     * Parse the Authorization header, validate the JWT, and set SecurityContext.
     *
     * @param request HTTP 请求 / HTTP request
     * @param response HTTP 响应 / HTTP response
     * @param filterChain 过滤链 / filter chain
     * @throws ServletException Servlet 错误 / servlet error
     * @throws IOException I/O 错误 / I/O error
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtService.validate(token)) {
                Long userId = jwtService.parseUserId(token);
                if (userId != null) {
                    String role = jwtService.parseRole(token);
                    AuthPrincipal principal = new AuthPrincipal(userId, role);
                    SecurityContextHolder.getContext().setAuthentication(principal);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
