package com.lumira.message.app;

/**
 * Trusted command produced by an internal integration-event consumer.
 */
public record SystemEventMessageCommand(
        Long operatorUserId,
        String operatorUserUuid,
        Long targetUserId,
        String targetUserUuid,
        String title,
        String content
) {
}
