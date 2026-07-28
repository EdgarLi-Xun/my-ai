package cn.edgarli.config;

import cn.edgarli.observability.TraceIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * ADR 0004 §5：把 {@link TraceIdFilter} 注册为 Servlet Filter，
 * 跑在 Spring Security 之前（这样 Security 抛 401/403 时 access log 仍能记到）。
 * <p>
 * 排序用 {@link Ordered#HIGHEST_PRECEDENCE} + 偏移足够小即可；
 * Spring Security 默认 filter chain 的 order = -100（HIGHEST_PRECEDENCE+99）。
 * 这里给 0 即可保证在 Security 之前。
 */
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration(TraceIdFilter traceIdFilter) {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(traceIdFilter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        // 仅作用于 /api/**（TraceIdFilter 内部也会再校验一次）
        registration.addUrlPatterns("/api/*");
        registration.setName("traceIdFilter");
        return registration;
    }
}