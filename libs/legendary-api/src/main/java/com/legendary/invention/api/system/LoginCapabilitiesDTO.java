package com.legendary.invention.api.system;

public record LoginCapabilitiesDTO(
        boolean passwordLoginAvailable,
        boolean smsLoginAvailable,
        boolean emailLoginAvailable,
        boolean wechatLoginAvailable,
        boolean passkeyLoginAvailable,
        boolean passkeyPasswordlessAvailable
) {
}
