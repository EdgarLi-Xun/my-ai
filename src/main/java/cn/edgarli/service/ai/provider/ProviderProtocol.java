package cn.edgarli.service.ai.provider;

/**
 * Protocol type used to dispatch ChatClient branches.
 * 协议类型，仅描述 ChatClient 派发时使用的分支协议。
 * <p>
 * Provider 名（字符串）由 application.yml 的 my-ai.providers 池维护，
 * 此枚举只决定 Spring AI 哪条 ChatClient 路由。
 * Provider names (strings) live in the {@code my-ai.providers} pool in
 * application.yml; this enum only decides which Spring AI ChatClient route to take.
 */
public enum ProviderProtocol {
    /** OpenAI 兼容协议（含 OpenAI 官方与第三方 OpenAI-兼容服务）/ OpenAI-compatible protocol (incl. OpenAI official and OpenAI-compatible third parties) */
    OPENAI_COMPATIBLE,
    /** Ollama 本地协议 / Ollama local protocol */
    OLLAMA,
    /** Anthropic 协议（Claude 系列）/ Anthropic protocol (Claude family) */
    ANTHROPIC
}
