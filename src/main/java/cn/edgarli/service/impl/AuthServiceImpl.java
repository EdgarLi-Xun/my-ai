package cn.edgarli.service.impl;

import cn.edgarli.common.BizException;
import cn.edgarli.service.AuthService;
import cn.edgarli.infrastructure.config.AdminProperties;
import cn.edgarli.entity.User;
import cn.edgarli.mapper.UserMapper;
import cn.edgarli.infrastructure.security.AuthPrincipal;
import cn.edgarli.infrastructure.security.JwtService;
import cn.edgarli.web.vo.AuthVo;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Default implementation of {@link AuthService}.
 * {@link AuthService} 默认实现。
 *
 * @author MyAi
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    public AuthServiceImpl(UserMapper userMapper,
                           JwtService jwtService,
                           PasswordEncoder passwordEncoder,
                           AdminProperties adminProperties) {
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.adminProperties = adminProperties;
    }

    /**
     * Register a new user. Transactional: the {@code users} insert and uniqueness check run in a single transaction.
     * 注册新用户。事务边界：用户写入与邮箱唯一性检查在同一事务内完成。
     *
     * @param name user name (required, non-empty after trim) / 用户名（必填，trim 后非空）
     * @param email email (required, non-empty after trim; unique) / 邮箱（必填，trim 后非空；唯一）
     * @param password password (required, at least 6 chars) / 密码（必填，至少 6 位）
     * @return register response (with token) / 注册响应（含 token）
     * @throws BizException 4000 field missing or invalid; 4090 email already registered
     *                      4000 字段缺失或非法；4090 邮箱已注册
     */
    @Override
    @Transactional
    public AuthVo register(String name, String email, String password) {
        String trimmedName = trim(name);
        // trim 后的用户名 / trimmed user name
        String trimmedEmail = trim(email);
        // trim 后的邮箱 / trimmed email
        String trimmedPassword = trim(password);
        // trim 后的密码 / trimmed password

        if (trimmedName == null) {
            throw BizException.badRequest("用户名不能为空");
        }
        if (trimmedEmail == null) {
            throw BizException.badRequest("邮箱不能为空");
        }
        if (trimmedPassword == null || trimmedPassword.length() < 6) {
            throw BizException.badRequest("密码至少 6 位");
        }

        // email 唯一性
        User existing = findByEmail(trimmedEmail);
        // 查重结果 / duplicate-check result
        if (existing != null) {
            throw BizException.conflict("该邮箱已被注册");
        }

        User user = new User();
        // 新建用户实体 / new user entity
        user.setName(trimmedName);
        user.setEmail(trimmedEmail);
        // BCrypt 散列后存库 / persist BCrypt-hashed password
        user.setPasswordHash(passwordEncoder.encode(trimmedPassword));
        // 按 admin email 列表决定 RBAC 角色 / role decided by admin email list
        user.setRole(adminProperties.isAdmin(trimmedEmail) ? User.ROLE_ADMIN : User.ROLE_USER);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);

        String token = jwtService.generate(user.getId(), user.getRole());
        // 生成 JWT（含 uid + role） / issue JWT (contains uid + role)
        return new AuthVo(user.getId(), user.getName(), user.getEmail(), token);
    }

    /**
     * Email + password login. Read-only: no transaction needed.
     * 邮箱密码登录。无写操作，无需事务。
     *
     * @param email email / 邮箱
     * @param password password / 密码
     * @return login response (with token) / 登录响应（含 token）
     * @throws BizException 4010 wrong email or password / 4010 邮箱或密码错误
     */
    @Override
    public AuthVo login(String email, String password) {
        String trimmedEmail = trim(email);
        // trim 后的邮箱 / trimmed email
        String trimmedPassword = trim(password);
        // trim 后的密码 / trimmed password

        if (trimmedEmail == null) {
            throw BizException.badRequest("邮箱不能为空");
        }
        if (trimmedPassword == null) {
            throw BizException.badRequest("密码不能为空");
        }

        User user = requireByEmail(trimmedEmail);
        String storedHash = userMapper.findPasswordHashById(user.getId());
        // DB 中的密码散列 / password hash stored in DB
        if (storedHash == null || !passwordEncoder.matches(trimmedPassword, storedHash)) {
            throw BizException.unauthorized("邮箱或密码错误");
        }

        // 角色以 DB 值为准；DB 缺失或异常时回退 admin 列表判定
        String role = user.getRole();
        // 当前用户的角色（DB 值） / current user role (DB value)
        if (role == null || role.isBlank()) {
            // DB role 缺失时回退到 admin 列表判定 / fallback to admin-list check when DB role is missing
            role = adminProperties.isAdmin(trimmedEmail) ? User.ROLE_ADMIN : User.ROLE_USER;
        }
        String token = jwtService.generate(user.getId(), role);
        // 生成 JWT（含 uid + role） / issue JWT (contains uid + role)
        return new AuthVo(user.getId(), user.getName(), user.getEmail(), token);
    }

    /**
     * Get current logged-in user from {@link SecurityContextHolder}.
     * 从 {@link SecurityContextHolder} 取当前登录用户。
     *
     * @return current user / 当前用户
     * @throws BizException 4010 not logged in; 4040 user not found
     *                      4010 未登录；4040 用户不存在
     */
    @Override
    public User getCurrentUser() {
        AuthPrincipal principal = (AuthPrincipal) SecurityContextHolder.getContext().getAuthentication();
        // 当前认证主体 / current authentication principal
        if (principal == null || principal.getUserId() == null) {
            throw BizException.unauthorized("未登录");
        }
        User user = userMapper.findById(principal.getUserId());
        // 按 token 中的 uid 查用户 / look up user by uid from token
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    private User requireByEmail(String email) {
        User user = findByEmail(email);
        if (user == null) {
            throw BizException.unauthorized("邮箱或密码错误");
        }
        return user;
    }

    private User findByEmail(String email) {
        return userMapper.findByEmail(email);
    }

    private static String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}