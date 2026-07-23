package cn.edgarli.ai;

import cn.edgarli.entity.UserApiKey;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 按用户 Key 配置动态创建 ChatClient。
 */
@Component
public class ChatClientFactory {

    public ChatClient getClient(UserApiKey key) {
        AiProvider provider = AiProvider.valueOf(key.getProvider().toUpperCase(Locale.ROOT));
        return switch (provider) {
            case OPENAI -> createOpenAiClient(key);
            case OLLAMA -> createOllamaClient(key);
        };
    }

    private static ChatClient createOpenAiClient(UserApiKey key) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .apiKey(key.getApiKey())
                .baseUrl(key.getBaseUrl())
                .model(key.getModelName())
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(options)
                .build();
        return ChatClient.builder(model).build();
    }

    private static ChatClient createOllamaClient(UserApiKey key) {
        OllamaApi api = OllamaApi.builder()
                .baseUrl(key.getBaseUrl())
                .build();
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(key.getModelName())
                .build();
        OllamaChatModel model = OllamaChatModel.builder()
                .ollamaApi(api)
                .options(options)
                .build();
        return ChatClient.builder(model).build();
    }
}
