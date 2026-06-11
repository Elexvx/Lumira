package com.lumira.saas.infrastructure.event;

public final class PlatformEventTypes {

    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_FILE = "FILE";
    public static final String SOURCE_MESSAGE = "MESSAGE";
    public static final String SOURCE_SYSTEM = "SYSTEM";

    public static final String AGGREGATE_KNOWLEDGE_DOCUMENT = "ai.knowledge-document";
    public static final String AGGREGATE_FILE_OBJECT = "file.object";
    public static final String AGGREGATE_MESSAGE_NOTICE = "message.notice";

    public static final String AI_KNOWLEDGE_DOCUMENT_INDEXED = "AI_KNOWLEDGE_DOCUMENT_INDEXED";
    public static final String AI_KNOWLEDGE_DOCUMENT_DELETED = "AI_KNOWLEDGE_DOCUMENT_DELETED";
    public static final String FILE_OBJECT_UPLOADED = "FILE_OBJECT_UPLOADED";
    public static final String FILE_OBJECT_DELETED = "FILE_OBJECT_DELETED";
    public static final String MESSAGE_NOTICE_CREATED = "MESSAGE_NOTICE_CREATED";
    public static final String MESSAGE_NOTICE_RETRACTED = "MESSAGE_NOTICE_RETRACTED";

    private PlatformEventTypes() {
    }
}
