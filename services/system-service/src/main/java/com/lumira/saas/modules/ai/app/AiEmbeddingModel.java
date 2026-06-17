package com.lumira.saas.modules.ai.app;

public interface AiEmbeddingModel {

    String modelName();

    AiEmbeddingVector embed(String text);
}
