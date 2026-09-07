package com.lumira.api.notification;

/** Port used by the async runtime to request a durable owner-side notification. */
public interface NotificationCommandPort {

    /**
     * Publishes the command to the message owner.
     *
     * @return {@code true} when this event was applied for the first time;
     *         {@code false} when the owner's receipt already exists
     */
    boolean publish(NotificationCommand command);
}
