package com.lumira.saas.modules.ai.app;

public record AiEmbeddingVector(
        String model,
        double[] values
) {
    public int dimensions() {
        return values == null ? 0 : values.length;
    }
}
