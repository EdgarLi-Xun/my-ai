package cn.edgarli.web;

/**
 * 认证响应（注册/登录成功后返回 token 和用户信息）。
 */
public record AuthResponse(Long userId, String name, String email, String token) {
}
