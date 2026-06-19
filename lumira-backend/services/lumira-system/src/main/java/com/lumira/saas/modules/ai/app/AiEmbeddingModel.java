package com.lumira.saas.modules.ai.app;

public interface AiEmbeddingModel {

    String modelName();

    AiEmbeddingVector embed(String text);

    default java.util.List<AiEmbeddingVector> embedBatch(java.util.List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<AiEmbeddingVector> vectors = new java.util.ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return vectors;
    }
}
