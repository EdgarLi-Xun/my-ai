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

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("user_id")
    private Long userId;

    private String name;

    private String provider;

    private String protocol;

    @Column("api_key")
    @ToString.Exclude
    private String apiKey;

    @Column("base_url")
    private String baseUrl;

    @Column("model_name")
    private String modelName;

    private Boolean enabled;

    @Column("create_time")
    private LocalDateTime createTime;
}
