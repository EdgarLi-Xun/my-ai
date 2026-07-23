package cn.edgarli.web;

/**
 * 注册请求体。
 */
public record RegisterRequest(String name, String email, String password) {
}
