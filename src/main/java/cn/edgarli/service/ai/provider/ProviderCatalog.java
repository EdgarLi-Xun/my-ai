package cn.edgarli.service.ai.provider;

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
 * AI provider pool, sourced solely from {@code my-ai.providers} in application.yml.
 * AI 厂商池：以 application.yml 的 {@code my-ai.providers} 配置为唯一数据源。
 * <p>
 * yml 示例 / yml example:
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
 * <p>
 * 名称以 trim + toLowerCase 归一化为 key，避免大小写差异导致同一 provider 出现两条。
 * Names are normalized (trim + toLowerCase) so case variants map to a single entry.
 */
@Component
@ConfigurationProperties(prefix = "my-ai")
public class ProviderCatalog {

    private final Map<String, ProviderSpec> providers = new LinkedHashMap<>(); // 名称（归一化）→ ProviderSpec / name (normalized) → ProviderSpec

    /**
     * 返回底层 Map（Spring Boot 配置绑定用，调用方一般改用 {@link #all()}）。
     * Return the underlying map (used by Spring Boot configuration binding; callers should prefer {@link #all()}).
     *
     * @return 当前已注册的 provider 映射 / currently registered provider map
     */
    public Map<String, ProviderSpec> getProviders() {
        return providers;
    }

    /**
     * Spring Boot 配置绑定入口：清空旧表，按归一化名称写入。
     * Spring Boot configuration binding entry: clear the old map and write entries under normalized names.
     *
     * @param providers 来自 yml 的 provider 映射 / provider map from yml
     */
    public void setProviders(Map<String, ProviderSpec> providers) {
        this.providers.clear();
        if (providers == null) {
            return;
        }
        providers.forEach((name, spec) -> {
            if (name == null || name.isBlank() || spec == null) {
                return; // 跳过空白名 / null spec
            }
            this.providers.put(name.trim().toLowerCase(Locale.ROOT), spec);
        });
    }

    /**
     * 返回所有 provider 的不可变列表（每条已注入 name）。
     * Return an immutable list of all providers (each entry has its name injected).
     *
     * @return provider 列表（不可变）/ immutable list of providers
     */
    public List<ProviderSpec> all() {
        List<ProviderSpec> result = new ArrayList<>();
        providers.forEach((name, spec) -> result.add(spec.withName(name)));
        return Collections.unmodifiableList(result);
    }

    /**
     * 按名称查找 provider；找不到抛 BizException（4000，badRequest）。
     * Lookup a provider by name; throws BizException (4000, badRequest) when not found.
     *
     * @param name provider 名称（不区分大小写，会 trim + toLowerCase）/ provider name (case-insensitive, trimmed and lowercased)
     * @return 对应的 ProviderSpec（已注入 name）/ the matching ProviderSpec (with name injected)
     */
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
