package cn.edgarli.common;

/**
 * 可安全返回给调用方的业务异常。
 */
public class BizException extends RuntimeException {

    public static final int BAD_REQUEST = 4000;
    public static final int NOT_FOUND = 4040;
    public static final int CONFLICT = 4090;
    public static final int UPSTREAM_ERROR = 5020;

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BizException badRequest(String message) {
        return new BizException(BAD_REQUEST, message);
    }

    public static BizException notFound(String message) {
        return new BizException(NOT_FOUND, message);
    }

    public static BizException conflict(String message) {
        return new BizException(CONFLICT, message);
    }

    public static BizException upstream(String message) {
        return new BizException(UPSTREAM_ERROR, message);
    }
}
