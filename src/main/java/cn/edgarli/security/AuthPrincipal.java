package cn.edgarli.security;

import cn.edgarli.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * 从 JWT 解析得到的登录用户主体，线程内传递 userId + role。
 * <p>
 * authorities 总是返回 {@code ROLE_USER}；{@code role=ADMIN} 时再加
 * {@code ROLE_ADMIN}（Spring Security 期望 {@code ROLE_xxx} 前缀，
 * {@code hasRole("ADMIN")} 会自动匹配 {@code ROLE_ADMIN}）。
 */
public class AuthPrincipal implements Authentication {

    private final Long userId;
    private final String role;
    private boolean authenticated = true;

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