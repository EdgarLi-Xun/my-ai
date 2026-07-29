package cn.edgarli.web.dto;

/**
 * Create user request body (admin/internal path).
 * 创建用户的请求体（管理 / 内部路径）。
 *
 * @param name  用户名 / user name
 * @param email 邮箱 / email
 */
public record UserCreateDto(String name, String email) {
}