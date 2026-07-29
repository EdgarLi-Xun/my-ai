package cn.edgarli.service;

import cn.edgarli.entity.User;
import cn.edgarli.web.vo.AuthVo;

/**
 * Authentication service: register, login, current user.
 * 用户认证服务（注册、登录、当前用户）。
 * <p>
 * RBAC roles (ADR 0004): on register / login, the {@code User.role} is determined by
 * {@link cn.edgarli.infrastructure.config.AdminProperties#isAdmin(String)}.
 * RBAC 角色（ADR 0004）：register / login 时按 {@link cn.edgarli.infrastructure.config.AdminProperties#isAdmin(String)}
 * 决定 {@code User.role}。命中 admin email 列表 → {@link User#ROLE_ADMIN}，
 * 否则 {@link User#ROLE_USER}。
 * Match against admin email list → {@link User#ROLE_ADMIN}, otherwise {@link User#ROLE_USER}.
 *
 * @author MyAi
 */
public interface AuthService {

    /**
     * Register a new user.
     * 注册新用户。
     *
     * @param name user name (required, non-empty after trim) / 用户名（必填，trim 后非空）
     * @param email email (required, non-empty after trim; unique) / 邮箱（必填，trim 后非空；唯一）
     * @param password password (required, at least 6 chars) / 密码（必填，至少 6 位）
     * @return register response (with token) / 注册响应（含 token）
     * @throws cn.edgarli.common.BizException 4000 field missing or invalid; 4090 email already registered
     *         4000 字段缺失或非法；4090 邮箱已注册
     */
    AuthVo register(String name, String email, String password);

    /**
     * Email + password login.
     * 邮箱密码登录。
     *
     * @param email email / 邮箱
     * @param password password / 密码
     * @return login response (with token) / 登录响应（含 token）
     * @throws cn.edgarli.common.BizException 4010 wrong email or password / 4010 邮箱或密码错误
     */
    AuthVo login(String email, String password);

    /**
     * Get current logged-in user.
     * 取当前登录用户。
     *
     * @return current user / 当前用户
     * @throws cn.edgarli.common.BizException 4010 not logged in; 4040 user not found
     *         4010 未登录；4040 用户不存在
     */
    User getCurrentUser();
}