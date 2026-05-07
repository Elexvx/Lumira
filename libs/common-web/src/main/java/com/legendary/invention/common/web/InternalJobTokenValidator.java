package com.legendary.invention.common.web;

public final class InternalJobTokenValidator {

    private InternalJobTokenValidator() {
    }

    public static boolean isConfigured(String internalToken) {
        return internalToken != null && !internalToken.isBlank();
    }

    public static boolean isAuthorized(String token, String internalToken) {
        return isConfigured(internalToken) && token != null && internalToken.equals(token);
    }
}
