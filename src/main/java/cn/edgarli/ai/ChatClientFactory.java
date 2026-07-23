package cn.edgarli.ai;

import cn.edgarli.ai.provider.ProviderCatalog;
import cn.edgarli.ai.provider.ProviderProtocol;
import cn.edgarli.ai.provider.ProviderSpec;
import cn.edgarli.entity.UserApiKey;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/**
 * 按用户 Key 动态创建 ChatClient。
 * 协议由 application.yml 中的 my-ai.providers 池决定：
 * - OPENAI_COMPATIBLE：minimax / kimi / zhipu / deepseek / openai 等
 * - OLLAMA：本地 Ollama
 */
@Component
public class ChatClientFactory {

    private final ProviderCatalog catalog;

    public ChatClientFactory(ProviderCatalog catalog) {
        this.catalog = catalog;
    }

    public ChatClient getClient(UserApiKey key) {
        ProviderSpec spec = catalog.require(key.getProvider());
        return switch (spec.protocol()) {
            case OPENAI_COMPATIBLE -> newOpenAiClient(key);
            case OLLAMA -> newOllamaClient(key);
        };
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
