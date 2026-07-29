package cn.edgarli.service.ai;

import cn.edgarli.service.ai.provider.ProviderCatalog;
import cn.edgarli.service.ai.provider.ProviderProtocol;
import cn.edgarli.service.ai.provider.ProviderSpec;
import cn.edgarli.entity.UserApiKey;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/**
 * Build ChatClient dynamically per user Key.
 * 按用户 Key 动态创建 ChatClient。
 * <p>
 * 协议优先取 Key 上的 protocol 字段，未填则用 application.yml 中 provider 的默认协议。
 * Protocol resolution: prefer the {@code protocol} field on the Key; when blank,
 * fall back to the provider's default protocol from application.yml.
 * <p>
 * 每次新建 ChatClient，不缓存——确保 Key / baseUrl / model 变更后下一次请求立即生效。
 * A new ChatClient is built on every call (no caching) so that Key / baseUrl / model
 * changes take effect on the next request.
 */
@Component
public class ChatClientFactory {

    private final ProviderCatalog catalog;

    public ChatClientFactory(ProviderCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * 按 Key 派发到对应协议分支，构建新的 ChatClient。
     * Dispatch by Key to the matching protocol branch and build a new ChatClient.
     *
     * @param key 用户 API Key配置 / user API Key configuration
     * @return 新建的 ChatClient（含 advisor 链）/ newly built ChatClient with advisor chain
     */
    public ChatClient getClient(UserApiKey key) {
        ProviderProtocol protocol = resolveProtocol(key); // 解析后的协议 / resolved protocol
        return switch (protocol) {
            // OPENAI 兼容分支 / OpenAI-compatible branch
            case OPENAI_COMPATIBLE -> newOpenAiClient(key);
            // Ollama 本地分支 / Ollama local branch
            case OLLAMA -> newOllamaClient(key);
            // Anthropic 分支 / Anthropic branch
            case ANTHROPIC -> newAnthropicClient(key);
        };
    }

    /**
     * 解析协议：Key 上的 protocol 字段优先；未填回落到 catalog 中 provider 的默认协议。
     * Resolve protocol: prefer the {@code protocol} field on the Key; otherwise fall
     * back to the provider's default protocol in the catalog.
     *
     * @param key 用户 API Key配置 / user API Key configuration
     * @return 协议枚举 / the protocol enum
     */
    private ProviderProtocol resolveProtocol(UserApiKey key) {
        String keyProtocol = trimToNull(key.getProtocol()); // Key 上的协议字符串 / protocol string on the Key
        if (keyProtocol != null) {
            return ProviderProtocol.valueOf(keyProtocol);
        }
        return catalog.require(key.getProvider()).protocol();
    }

    /**
     * 构建 OpenAI 兼容协议的 ChatClient（baseUrl / model 缺省回落到 catalog 默认值）。
     * Build a ChatClient for the OpenAI-compatible protocol (baseUrl / model fall back to catalog defaults).
     *
     * @param key 用户 API Key配置 / user API Key configuration
     * @return 新建的 ChatClient / newly built ChatClient
     */
    private ChatClient newOpenAiClient(UserApiKey key) {
        ProviderSpec spec = catalog.require(key.getProvider()); // yml 中的 provider 规范 / provider spec from yml
        String baseUrl = trimToNull(key.getBaseUrl()) == null ? spec.defaultBaseUrl() : key.getBaseUrl(); // baseUrl（Key 优先）/ baseUrl (Key wins)
        String model = trimToNull(key.getModelName()) == null ? spec.defaultModel() : key.getModelName(); // model 名（Key 优先）/ model name (Key wins)
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .apiKey(key.getApiKey())
                .baseUrl(baseUrl)
                .model(model)
                .build(); // OpenAI 选项 / OpenAI options
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .options(options)
                .build(); // OpenAI 兼容模型实例 / OpenAI-compatible model instance
        ChatClient client = ChatClient.builder(chatModel).build(); // 客户端（含 advisor 链）/ client with advisor chain
        return client;
    }

    /**
     * 构建 Anthropic 协议的 ChatClient（baseUrl / model 缺省回落到 catalog 默认值）。
     * Build a ChatClient for the Anthropic protocol (baseUrl / model fall back to catalog defaults).
     *
     * @param key 用户 API Key配置 / user API Key configuration
     * @return 新建的 ChatClient / newly built ChatClient
     */
    private ChatClient newAnthropicClient(UserApiKey key) {
        ProviderSpec spec = catalog.require(key.getProvider()); // yml 中的 provider 规范 / provider spec from yml
        String baseUrl = trimToNull(key.getBaseUrl()) == null ? spec.defaultBaseUrl() : key.getBaseUrl(); // baseUrl（Key 优先）/ baseUrl (Key wins)
        String model = trimToNull(key.getModelName()) == null ? spec.defaultModel() : key.getModelName(); // model 名（Key 优先）/ model name (Key wins)
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .apiKey(key.getApiKey())
                .baseUrl(baseUrl)
                .model(model)
                .build(); // Anthropic 选项 / Anthropic options
        AnthropicChatModel chatModel = AnthropicChatModel.builder()
                .options(options)
                .build(); // Anthropic 模型实例 / Anthropic model instance
        ChatClient client = ChatClient.builder(chatModel).build(); // 客户端（含 advisor 链）/ client with advisor chain
        return client;
    }

    /**
     * 构建 Ollama 本地协议的 ChatClient（不走 API Key，仅 baseUrl / model）。
     * Build a ChatClient for the Ollama local protocol (no API Key, only baseUrl / model).
     *
     * @param key 用户 API Key配置 / user API Key configuration
     * @return 新建的 ChatClient / newly built ChatClient
     */
    private ChatClient newOllamaClient(UserApiKey key) {
        ProviderSpec spec = catalog.require(key.getProvider()); // yml 中的 provider 规范 / provider spec from yml
        String baseUrl = trimToNull(key.getBaseUrl()) == null ? spec.defaultBaseUrl() : key.getBaseUrl(); // baseUrl（Key 优先）/ baseUrl (Key wins)
        String model = trimToNull(key.getModelName()) == null ? spec.defaultModel() : key.getModelName(); // model 名（Key 优先）/ model name (Key wins)
        OllamaApi api = OllamaApi.builder()
                .baseUrl(baseUrl)
                .build(); // Ollama API 客户端 / Ollama API client
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(model)
                .build(); // Ollama 选项 / Ollama options
        OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(api)
                .options(options)
                .build(); // Ollama 模型实例 / Ollama model instance
        ChatClient client = ChatClient.builder(chatModel).build(); // 客户端（含 advisor 链）/ client with advisor chain
        return client;
    }

    /**
     * 字符串空值规整：null / blank 返回 null，否则 trim。
     * Normalize empty strings: returns null for null / blank, otherwise trimmed value.
     *
     * @param value 原始字符串 / raw string
     * @return 规整后的字符串 / normalized string
     */
    private static String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
