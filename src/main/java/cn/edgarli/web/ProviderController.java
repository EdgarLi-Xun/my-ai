package cn.edgarli.web;

import cn.edgarli.service.ai.provider.ProviderCatalog;
import cn.edgarli.service.ai.provider.ProviderSpec;
import cn.edgarli.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI Provider 池接口（只读）。
 * AI provider pool endpoint (read-only).
 * <p>
 * 数据源是 {@code application.yml} 的 {@code my-ai.providers}，
 * 由 {@link ProviderCatalog} 在启动时加载、运行时不再变更。
 * Source of truth is {@code application.yml} (my-ai.providers);
 * loaded by {@link ProviderCatalog} at startup and immutable at runtime.
 */
@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderCatalog catalog;

    public ProviderController(ProviderCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * 列出所有已配置的 AI 厂家。
     * List all configured AI providers.
     *
     * @return 厂家规格列表 / list of provider specs
     */
    @GetMapping
    public Result<List<ProviderSpec>> list() {
        return Result.success(catalog.all());
    }
}
