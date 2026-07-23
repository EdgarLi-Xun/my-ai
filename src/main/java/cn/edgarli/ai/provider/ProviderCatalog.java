package cn.edgarli.ai.provider;

import cn.edgarli.common.BizException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 厂商池：以 application.yml 的 my-ai.providers 配置为唯一数据源。
 *
 * yml 示例：
 * <pre>
 * my-ai:
 *   providers:
 *     ollama:
 *       display-name: Ollama (本地)
 *       protocol: OLLAMA
 *       default-base-url: http://localhost:11434
 *       default-model: qwen2.5:7b
 *       requires-key: false
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "my-ai.providers")
public class ProviderCatalog {

    private final Map<String, ProviderSpec> providers = new LinkedHashMap<>();

    public Map<String, ProviderSpec> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderSpec> providers) {
        this.providers.clear();
        if (providers == null) {
            return;
        }
        providers.forEach((name, spec) -> {
            if (name == null || name.isBlank() || spec == null) {
                return;
            }
            this.providers.put(name.trim().toLowerCase(Locale.ROOT), spec);
        });
    }

    public List<ProviderSpec> all() {
        List<ProviderSpec> result = new ArrayList<>();
        providers.forEach((name, spec) -> result.add(spec.withName(name)));
        return Collections.unmodifiableList(result);
    }

    public ProviderSpec require(String name) {
        if (name == null) {
            throw BizException.badRequest("provider 不能为空");
        }
        ProviderSpec spec = providers.get(name.trim().toLowerCase(Locale.ROOT));
        if (spec == null) {
            throw BizException.badRequest("不支持的 provider: " + name);
        }
        return spec.withName(name.trim().toLowerCase(Locale.ROOT));
    }
}
