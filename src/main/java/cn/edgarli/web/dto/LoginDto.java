package cn.edgarli.web.dto;

/**
 * Login request body.
 * 登录请求体。
 *
 * @param email    邮箱（登录身份）/ email (login identifier)
 * @param password 密码（明文传输，service 层 BCrypt 散列）/ password (plain text over wire; BCrypt-hashed by service)
 */
public record LoginDto(String email, String password) {
}