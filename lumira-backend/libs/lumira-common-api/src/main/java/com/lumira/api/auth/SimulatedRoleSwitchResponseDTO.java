package com.lumira.api.auth;

public record SimulatedRoleSwitchResponseDTO(
        CurrentUserDTO currentUser,
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresIn
) {
}
