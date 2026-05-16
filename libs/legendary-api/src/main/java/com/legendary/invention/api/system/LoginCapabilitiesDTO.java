package com.legendary.invention.api.system;

import java.util.List;

public record LoginCapabilitiesDTO(
        boolean passwordLoginAvailable,
        boolean smsLoginAvailable,
        boolean emailLoginAvailable,
        boolean wechatLoginAvailable,
        boolean passkeyLoginAvailable,
        boolean passkeyPasswordlessAvailable,
        List<String> loginModeOrder
) {
}
