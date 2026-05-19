package com.legendary.invention.saas.infrastructure.event;

public interface PlatformEventDispatcher {

    void dispatch(PlatformEventOutboxEntity event);
}
