package cn.edgarli.service.impl;

import cn.edgarli.common.BizException;
import cn.edgarli.entity.User;
import cn.edgarli.service.UserService;
import cn.edgarli.infrastructure.security.AuthPrincipal;
import cn.edgarli.mapper.UserMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Default implementation of {@link UserService}.
 * {@link UserService} 默认实现。
 *
 * @author MyAi
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
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

    /**
     * Look up user by id.
     * 按 id 查询用户。
     *
     * @param id user id / 用户 ID
     * @return user entity / 用户实体
     * @throws BizException 4040 user not found / 4040 用户不存在
     */
    @Override
    public User getById(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    /**
     * Create a new user. Single insert; no transaction needed.
     * 创建新用户。单条 insert，无需事务。
     *
     * @param name user name (required, non-empty after trim) / 用户名（必填，trim 后非空）
     * @param email email (optional) / 邮箱（可空）
     * @return newly created user / 新创建的用户
     * @throws BizException 4000 name is empty / 4000 用户名为空
     */
    @Override
    public User create(String name, String email) {
        String trimmedName = trimToNull(name);
        // trim 后的用户名 / trimmed user name
        if (trimmedName == null) {
            throw BizException.badRequest("用户名不能为空");
        }
        String trimmedEmail = trimToNull(email);
        // trim 后的邮箱（可空） / trimmed email (optional)
        User user = new User();
        // 新建用户实体 / new user entity
        user.setName(trimmedName);
        user.setEmail(trimmedEmail);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    /**
     * Delete a user (DB cascades keys/conversations/messages/ai_call_log rows).
     * 删除用户（DB 级联删 Key / 对话 / 消息 / ai_call_log）。
     *
     * @param id user id / 用户 ID
     * @throws BizException 4040 user not found / 4040 用户不存在
     */
    @Override
    public void delete(Long id) {
        if (userMapper.deleteById(id) == 0) {
            throw BizException.notFound("用户不存在");
        }
    }

    private static String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}