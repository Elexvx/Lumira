package com.lumira.api.auth;

import java.util.Map;

public record PasskeyOptionsDTO(
        String challengeId,
        Map<String, Object> publicKey
) {
}
