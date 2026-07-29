package cn.edgarli.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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
     *
     * @param exception 异常 / exception
     * @return Result.failure(5000, ...) / Result with internal error code
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception) {
        log.error("Unhandled exception", exception);
        return Result.failure(5000, "服务异常");
    }
}