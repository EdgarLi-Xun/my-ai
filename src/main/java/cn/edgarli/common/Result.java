package cn.edgarli.common;

/**
 * Unified API response envelope.
 * 统一 API 响应。
 * <p>
 * {@code code = 0} 表示成功；非 0 为业务码（见 {@link BizException}）。
 * HTTP 状态码本身始终是 200（{@link GlobalExceptionHandler} 强制 200 响应体）。
 *
 * @param code    业务状态码，0 表示成功 / business status code, 0 = success
 * @param message 提示信息 / message
 * @param data    响应数据 / response data
 */
public record Result<T>(int code, String message, T data) {

    /**
     * Build a success envelope with data.
     * 构造带数据的成功响应。
     *
     * @param data 响应数据 / response data
     * @param <T>  数据类型 / data type
     * @return 成功 Result / success Result
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }

    /**
     * Build an empty success envelope.
     * 构造空成功响应。
     *
     * @param <T> 数据类型 / data type
     * @return 空成功 Result / empty success Result
     */
    public static <T> Result<T> success() {
        return new Result<>(0, "success", null);
    }

    /**
     * Build a failure envelope with business code.
     * 构造失败响应。
     *
     * @param code    业务码（非 0）/ business code (non-zero)
     * @param message 提示信息 / message
     * @param <T>     数据类型 / data type
     * @return 失败 Result / failure Result
     */
    public static <T> Result<T> failure(int code, String message) {
        return new Result<>(code, message, null);
    }
}