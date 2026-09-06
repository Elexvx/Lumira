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

    /**
     * Fenced dispatch hook. The default keeps the narrow legacy contract
     * source-compatible for owner implementations and tests that have not
     * opted into runtime fencing yet.
     */
    default int dispatchPendingEvents(RelayExecutionContext context) {
        return dispatchPendingEvents();
    }

    /** Fenced replay hook with the same compatibility behavior as dispatch. */
    default boolean replay(Long eventId, RelayExecutionContext context) {
        return replay(eventId);
    }
}
