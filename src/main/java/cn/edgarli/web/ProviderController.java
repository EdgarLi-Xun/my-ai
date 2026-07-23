package cn.edgarli.web;

import cn.edgarli.ai.provider.ProviderCatalog;
import cn.edgarli.ai.provider.ProviderSpec;
import cn.edgarli.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI Provider 池接口（只读）。
 */
@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderCatalog catalog;

    public ProviderController(ProviderCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public Result<List<ProviderSpec>> list() {
        return Result.success(catalog.all());
    }
}
