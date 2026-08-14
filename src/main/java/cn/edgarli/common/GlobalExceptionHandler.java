package cn.edgarli.common;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Convert application exceptions to HTTP 200 Result envelopes.
 * 将应用异常统一转换为 HTTP 200 的 Result 响应。
 * <p>
 * 业务异常走对应业务码；JSON 解析 / 参数错误统一收口为 4000；
 * 未捕获异常 → 5000。HTTP 状态码本身保持 200。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常 → 透传业务码。
     * Business exception → forward business code as-is.
     *
     * @param exception 业务异常 / business exception
     * @return Result(code = exception.getCode()) / Result with the business code
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException exception) {
        return Result.failure(exception.getCode(), exception.getMessage());
    }

    /**
     * 请求参数 / JSON 格式错误 → 4000。
     * Request param / JSON format errors → 4000.
     *
     * @param exception 异常（具体类型由 ExceptionHandler 列表决定）/ exception
     * @return Result.failure(BAD_REQUEST, ...) / Result with BAD_REQUEST
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpRequestMethodNotSupportedException.class
    })
    public Result<Void> handleBadRequest(Exception exception) {
        return Result.failure(BizException.BAD_REQUEST, "请求参数或 JSON 格式错误");
    }

    /**
     * 接口不存在 → 4040。
     * Endpoint not found → 4040.
     *
     * @param exception NoResourceFoundException / no-resource exception
     * @return Result.failure(NOT_FOUND, ...) / Result with NOT_FOUND
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNotFound(NoResourceFoundException exception) {
        return Result.failure(BizException.NOT_FOUND, "接口不存在");
    }

    /**
     * 未登录 → 4010。
     * Not authenticated → 4010.
     *
     * @param exception Spring Security 认证异常 / Spring Security auth exception
     * @return Result.failure(UNAUTHORIZED, ...) / Result with UNAUTHORIZED
     */
    @ExceptionHandler(AuthenticationException.class)
    public Result<Void> handleAuthentication(AuthenticationException exception) {
        return Result.failure(BizException.UNAUTHORIZED, "未登录或登录已过期");
    }

    /**
     * 已登录但权限不足 → 4030。
     * Authenticated but not authorized → 4030.
     *
     * @param exception Spring Security 拒绝访问异常 / Spring Security access-denied exception
     * @return Result.failure(FORBIDDEN, ...) / Result with FORBIDDEN
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException exception) {
        return Result.failure(BizException.FORBIDDEN, "无权访问");
    }

    /**
     * 未捕获异常 → 5000 + ERROR 日志。
     * Unhandled exception → 5000 with ERROR-level log.
     * <p>
     * SSE 流（{@code text/event-stream}）已经 committed 时不再写 body —— Spring 会按"已处理"忽略，
     * 避免 {@code HttpMessageNotWritableException} 二次失败。
     * When an SSE response ({@code text/event-stream}) is already committed, skip writing the body
     * (return {@code null}). Spring treats {@code null} from {@code @ExceptionHandler} as "no body" and
     * avoids the secondary {@code HttpMessageNotWritableException} against the committed stream.
     *
     * @param exception 异常 / exception
     * @param request   当前请求上下文（用于判断 SSE 流是否已提交）/ current request context (for SSE-committed detection)
     * @return Result.failure(5000, ...) / Result with internal error code, or {@code null} for SSE-committed responses
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception, WebRequest request) {
        if (isSseCommitted(request)) {
            // SSE 流已 commit → 不再写 Result，让连接自然结束 / SSE stream committed → skip Result, let connection close
            // 降为 debug：断连/已 commit 是正常现象 / downgrade to debug: disconnect/committed is normal
            if (log.isDebugEnabled()) {
                log.debug("Suppressed exception after SSE response committed: {}", exception.toString());
            }
            return null;
        }
        log.error("Unhandled exception", exception);
        return Result.failure(5000, "服务异常");
    }

    /**
     * 判断当前响应是否已经提交为 SSE 流。
     * Whether the current response has already been committed as an SSE stream.
     *
     * @param request 当前请求 / current request
     * @return true if SSE-committed / true if SSE-committed
     */
    private static boolean isSseCommitted(WebRequest request) {
        if (!(request instanceof ServletWebRequest swr)) {
            return false;
        }
        HttpServletResponse response = swr.getResponse();
        if (response == null || !response.isCommitted()) {
            return false;
        }
        String contentType = response.getContentType();
        return contentType != null && contentType.toLowerCase().contains("text/event-stream");
    }
}