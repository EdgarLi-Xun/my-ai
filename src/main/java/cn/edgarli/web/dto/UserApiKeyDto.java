package cn.edgarli.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Create or update user API Key configuration request body.
 * 新增或更新用户 API Key 配置的请求体。
 */
@Data
@NoArgsConstructor
@ToString
public class UserApiKeyDto {

    /** Key 别名（用户自定义）/ key alias (user-defined) */
    private String name;

    /** 厂家名（与 application.yml 中 my-ai.providers.* 配对）/ provider name (must match a key in application.yml's my-ai.providers) */
    private String provider;

    /** 协议覆盖（OPENAI_COMPATIBLE / OLLAMA / ANTHROPIC）；可空，回落到 provider 默认 / protocol override (OPENAI_COMPATIBLE / OLLAMA / ANTHROPIC); nullable, falls back to provider default */
    private String protocol;

    /** 明文 API Key（编辑时空字符串 = 保留原值；ToString.Exclude 不外露）/ plaintext API key (empty string on update means "keep existing"; ToString.Exclude) */
    @ToString.Exclude
    private String apiKey;

    /** 自定义 baseUrl（仅 OPENAI_COMPATIBLE 有效）/ custom base URL (effective only in OPENAI_COMPATIBLE mode) */
    private String baseUrl;

    /** 模型名（如 gpt-4o-mini、llama3.1:8b、claude-3-5-sonnet-20241022）/ model name */
    private String modelName;

    /** 是否启用；false 时不能被设为默认 Key / enabled flag; false keys cannot be set as default */
    private Boolean enabled;
}