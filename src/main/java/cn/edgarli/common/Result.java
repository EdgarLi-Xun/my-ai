package cn.edgarli.common;

/**
 * 统一 API 响应。
 *
 * @param code    业务状态码，0 表示成功
 * @param message 提示信息
 * @param data    响应数据
 */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }

    public static <T> Result<T> success() {
        return new Result<>(0, "success", null);
    }

    public static <T> Result<T> failure(int code, String message) {
        return new Result<>(code, message, null);
    }
}
