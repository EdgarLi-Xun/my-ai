package cn.edgarli.ai;

import cn.edgarli.ai.provider.ProviderCatalog;
import cn.edgarli.ai.provider.ProviderProtocol;
import cn.edgarli.ai.provider.ProviderSpec;
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
 * 按用户 Key 动态创建 ChatClient。
 * 协议优先取 Key 上的 protocol 字段，未填则用 application.yml 中 provider 的默认协议。
 */
@Component
public class ChatClientFactory {

    private final ProviderCatalog catalog;

    public ChatClientFactory(ProviderCatalog catalog) {
        this.catalog = catalog;
    }

    public ChatClient getClient(UserApiKey key) {
        ProviderProtocol protocol = resolveProtocol(key);
        return switch (protocol) {
            case OPENAI_COMPATIBLE -> newOpenAiClient(key);
            case OLLAMA -> newOllamaClient(key);
            case ANTHROPIC -> newAnthropicClient(key);
        };
    }

    private ProviderProtocol resolveProtocol(UserApiKey key) {
        String keyProtocol = trimToNull(key.getProtocol());
        if (keyProtocol != null) {
            return ProviderProtocol.valueOf(keyProtocol);
        }
        return catalog.require(key.getProvider()).protocol();
    }

    private ChatClient newOpenAiClient(UserApiKey key) {
        ProviderSpec spec = catalog.require(key.getProvider());
        String baseUrl = trimToNull(key.getBaseUrl()) == null ? spec.defaultBaseUrl() : key.getBaseUrl();
        String model = trimToNull(key.getModelName()) == null ? spec.defaultModel() : key.getModelName();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .apiKey(key.getApiKey())
                .baseUrl(baseUrl)
                .model(model)
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .options(options)
                .build();
        return ChatClient.builder(chatModel).build();
    }

    private ChatClient newAnthropicClient(UserApiKey key) {
        ProviderSpec spec = catalog.require(key.getProvider());
        String baseUrl = trimToNull(key.getBaseUrl()) == null ? spec.defaultBaseUrl() : key.getBaseUrl();
        String model = trimToNull(key.getModelName()) == null ? spec.defaultModel() : key.getModelName();
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .apiKey(key.getApiKey())
                .baseUrl(baseUrl)
                .model(model)
                .build();
        AnthropicChatModel chatModel = AnthropicChatModel.builder()
                .options(options)
                .build();
        return ChatClient.builder(chatModel).build();
    }

    private ChatClient newOllamaClient(UserApiKey key) {
        ProviderSpec spec = catalog.require(key.getProvider());
        String baseUrl = trimToNull(key.getBaseUrl()) == null ? spec.defaultBaseUrl() : key.getBaseUrl();
        String model = trimToNull(key.getModelName()) == null ? spec.defaultModel() : key.getModelName();
        OllamaApi api = OllamaApi.builder()
                .baseUrl(baseUrl)
                .build();
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(model)
                .build();
        OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(api)
                .options(options)
                .build();
        return ChatClient.builder(chatModel).build();
    }

    private static String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
