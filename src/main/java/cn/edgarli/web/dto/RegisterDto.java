package cn.edgarli.web.dto;

/**
 * Register request body.
 * 注册请求体。
 *
 * @param name     用户名（注册时设置，不允许重复）/ user name (set on register, must be unique)
 * @param email    邮箱（登录身份；命中 admin 邮箱列表时写 ROLE_ADMIN）/ email (login identifier; ROLE_ADMIN when matching admin allowlist)
 * @param password 密码（明文传输，service 层 BCrypt 散列后写 password_hash）/ password (plain text over wire; BCrypt-hashed into password_hash by service)
 */
public record RegisterDto(String name, String email, String password) {
}