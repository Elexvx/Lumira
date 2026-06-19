package com.lumira.file.event;

public final class FilePlatformEventTypes {

    public static final String SOURCE_FILE = "FILE";
    public static final String AGGREGATE_FILE_OBJECT = "file.object";
    public static final String AGGREGATE_FILE_PROCESSING_TASK = "file.processing_task";
    public static final String FILE_OBJECT_UPLOADED = "FILE_OBJECT_UPLOADED";
    public static final String FILE_OBJECT_DELETED = "FILE_OBJECT_DELETED";
    public static final String FILE_PROCESSING_TASK_REQUESTED = "FileProcessingTaskRequested";

    private FilePlatformEventTypes() {
    }
}
