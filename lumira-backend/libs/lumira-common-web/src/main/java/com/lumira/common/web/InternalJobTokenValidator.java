package com.lumira.common.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class InternalJobTokenValidator {

    private static final int MAX_TOKEN_LENGTH = 512;

    private InternalJobTokenValidator() {
    }

    public static boolean isConfigured(String internalToken) {
        return isTrustedToken(internalToken);
    }

    public static boolean isAuthorized(String token, String internalToken) {
        if (!isConfigured(internalToken) || !isTrustedToken(token)) {
            return false;
        }
        return MessageDigest.isEqual(sha256(token), sha256(internalToken));
    }

    private static boolean isTrustedToken(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char ch = token.charAt(i);
            if (ch <= 32 || ch > 126) {
                return false;
            }
        }
        return true;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }
}
