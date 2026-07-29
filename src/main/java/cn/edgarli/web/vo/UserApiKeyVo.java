package cn.edgarli.web.vo;

import java.time.LocalDateTime;

/**
 * Masked user API Key configuration response.
 * 已脱敏的用户 API Key 配置响应。
 *
 * @param id           Key ID / key ID
 * @param userId       所属用户 ID / owning user ID
 * @param name         Key 别名 / key alias
 * @param provider     厂家名 / provider name
 * @param protocol     实际协议（OPENAI_COMPATIBLE / OLLAMA / ANTHROPIC）/ effective protocol
 * @param maskedApiKey 脱敏串（如 {@code ****abcd}）/ masked string (e.g. {@code ****abcd})
 * @param hasApiKey    是否配置了 Key（false 时表示未填；编辑空字符串语义见 UserApiKeyDto）/ whether a key is configured (false means never set)
 * @param baseUrl      实际 baseUrl / effective base URL
 * @param modelName    实际模型名 / effective model name
 * @param enabled      是否启用 / enabled flag
 * @param defaultKey   是否为该用户的默认 Key / whether this is the user's default key
 * @param createTime   Key 创建时间 / creation timestamp
 */
public record UserApiKeyVo(
        Long id,
        Long userId,
        String name,
        String provider,
        String protocol,
        String maskedApiKey,
        boolean hasApiKey,
        String baseUrl,
        String modelName,
        boolean enabled,
        boolean defaultKey,
        LocalDateTime createTime) {
}