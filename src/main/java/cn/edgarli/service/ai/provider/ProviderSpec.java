package cn.edgarli.service.ai.provider;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Spec for a single AI provider, sourced from {@code my-ai.providers} in application.yml.
 * 单个 AI 厂家的规范，来源于 application.yml 的 {@code my-ai.providers} 段。
 * <p>
 * 同时暴露 getter / setter（Java Bean 规范）与 record 风格的 xxx() 方法（供业务层以 record 风格访问）。
 * Exposes both getters / setters (Java Bean convention) and record-style xxx() accessors
 * so business code can use either style.
 */
public class ProviderSpec {

    private String name;

    @JsonProperty("displayName")
    private String displayName;

    private ProviderProtocol protocol;

    @JsonProperty("defaultBaseUrl")
    private String defaultBaseUrl;

    @JsonProperty("defaultModel")
    private String defaultModel;

    @JsonProperty("requiresKey")
    private boolean requiresKey;

    /**
     * 获取 provider 名称（null 时返回空串，避免 NPE）。
     * Get the provider name (returns empty string when null to avoid NPE).
     *
     * @return provider 名 / provider name
     */
    public String getName() {
        return name == null ? "" : name;
    }

    /**
     * record 风格的 name() 访问器，等价于 {@link #getName()}。
     * Record-style {@code name()} accessor equivalent to {@link #getName()}.
     *
     * @return provider 名 / provider name
     */
    public String name() {
        return getName();
    }

    /**
     * 设置 provider 名称。
     * Set the provider name.
     *
     * @param name provider 名 / provider name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取显示名（用于前端 UI）。
     * Get the display name (used by the frontend UI).
     *
     * @return 显示名 / display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * record 风格的 displayName() 访问器，等价于 {@link #getDisplayName()}。
     * Record-style {@code displayName()} accessor equivalent to {@link #getDisplayName()}.
     *
     * @return 显示名 / display name
     */
    public String displayName() {
        return getDisplayName();
    }

    /**
     * 设置显示名。
     * Set the display name.
     *
     * @param displayName 显示名 / display name
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取协议枚举（决定 ChatClient 派发分支）。
     * Get the protocol enum (decides ChatClient dispatch branch).
     *
     * @return 协议枚举 / protocol enum
     */
    public ProviderProtocol getProtocol() {
        return protocol;
    }

    /**
     * record 风格的 protocol() 访问器，等价于 {@link #getProtocol()}。
     * Record-style {@code protocol()} accessor equivalent to {@link #getProtocol()}.
     *
     * @return 协议枚举 / protocol enum
     */
    public ProviderProtocol protocol() {
        return getProtocol();
    }

    /**
     * 设置协议枚举。
     * Set the protocol enum.
     *
     * @param protocol 协议枚举 / protocol enum
     */
    public void setProtocol(ProviderProtocol protocol) {
        this.protocol = protocol;
    }

    /**
     * 获取默认 baseUrl（用户未填时回落）。
     * Get the default baseUrl (used when the user leaves it blank).
     *
     * @return 默认 baseUrl / default baseUrl
     */
    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    /**
     * record 风格的 defaultBaseUrl() 访问器，等价于 {@link #getDefaultBaseUrl()}。
     * Record-style {@code defaultBaseUrl()} accessor equivalent to {@link #getDefaultBaseUrl()}.
     *
     * @return 默认 baseUrl / default baseUrl
     */
    public String defaultBaseUrl() {
        return getDefaultBaseUrl();
    }

    /**
     * 设置默认 baseUrl。
     * Set the default baseUrl.
     *
     * @param defaultBaseUrl 默认 baseUrl / default baseUrl
     */
    public void setDefaultBaseUrl(String defaultBaseUrl) {
        this.defaultBaseUrl = defaultBaseUrl;
    }

    /**
     * 获取默认 model 名（用户未填时回落）。
     * Get the default model name (used when the user leaves it blank).
     *
     * @return 默认 model 名 / default model name
     */
    public String getDefaultModel() {
        return defaultModel;
    }

    /**
     * record 风格的 defaultModel() 访问器，等价于 {@link #getDefaultModel()}。
     * Record-style {@code defaultModel()} accessor equivalent to {@link #getDefaultModel()}.
     *
     * @return 默认 model 名 / default model name
     */
    public String defaultModel() {
        return getDefaultModel();
    }

    /**
     * 设置默认 model 名。
     * Set the default model name.
     *
     * @param defaultModel 默认 model 名 / default model name
     */
    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    /**
     * 是否要求用户提供 API Key（如 Ollama 本地为 false）。
     * Whether an API Key is required (e.g. false for local Ollama).
     *
     * @return 是否要求 Key / whether a Key is required
     */
    public boolean isRequiresKey() {
        return requiresKey;
    }

    /**
     * record 风格的 requiresKey() 访问器，等价于 {@link #isRequiresKey()}。
     * Record-style {@code requiresKey()} accessor equivalent to {@link #isRequiresKey()}.
     *
     * @return 是否要求 Key / whether a Key is required
     */
    public boolean requiresKey() {
        return isRequiresKey();
    }

    /**
     * 设置是否要求 Key。
     * Set whether a Key is required.
     *
     * @param requiresKey 是否要求 Key / whether a Key is required
     */
    public void setRequiresKey(boolean requiresKey) {
        this.requiresKey = requiresKey;
    }

    /**
     * 流式注入 name（catalog 在 all() / require() 处复用同一实例）。
     * Fluent name setter (catalog reuses the same instance in {@code all()} / {@code require()}).
     *
     * @param name provider 名 / provider name
     * @return 当前实例（支持链式调用）/ current instance (chainable)
     */
    public ProviderSpec withName(String name) {
        this.name = name;
        return this;
    }
}
