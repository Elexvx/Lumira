package com.lumira.common.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class InternalJobTokenValidator {

    private InternalJobTokenValidator() {
    }

    public static boolean isConfigured(String internalToken) {
        return internalToken != null && !internalToken.isBlank();
    }

    public static boolean isAuthorized(String token, String internalToken) {
        if (!isConfigured(internalToken) || token == null || token.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(sha256(token), sha256(internalToken));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }
}
