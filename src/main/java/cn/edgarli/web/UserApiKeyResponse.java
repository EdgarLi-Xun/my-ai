package cn.edgarli.web;

import java.time.LocalDateTime;

/**
 * 已脱敏的用户 API Key 配置响应。
 */
public record UserApiKeyResponse(
        Long id,
        Long userId,
        String name,
        String provider,
        String maskedApiKey,
        boolean hasApiKey,
        String baseUrl,
        String modelName,
        boolean enabled,
        boolean defaultKey,
        LocalDateTime createTime) {
}
