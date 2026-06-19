package com.lumira.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lumira.ai.provider")
public class AiProviderProperties {

    private OpenAiCompatible openaiCompatible = new OpenAiCompatible();

    public OpenAiCompatible getOpenaiCompatible() {
        return openaiCompatible;
    }

    public void setOpenaiCompatible(OpenAiCompatible openaiCompatible) {
        this.openaiCompatible = openaiCompatible;
    }

    public static class OpenAiCompatible {
        private boolean enabled;
        private String baseUrl;
        private String apiKey;
        private String chatModel = "gpt-4o-mini";
        private String embeddingModel = "text-embedding-3-small";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

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

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public boolean configured() {
            return enabled
                    && baseUrl != null && !baseUrl.isBlank()
                    && apiKey != null && !apiKey.isBlank();
        }
    }
}
