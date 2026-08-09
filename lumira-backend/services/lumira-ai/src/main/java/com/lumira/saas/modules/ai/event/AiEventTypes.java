package com.lumira.saas.modules.ai.event;

/** AI-owned integration event names and aggregate identifiers. */
public final class AiEventTypes {

    public static final String SOURCE_AI = "AI";
    public static final String AGGREGATE_KNOWLEDGE_DOCUMENT = "ai.knowledge-document";
    public static final String KNOWLEDGE_DOCUMENT_INDEXED = "AI_KNOWLEDGE_DOCUMENT_INDEXED";
    public static final String KNOWLEDGE_DOCUMENT_DELETED = "AI_KNOWLEDGE_DOCUMENT_DELETED";

    private AiEventTypes() {
    }
}
