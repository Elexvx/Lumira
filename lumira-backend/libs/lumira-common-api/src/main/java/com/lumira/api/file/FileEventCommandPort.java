package com.lumira.api.file;

/** Async-to-file owner command boundary for File lifecycle events. */
public interface FileEventCommandPort {

    /**
     * Applies the event at the File owner.
     *
     * @return {@code true} when the event created the receipt and projection;
     *         {@code false} when the owner already completed the same event
     */
    boolean handleUploaded(FileObjectUploadedEventCommand command);
}
