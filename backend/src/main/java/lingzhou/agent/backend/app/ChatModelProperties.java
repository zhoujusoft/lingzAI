/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package lingzhou.agent.backend.app;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.chat")
public class ChatModelProperties {

    private static final String PROVIDER_QWEN_ONLINE = "qwen-online";
    private static final String PROVIDER_VLLM = "vllm";

    private String provider = PROVIDER_QWEN_ONLINE;
    private Double temperature;
    private Integer maxTokens;
    private String systemPrompt;
    private ProviderProperties vllm = new ProviderProperties();
    private ProviderProperties qwenOnline = new ProviderProperties();

    public String getBaseUrl() {
        return getActiveProvider().getBaseUrl();
    }

    public void setBaseUrl(String baseUrl) {
        getActiveProvider().setBaseUrl(baseUrl);
    }

    public String getApiKey() {
        return getActiveProvider().getApiKey();
    }

    public void setApiKey(String apiKey) {
        getActiveProvider().setApiKey(apiKey);
    }

    public String getCompletionsPath() {
        return getActiveProvider().getCompletionsPath();
    }

    public void setCompletionsPath(String completionsPath) {
        getActiveProvider().setCompletionsPath(completionsPath);
    }

    public String getModel() {
        return getActiveProvider().getModel();
    }

    public void setModel(String model) {
        getActiveProvider().setModel(model);
    }

    public Boolean getEnableThinking() {
        return getActiveProvider().getEnableThinking();
    }

    public void setEnableThinking(Boolean enableThinking) {
        getActiveProvider().setEnableThinking(enableThinking);
    }

    public String getProvider() {
        return resolveProviderName();
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public ProviderProperties getVllm() {
        return vllm;
    }

    public void setVllm(ProviderProperties vllm) {
        this.vllm = vllm;
    }

    public ProviderProperties getQwenOnline() {
        return qwenOnline;
    }

    public void setQwenOnline(ProviderProperties qwenOnline) {
        this.qwenOnline = qwenOnline;
    }

    private ProviderProperties getActiveProvider() {
        String resolvedProvider = resolveProviderName();
        if (PROVIDER_VLLM.equalsIgnoreCase(resolvedProvider)) {
            return vllm;
        }
        if (PROVIDER_QWEN_ONLINE.equalsIgnoreCase(resolvedProvider)) {
            return qwenOnline;
        }
        throw new IllegalStateException("Unsupported app.chat.provider: " + provider);
    }

    private String resolveProviderName() {
        if (!StringUtils.hasText(provider)) {
            return PROVIDER_QWEN_ONLINE;
        }
        if (PROVIDER_VLLM.equalsIgnoreCase(provider)) {
            return PROVIDER_VLLM;
        }
        if (PROVIDER_QWEN_ONLINE.equalsIgnoreCase(provider)
                || "qwen_online".equalsIgnoreCase(provider)
                || "qwen".equalsIgnoreCase(provider)) {
            return PROVIDER_QWEN_ONLINE;
        }
        throw new IllegalStateException("Unsupported app.chat.provider: " + provider);
    }

    public static class ProviderProperties {

        private String baseUrl;
        private String apiKey;
        private String completionsPath;
        private String model;
        private Boolean enableThinking = Boolean.FALSE;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getCompletionsPath() {
            return completionsPath;
        }

        public void setCompletionsPath(String completionsPath) {
            this.completionsPath = completionsPath;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Boolean getEnableThinking() {
            return enableThinking;
        }

        public void setEnableThinking(Boolean enableThinking) {
            this.enableThinking = enableThinking;
        }
    }
}
