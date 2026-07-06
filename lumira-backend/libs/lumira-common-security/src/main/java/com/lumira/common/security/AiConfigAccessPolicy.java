package com.lumira.common.security;

import java.util.List;
import java.util.Locale;

public final class AiConfigAccessPolicy {

    private static final List<String> AI_CONFIG_SAFE_PREFIXES = List.of(
            "branding.",
            "agreement.",
            "watermark.",
            "floating-window."
    );

    private AiConfigAccessPolicy() {
    }

    public static boolean isAiManageableConfigKey(String configKey) {
        if (isBlank(configKey) || looksSensitive(configKey)) {
            return false;
        }
        String normalized = configKey.trim().toLowerCase(Locale.ROOT);
        for (String prefix : AI_CONFIG_SAFE_PREFIXES) {
            if (normalized.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static boolean looksSensitive(String value) {
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("credential")
                || normalized.contains("private")
                || normalized.endsWith(".key")
                || normalized.contains("app-secret");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
