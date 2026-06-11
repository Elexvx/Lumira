package com.lumira.saas.infrastructure.event;

public interface PlatformEventDispatcher {

    void dispatch(PlatformEventOutboxEntity event);
}
