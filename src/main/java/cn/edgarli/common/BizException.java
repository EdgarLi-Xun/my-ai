package cn.edgarli.common;

/**
 * Business exception safe to surface to callers.
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

    /** 4000 — 请求参数错误 / request parameter error */
    public static final int BAD_REQUEST = 4000;
    /** 4010 — 未登录 / not authenticated */
    public static final int UNAUTHORIZED = 4010;
    /** 4030 — 无权访问（跨用户操作）/ forbidden (cross-user operation) */
    public static final int FORBIDDEN = 4030;
    /** 4031 — 对话不存在 / 已删（ADR 0003）/ conversation not found or already deleted (ADR 0003) */
    public static final int CONVERSATION_NOT_FOUND = 4031;
    /** 4032 — 消息不存在 / 不属于当前用户（ADR 0003）/ message not found or not owned (ADR 0003) */
    public static final int MESSAGE_NOT_FOUND = 4032;
    /** 4033 — 编辑消息时该消息不是 USER 角色（ADR 0003）/ message to edit is not USER (ADR 0003) */
    public static final int MESSAGE_NOT_USER = 4033;
    /** 4034 — 重新生成时该消息不是 ASSISTANT 角色（ADR 0003）/ message to regenerate is not ASSISTANT (ADR 0003) */
    public static final int MESSAGE_NOT_ASSISTANT = 4034;
    /** 4035 — 默认 Key 不可用（NULL / disabled / 配置无效）/ default key unavailable (null / disabled / invalid config) */
    public static final int DEFAULT_KEY_UNAVAILABLE = 4035;
    /** 4040 — 资源不存在 / resource not found */
    public static final int NOT_FOUND = 4040;
    /** 4090 — 业务冲突 / business conflict */
    public static final int CONFLICT = 4090;
    /** 5020 — 上游错误（预留）/ upstream error (reserved) */
    public static final int UPSTREAM_ERROR = 5020;

    /** 业务码 / business code */
    private final int code;

    /**
     * Construct with code and message.
     * 用业务码与消息构造。
     *
     * @param code    业务码 / business code
     * @param message 提示信息 / message
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Get the business code.
     * 取业务码。
     *
     * @return 业务码 / business code
     */
    public int getCode() {
        return code;
    }

    /**
     * 4000 — 请求参数错误 / request parameter error.
     *
     * @param message 提示信息 / message
     * @return BizException(BAD_REQUEST) / BizException with BAD_REQUEST
     */
    public static BizException badRequest(String message) {
        return new BizException(BAD_REQUEST, message);
    }

    /**
     * 4010 — 未登录 / not authenticated.
     *
     * @param message 提示信息 / message
     * @return BizException(UNAUTHORIZED) / BizException with UNAUTHORIZED
     */
    public static BizException unauthorized(String message) {
        return new BizException(UNAUTHORIZED, message);
    }

    /**
     * 4030 — 无权访问（跨用户操作）/ forbidden (cross-user operation).
     *
     * @param message 提示信息 / message
     * @return BizException(FORBIDDEN) / BizException with FORBIDDEN
     */
    public static BizException forbidden(String message) {
        return new BizException(FORBIDDEN, message);
    }

    /**
     * 4031 — 对话不存在 / 已删（ADR 0003）/ conversation not found or deleted (ADR 0003).
     *
     * @param message 提示信息 / message
     * @return BizException(CONVERSATION_NOT_FOUND) / BizException with CONVERSATION_NOT_FOUND
     */
    public static BizException conversationNotFound(String message) {
        return new BizException(CONVERSATION_NOT_FOUND, message);
    }

    /**
     * 4032 — 消息不存在 / 不属于当前用户（ADR 0003）/ message not found or not owned (ADR 0003).
     *
     * @param message 提示信息 / message
     * @return BizException(MESSAGE_NOT_FOUND) / BizException with MESSAGE_NOT_FOUND
     */
    public static BizException messageNotFound(String message) {
        return new BizException(MESSAGE_NOT_FOUND, message);
    }

    /**
     * 4033 — 编辑消息时该消息不是 USER 角色（ADR 0003）/ message to edit is not USER (ADR 0003).
     *
     * @param message 提示信息 / message
     * @return BizException(MESSAGE_NOT_USER) / BizException with MESSAGE_NOT_USER
     */
    public static BizException messageNotUser(String message) {
        return new BizException(MESSAGE_NOT_USER, message);
    }

    /**
     * 4034 — 重新生成时该消息不是 ASSISTANT 角色（ADR 0003）/ message to regenerate is not ASSISTANT (ADR 0003).
     *
     * @param message 提示信息 / message
     * @return BizException(MESSAGE_NOT_ASSISTANT) / BizException with MESSAGE_NOT_ASSISTANT
     */
    public static BizException messageNotAssistant(String message) {
        return new BizException(MESSAGE_NOT_ASSISTANT, message);
    }

    /**
     * 4035 — 默认 Key 不可用（NULL / disabled / 配置无效）/ default key unavailable.
     *
     * @param message 提示信息 / message
     * @return BizException(DEFAULT_KEY_UNAVAILABLE) / BizException with DEFAULT_KEY_UNAVAILABLE
     */
    public static BizException defaultKeyUnavailable(String message) {
        return new BizException(DEFAULT_KEY_UNAVAILABLE, message);
    }

    /**
     * 4040 — 资源不存在 / resource not found.
     *
     * @param message 提示信息 / message
     * @return BizException(NOT_FOUND) / BizException with NOT_FOUND
     */
    public static BizException notFound(String message) {
        return new BizException(NOT_FOUND, message);
    }

    /**
     * 4090 — 业务冲突 / business conflict.
     *
     * @param message 提示信息 / message
     * @return BizException(CONFLICT) / BizException with CONFLICT
     */
    public static BizException conflict(String message) {
        return new BizException(CONFLICT, message);
    }

    /**
     * 5020 — 上游错误（预留）/ upstream error (reserved).
     *
     * @param message 提示信息 / message
     * @return BizException(UPSTREAM_ERROR) / BizException with UPSTREAM_ERROR
     */
    public static BizException upstream(String message) {
        return new BizException(UPSTREAM_ERROR, message);
    }
}