package cn.edgarli.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 管理员名单（ADR 0004 §8）。
 * Admin whitelist (ADR 0004 §8).
 * <p>
 * 配置来源：{@code my-ai.admin.emails}，env var {@code MYAI_ADMIN_EMAILS}，逗号分隔。
 * Source of configuration: {@code my-ai.admin.emails}, env var {@code MYAI_ADMIN_EMAILS}, comma-separated.
 * 大小写不敏感；匹配 {@code User.email}（trim 后）。命中用户在 register / login 时
 * 会被授予 {@code ADMIN} 角色。**无 fallback**：env var 没配且 user 表非空，
 * Case-insensitive; matches {@code User.email} (after trim). Matching users get the {@code ADMIN} role on register/login.
 * **No fallback**: if env var is unset and the user table is non-empty, there are no admins —
 * 系统没有管理员 — 查询日志的 API 任何人都调不通（被 Security 403）。
 * no one can hit the log query API (it gets a Security 403).
 */
@ConfigurationProperties(prefix = "my-ai.admin")
public class AdminProperties {

    /**
     * 管理员邮箱列表。空 = 无管理员。
     * Admin email list. Empty means no admins.
     */
    private List<String> emails = Collections.emptyList();

    public List<String> getEmails() {
        return emails;
    }

    public void setEmails(List<String> emails) {
        List<String> source = emails == null ? Collections.emptyList() : emails; // 待归一化的原始邮箱列表 / raw email list to normalize
        this.emails = source.stream()
                .filter(e -> e != null && !e.isBlank())
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .toList();
    }

    /**
     * 是否把 {@code email} 视为管理员邮箱。
     * Whether the given {@code email} is considered an admin.
     *
     * @param email 待判断的邮箱 / candidate email
     * @return 是否命中管理员名单 / true if it matches the admin list
     */
    public boolean isAdmin(String email) {
        if (email == null || emails.isEmpty()) {
            return false;
        }
        return emails.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}