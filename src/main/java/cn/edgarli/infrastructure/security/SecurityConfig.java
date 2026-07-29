package cn.edgarli.infrastructure.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置：JWT 无状态 + BCrypt 密码编码。
 * Spring Security configuration: stateless JWT + BCrypt password encoding.
 * <p>
 * 公开端点：{@code /api/auth/**}, {@code GET /api/providers}, 静态资源, H2 console, {@code /error}。
 * Public endpoints: {@code /api/auth/**}, {@code GET /api/providers}, static assets, H2 console, {@code /error}.
 * 日志查询端点：仅 ADMIN。其余 API 全部需登录。
 * Log query endpoints: ADMIN only. All other APIs require login.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    /**
     * 注入过滤器与异常处理器。
     * Inject the filter and exception handlers.
     *
     * @param jwtAuthenticationFilter JWT 过滤器 / JWT authentication filter
     * @param authenticationEntryPoint 401 处理器 / 401 entry point
     * @param accessDeniedHandler 403 处理器 / 403 access-denied handler
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /**
     * 配置 SecurityFilterChain：禁用 CSRF、设无状态会话、加入 JwtAuthenticationFilter。
     * Configure SecurityFilterChain: disable CSRF, set stateless sessions, add JwtAuthenticationFilter.
     *
     * @param http HttpSecurity / HttpSecurity builder
     * @return SecurityFilterChain / filter chain
     * @throws Exception 配置失败 / configuration failure
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // SSE 异步派发时跳过二次校验（JWT 已在首次派发时验证过）
                        // skip re-check on SSE ASYNC dispatch (JWT was already validated on initial dispatch)
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        // 公开端点 / public endpoints
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/providers").permitAll()
                        .requestMatchers("/", "/index.html", "/static/**", "/assets/**", "/favicon.ico").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // ADR 0004：日志查询端点仅 admin 可访问
                        // ADR 0004: log query endpoints are admin-only
                        .requestMatchers("/api/logs/**").hasRole("ADMIN")
                        // 其余全部需要登录 / everything else requires login
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // H2 控制台需要跳过 X-Frame-Options
        // H2 console requires X-Frame-Options to be relaxed
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    /**
     * 提供 BCrypt 密码编码器。
     * Provide the BCrypt password encoder.
     *
     * @return PasswordEncoder / password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
