package cn.edgarli.infrastructure.security;

import cn.edgarli.common.BizException;
import cn.edgarli.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未登录访问受保护接口时返回 4010 Result，不跳转登录页。
 * Return a 4010 Result when an unauthenticated request hits a protected endpoint (no redirect to login).
 * <p>
 * REST 风格：前端拿到 4010 后自行弹登录框。
 * REST style: the frontend pops up a login dialog after receiving 4010.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 写 4010 Result（JSON）到响应。
     * Write a 4010 Result (JSON) to the response.
     *
     * @param request HTTP 请求 / HTTP request
     * @param response HTTP 响应 / HTTP response
     * @param authException Spring Security 异常 / Spring Security exception
     * @throws IOException 写入失败 / write failure
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(
                        Result.failure(BizException.UNAUTHORIZED, "未登录或登录已过期"))); // 4010 / unauthenticated
    }
}
