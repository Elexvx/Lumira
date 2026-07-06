package com.lumira.api.system;

public record SystemUserEmailRecipientDTO(
        Long userId,
        String userUuid,
        String username,
        String email
) {
}
