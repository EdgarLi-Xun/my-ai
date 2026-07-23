package cn.edgarli.web;

/**
 * 登录请求体。
 */
public record LoginRequest(String email, String password) {
}
