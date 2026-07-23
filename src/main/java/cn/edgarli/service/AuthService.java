package cn.edgarli.service;

import cn.edgarli.common.BizException;
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
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserMapper userMapper, JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
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
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);

        String token = jwtService.generate(user.getId());
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

        String token = jwtService.generate(user.getId());
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
