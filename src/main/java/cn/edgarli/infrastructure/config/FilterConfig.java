package cn.edgarli.infrastructure.config;

import cn.edgarli.infrastructure.observability.TraceIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * ADR 0004 §5：把 {@link TraceIdFilter} 注册为 Servlet Filter，
 * ADR 0004 §5: register {@link TraceIdFilter} as a Servlet Filter,
 * 跑在 Spring Security 之前（这样 Security 抛 401/403 时 access log 仍能记到）。
 * running before Spring Security so 401/403 responses still get captured in access logs.
 * <p>
 * 排序用 {@link Ordered#HIGHEST_PRECEDENCE} + 偏移足够小即可；
 * Ordering uses {@link Ordered#HIGHEST_PRECEDENCE} + a small offset;
 * Spring Security 默认 filter chain 的 order = -100（HIGHEST_PRECEDENCE+99）。
 * Spring Security's default filter chain order is -100 (HIGHEST_PRECEDENCE+99).
 * 这里给 0 即可保证在 Security 之前。
 * Using 0 here is enough to run before Security.
 */
@Configuration
public class FilterConfig {

    /**
     * 注册 TraceIdFilter 到 /api/**，order = HIGHEST_PRECEDENCE+10。
     * Register TraceIdFilter for /api/** with order = HIGHEST_PRECEDENCE+10.
     *
     * @param traceIdFilter 过滤实例 / filter instance
     * @return Servlet Filter 注册 / filter registration
     */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration(TraceIdFilter traceIdFilter) {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(traceIdFilter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        // 仅作用于 /api/**（TraceIdFilter 内部也会再校验一次）
        // only applies to /api/** (TraceIdFilter rechecks internally as well)
        registration.addUrlPatterns("/api/*");
        registration.setName("traceIdFilter");
        return registration;
    }
}