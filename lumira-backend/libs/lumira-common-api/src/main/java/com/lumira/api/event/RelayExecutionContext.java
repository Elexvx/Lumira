package com.lumira.api.event;

import java.util.Objects;

/**
 * Fencing context carried from the async runtime to an owner outbox.
 *
 * <p>The owner must treat the generation and token as a short-lived publish
 * authority. They are deliberately kept out of the public relay port's
 * existing methods so callers that do not participate in runtime fencing can
 * continue to compile while the production async path opts in explicitly.</p>
 */
public record RelayExecutionContext(
        String owner,
        long generation,
        String fenceToken,
        String holder
) {
    public RelayExecutionContext {
        owner = requireText(owner, "owner");
        if (generation <= 0L) {
            throw new IllegalArgumentException("generation must be positive");
        }
        fenceToken = requireText(fenceToken, "fenceToken");
        if (fenceToken.length() < 24) {
            throw new IllegalArgumentException("fenceToken must contain at least 24 characters");
        }
        holder = requireText(holder, "holder");
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name + " is required").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return normalized;
    }
}
