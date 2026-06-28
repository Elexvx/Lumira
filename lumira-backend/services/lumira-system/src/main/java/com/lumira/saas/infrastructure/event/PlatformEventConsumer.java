package com.lumira.saas.infrastructure.event;

public interface PlatformEventConsumer {

    boolean supports(PlatformEventOutboxEntity event);

    void consume(PlatformEventOutboxEntity event);
}
