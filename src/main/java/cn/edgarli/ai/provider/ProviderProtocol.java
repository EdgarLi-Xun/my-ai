package cn.edgarli.ai.provider;

/**
 * 协议类型，仅描述 ChatClient 派发时使用的分支协议。
 * Provider 名（字符串）由 application.yml 的 my-ai.providers 池维护，
 * 此枚举只决定 Spring AI 哪条 ChatClient 路由。
 */
public enum ProviderProtocol {
    OPENAI_COMPATIBLE,
    OLLAMA,
    ANTHROPIC
}
