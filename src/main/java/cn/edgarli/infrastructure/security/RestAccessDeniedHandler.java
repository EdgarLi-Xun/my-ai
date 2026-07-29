package cn.edgarli.infrastructure.security;

import cn.edgarli.common.BizException;
import cn.edgarli.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 已登录但无权访问时返回 4030 Result。
 * Return a 4030 Result when an authenticated user lacks permission.
 * <p>
 * 用于 Spring Security 拒绝已登录用户的访问场景。
 * Used by Spring Security when an authenticated user is denied access.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 写 4030 Result（JSON）到响应。
     * Write a 4030 Result (JSON) to the response.
     *
     * @param request HTTP 请求 / HTTP request
     * @param response HTTP 响应 / HTTP response
     * @param accessDeniedException Spring Security 异常 / Spring Security exception
     * @throws IOException 写入失败 / write failure
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(
                        Result.failure(BizException.FORBIDDEN, "无权访问"))); // 4030 / forbid
    }
}
