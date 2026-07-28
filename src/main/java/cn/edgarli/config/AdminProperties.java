package cn.edgarli.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 管理员名单（ADR 0004 §8）。
 * <p>
 * 配置来源：{@code my-ai.admin.emails}，env var {@code MYAI_ADMIN_EMAILS}，逗号分隔。
 * 大小写不敏感；匹配 {@code User.email}（trim 后）。命中用户在 register / login 时
 * 会被授予 {@code ADMIN} 角色。**无 fallback**：env var 没配且 user 表非空，
 * 系统没有管理员 — 查询日志的 API 任何人都调不通（被 Security 403）。
 */
@ConfigurationProperties(prefix = "my-ai.admin")
public class AdminProperties {

    /**
     * 管理员邮箱列表。空 = 无管理员。
     */
    private List<String> emails = Collections.emptyList();

    public List<String> getEmails() {
        return emails;
    }

    public void setEmails(List<String> emails) {
        this.emails = emails == null ? Collections.emptyList()
                : emails.stream()
                .filter(e -> e != null && !e.isBlank())
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .toList();
    }

    /**
     * 是否把 {@code email} 视为管理员邮箱。
     */
    public boolean isAdmin(String email) {
        if (email == null || emails.isEmpty()) {
            return false;
        }
        return emails.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}