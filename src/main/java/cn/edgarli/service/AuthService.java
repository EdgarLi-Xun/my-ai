package cn.edgarli.service;

import cn.edgarli.common.BizException;
import cn.edgarli.config.AdminProperties;
import cn.edgarli.entity.User;
import cn.edgarli.mapper.UserMapper;
import cn.edgarli.security.AuthPrincipal;
import cn.edgarli.security.JwtService;
import cn.edgarli.web.AuthResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户认证：注册、登录。
 * <p>
 * RBAC 角色（ADR 0004）：register / login 时按 {@link AdminProperties#isAdmin(String)}
 * 决定 {@code User.role}。命中 admin email 列表 → {@link User#ROLE_ADMIN}，
 * 否则 {@link User#ROLE_USER}。
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    public AuthService(UserMapper userMapper,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       AdminProperties adminProperties) {
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.adminProperties = adminProperties;
    }

    @Transactional
    public AuthResponse register(String name, String email, String password) {
        String trimmedName = trim(name);
        String trimmedEmail = trim(email);
        String trimmedPassword = trim(password);

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
        if (existing != null) {
            throw BizException.conflict("该邮箱已被注册");
        }

        User user = new User();
        user.setName(trimmedName);
        user.setEmail(trimmedEmail);
        user.setPasswordHash(passwordEncoder.encode(trimmedPassword));
        user.setRole(adminProperties.isAdmin(trimmedEmail) ? User.ROLE_ADMIN : User.ROLE_USER);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);

        String token = jwtService.generate(user.getId(), user.getRole());
        return new AuthResponse(user.getId(), user.getName(), user.getEmail(), token);
    }

    public AuthResponse login(String email, String password) {
        String trimmedEmail = trim(email);
        String trimmedPassword = trim(password);

        if (trimmedEmail == null) {
            throw BizException.badRequest("邮箱不能为空");
        }
        if (trimmedPassword == null) {
            throw BizException.badRequest("密码不能为空");
        }

        User user = requireByEmail(trimmedEmail);
        String storedHash = userMapper.findPasswordHashById(user.getId());
        if (storedHash == null || !passwordEncoder.matches(trimmedPassword, storedHash)) {
            throw BizException.unauthorized("邮箱或密码错误");
        }

        // 角色以 DB 值为准；DB 缺失或异常时回退 admin 列表判定（覆盖历史 user role=NULL）
        String role = user.getRole();
        if (role == null || role.isBlank()) {
            role = adminProperties.isAdmin(trimmedEmail) ? User.ROLE_ADMIN : User.ROLE_USER;
        }
        String token = jwtService.generate(user.getId(), role);
        return new AuthResponse(user.getId(), user.getName(), user.getEmail(), token);
    }

    public User getCurrentUser() {
        AuthPrincipal principal = (AuthPrincipal) SecurityContextHolder.getContext().getAuthentication();
        if (principal == null || principal.getUserId() == null) {
            throw BizException.unauthorized("未登录");
        }
        User user = userMapper.findById(principal.getUserId());
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
