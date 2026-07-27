package cn.edgarli.common;

/**
 * 可安全返回给调用方的业务异常。
 *
 * <p>业务码约定（详见 {@code .claude/api.md} 错误码表）：
 * <ul>
 *   <li>4000 — 请求参数错误</li>
 *   <li>4010 — 未登录</li>
 *   <li>4030 — 无权访问（跨用户操作）</li>
 *   <li>4031 — 对话不存在 / 已删（ADR 0003）</li>
 *   <li>4032 — 消息不存在 / 不属于当前用户（ADR 0003）</li>
 *   <li>4033 — 编辑消息时该消息不是 USER 角色（ADR 0003）</li>
 *   <li>4034 — 重新生成时该消息不是 ASSISTANT 角色（ADR 0003）</li>
 *   <li>4035 — 默认 Key 不可用（NULL / disabled / 配置无效），ADR 0003</li>
 *   <li>4040 — 资源不存在</li>
 *   <li>4090 — 业务冲突</li>
 *   <li>5020 — 上游错误（预留）</li>
 * </ul>
 */
public class BizException extends RuntimeException {

    public static final int BAD_REQUEST = 4000;
    public static final int UNAUTHORIZED = 4010;
    public static final int FORBIDDEN = 4030;
    public static final int CONVERSATION_NOT_FOUND = 4031;
    public static final int MESSAGE_NOT_FOUND = 4032;
    public static final int MESSAGE_NOT_USER = 4033;
    public static final int MESSAGE_NOT_ASSISTANT = 4034;
    public static final int DEFAULT_KEY_UNAVAILABLE = 4035;
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

    public static BizException unauthorized(String message) {
        return new BizException(UNAUTHORIZED, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(FORBIDDEN, message);
    }

    public static BizException conversationNotFound(String message) {
        return new BizException(CONVERSATION_NOT_FOUND, message);
    }

    public static BizException messageNotFound(String message) {
        return new BizException(MESSAGE_NOT_FOUND, message);
    }

    public static BizException messageNotUser(String message) {
        return new BizException(MESSAGE_NOT_USER, message);
    }

    public static BizException messageNotAssistant(String message) {
        return new BizException(MESSAGE_NOT_ASSISTANT, message);
    }

    public static BizException defaultKeyUnavailable(String message) {
        return new BizException(DEFAULT_KEY_UNAVAILABLE, message);
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