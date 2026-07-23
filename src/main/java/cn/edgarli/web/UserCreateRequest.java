package cn.edgarli.web;

/**
 * 创建用户的请求体。
 *
 * @param name  用户名
 * @param email 邮箱
 */
public record UserCreateRequest(String name, String email) {
}
