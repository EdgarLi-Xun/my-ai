package cn.edgarli.ai.provider;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单个 AI 厂家的规范，来源于 application.yml 的 my-ai.providers 段。
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

    public String getName() {
        return name == null ? "" : name;
    }

    public String name() {
        return getName();
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String displayName() {
        return getDisplayName();
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public ProviderProtocol getProtocol() {
        return protocol;
    }

    public ProviderProtocol protocol() {
        return getProtocol();
    }

    public void setProtocol(ProviderProtocol protocol) {
        this.protocol = protocol;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String defaultBaseUrl() {
        return getDefaultBaseUrl();
    }

    public void setDefaultBaseUrl(String defaultBaseUrl) {
        this.defaultBaseUrl = defaultBaseUrl;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public String defaultModel() {
        return getDefaultModel();
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public boolean isRequiresKey() {
        return requiresKey;
    }

    public boolean requiresKey() {
        return isRequiresKey();
    }

    public void setRequiresKey(boolean requiresKey) {
        this.requiresKey = requiresKey;
    }

    public ProviderSpec withName(String name) {
        this.name = name;
        return this;
    }
}
