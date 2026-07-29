package cn.edgarli.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Single user AI Key configuration.
 * 单条用户 AI Key 配置。
 * <p>
 * {@code provider}（如 openai / ollama）来源于 application.yml 的 my-ai.providers 池；
 * {@code protocol} 可选地覆盖派发协议（OPENAI_COMPATIBLE / OLLAMA / ANTHROPIC），
 * 为 null 时由 {@code provider} 默认协议决定（见 {@code ChatClientFactory.resolveProtocol}）。
 * {@code apiKey} 用 {@link ToString.Exclude} 屏蔽日志，但响应体走 {@code mask()} 仅回
 * 脱敏串与 {@code hasApiKey} 标记。
 */
@Data
@NoArgsConstructor
@Table("user_api_key")
public class UserApiKey {

    /** Key ID（主键，自增）/ key ID (PK, auto-increment) */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 所属用户 ID（FK → user.id，ON DELETE CASCADE）/ owning user ID (FK → user.id, ON DELETE CASCADE) */
    @Column("user_id")
    private Long userId;

    /** 用户给 Key 起的别名 / user-given alias for this key */
    private String name;

    /** 厂家名（与 application.yml 中 my-ai.providers.* 配对）/ provider name (must match a key in application.yml's my-ai.providers) */
    private String provider;

    /** 协议覆盖（OPENAI_COMPATIBLE / OLLAMA / ANTHROPIC）；null 时回落到 provider 默认 / protocol override (OPENAI_COMPATIBLE / OLLAMA / ANTHROPIC); null falls back to provider default */
    private String protocol;

    /** 明文 API Key（ToString.Exclude 不外露，仅响应体走 mask()）/ plaintext API key (ToString.Exclude; response payload masks via {@code mask()}) */
    @Column("api_key")
    @ToString.Exclude
    private String apiKey;

    /** 自定义 baseUrl（OpenAI 兼容模式允许覆盖，Ollama / Anthropic 多半由厂家决定）/ custom base URL (overridable in OPENAI_COMPATIBLE mode; Ollama / Anthropic typically fixed by provider) */
    @Column("base_url")
    private String baseUrl;

    /** 模型名（如 gpt-4o-mini、llama3.1:8b、claude-3-5-sonnet-20241022）/ model name (e.g. gpt-4o-mini, llama3.1:8b, claude-3-5-sonnet-20241022) */
    @Column("model_name")
    private String modelName;

    /** 是否启用；false 时不能被选为默认 Key / enabled flag; false keys cannot be set as default */
    private Boolean enabled;

    /** Key 创建时间 / creation timestamp */
    @Column("create_time")
    private LocalDateTime createTime;
}