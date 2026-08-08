package com.lumira.api.event;

/**
 * Narrow runtime contract for dispatching an owner's durable outbox.
 *
 * <p>The owner retains the outbox SQL, lease, retry, and replay semantics.
 * Async runtimes only schedule this surface and must not import an owner's
 * control-plane application graph.</p>
 */
public interface OwnerOutboxRelayPort {

    String owner();

    int dispatchPendingEvents();

    boolean replay(Long eventId);
}
