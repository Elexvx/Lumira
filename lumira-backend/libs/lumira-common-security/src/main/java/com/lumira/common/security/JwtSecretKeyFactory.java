package com.lumira.common.security;

import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class JwtSecretKeyFactory {

    private static final int MINIMUM_HMAC_KEY_BYTES = 32;

    private JwtSecretKeyFactory() {
    }

    public static SecretKey createHmacKey(String jwtSecret) {
        byte[] secretBytes = decodeSecret(jwtSecret);
        if (secretBytes.length < MINIMUM_HMAC_KEY_BYTES) {
            throw new IllegalStateException("JWT密钥长度不足");
        }
        return Keys.hmacShaKeyFor(secretBytes);
    }

    public static byte[] decodeSecret(String jwtSecret) {
        String normalizedJwtSecret = jwtSecret == null ? "" : jwtSecret.trim();
        if (normalizedJwtSecret.isEmpty()) {
            throw new IllegalStateException("JWT密钥未配置");
        }
        if (looksLikeBase64(normalizedJwtSecret)) {
            try {
                return Base64.getDecoder().decode(normalizedJwtSecret);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return normalizedJwtSecret.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean looksLikeBase64(String value) {
        return value.length() % 4 == 0 && value.matches("^[A-Za-z0-9+/]+={0,2}$");
    }
}
