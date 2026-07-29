package cn.edgarli.infrastructure.security;

import cn.edgarli.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * 从 JWT 解析得到的登录用户主体，线程内传递 userId + role。
 * Logged-in principal parsed from JWT; carries userId + role per thread.
 * <p>
 * authorities 总是返回 {@code ROLE_USER}；{@code role=ADMIN} 时再加
 * authorities always returns {@code ROLE_USER}; adds {@code ROLE_ADMIN} when {@code role=ADMIN}
 * {@code ROLE_ADMIN}（Spring Security 期望 {@code ROLE_xxx} 前缀，
 * (Spring Security expects the {@code ROLE_xxx} prefix,
 * {@code hasRole("ADMIN")} 会自动匹配 {@code ROLE_ADMIN}）。
 * so {@code hasRole("ADMIN")} matches {@code ROLE_ADMIN} automatically).
 */
public class AuthPrincipal implements Authentication {

    private final Long userId;
    private final String role;
    private boolean authenticated = true;

    /**
     * 构造已认证的主体。
     * Construct an authenticated principal.
     *
     * @param userId 用户 id / user id
     * @param role 角色（USER / ADMIN），可空 / role (USER / ADMIN), nullable
     */
    public AuthPrincipal(Long userId, String role) {
        this.userId = userId;
        this.role = role == null ? User.ROLE_USER : role;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    /**
     * 返回 Spring Security 权限集合。普通用户只有 ROLE_USER，管理员额外加 ROLE_ADMIN。
     * Return Spring Security authorities. Normal users get ROLE_USER; admins also get ROLE_ADMIN.
     *
     * @return 权限集合 / authority collection
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (User.ROLE_ADMIN.equals(role)) {
            return List.of(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return userId == null ? "" : userId.toString();
    }
}