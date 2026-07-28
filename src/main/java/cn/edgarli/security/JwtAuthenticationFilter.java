package cn.edgarli.security;

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
 * <p>
 * 解析两个 claim：{@code uid} 与 {@code role}（ADR 0004）。旧 token 没有
 * {@code role} 字段时回退 {@link cn.edgarli.entity.User#ROLE_USER}。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

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
