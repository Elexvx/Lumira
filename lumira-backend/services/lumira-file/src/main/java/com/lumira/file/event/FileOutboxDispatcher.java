package com.lumira.file.event;

public interface FileOutboxDispatcher {

    void dispatch(PlatformEventOutboxEntity row);
}
