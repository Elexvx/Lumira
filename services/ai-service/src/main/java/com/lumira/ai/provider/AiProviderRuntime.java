package com.lumira.ai.provider;

import com.lumira.ai.vo.AiKnowledgeReferenceVO;

import java.util.List;

public interface AiProviderRuntime {

    ChatCompletion complete(ChatPrompt prompt);

    EmbeddingVector embed(String text);

    ProviderStatus status();

    record ChatPrompt(String userMessage, List<AiKnowledgeReferenceVO> references) {
    }

    record ChatCompletion(String text, String provider, String model, boolean remote, boolean degraded) {
    }

    record EmbeddingVector(String model, List<Double> values, boolean remote, boolean degraded) {
        public int dimension() {
            return values == null ? 0 : values.size();
        }
    }

    record ProviderStatus(String provider, String chatModel, String embeddingModel, boolean remoteConfigured, boolean degraded) {
    }
}
