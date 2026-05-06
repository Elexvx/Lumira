package com.legendary.invention.api.auth;

public record RefreshTokenResponseDTO(String accessToken, String refreshToken, String tokenType, Long expiresIn) {
}
