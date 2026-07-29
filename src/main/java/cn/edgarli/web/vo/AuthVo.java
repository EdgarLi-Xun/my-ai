package cn.edgarli.web.vo;

/**
 * Auth response (returned after register / login).
 * 认证响应（注册/登录成功后返回 token 和用户信息）。
 *
 * @param userId 用户 ID / user ID
 * @param name   用户名 / user name
 * @param email  邮箱 / email
 * @param token  JWT token（前端写 localStorage 键 myai.token）/ JWT token (frontend stores in localStorage key myai.token)
 */
public record AuthVo(Long userId, String name, String email, String token) {
}