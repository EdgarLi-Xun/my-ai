package cn.edgarli.web;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 新增或更新用户 API Key 配置的请求体。
 */
@Data
@NoArgsConstructor
@ToString
public class UserApiKeyRequest {

    private String name;

    private String provider;

    @ToString.Exclude
    private String apiKey;

    private String baseUrl;

    private String modelName;

    private Boolean enabled;
}
