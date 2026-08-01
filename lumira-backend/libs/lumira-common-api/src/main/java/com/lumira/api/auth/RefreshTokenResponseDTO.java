package com.lumira.api.auth;

public record RefreshTokenResponseDTO(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresIn,
        Integer sessionVersion,
        String permissionsVersion
) {

    public RefreshTokenResponseDTO(String accessToken, String refreshToken, String tokenType, Long expiresIn) {
        this(accessToken, refreshToken, tokenType, expiresIn, null, null);
    }
}
