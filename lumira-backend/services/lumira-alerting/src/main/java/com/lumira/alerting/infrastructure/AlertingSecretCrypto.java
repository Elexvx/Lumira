package com.lumira.alerting.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class AlertingSecretCrypto {
    private static final String PREFIX = "a1:";
    private static final int IV_BYTES = 12;
    private static final Set<String> SECRET_KEYS = Set.of(
            "secret", "appsecret", "clientsecret", "corpsecret", "password", "webhookurl", "token", "signsecret"
    );
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final ObjectMapper objectMapper;
    private final byte[] key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AlertingSecretCrypto(
            ObjectMapper objectMapper,
            @Value("${alerting.secrets.master-key:${ALERTING_SECRETS_MASTER_KEY:}}") String masterKey
    ) {
        this.objectMapper = objectMapper;
        this.key = derive(masterKey);
    }

    public String encrypt(Map<String, Object> config) {
        requireConfigured();
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] plain = objectMapper.writeValueAsBytes(config == null ? Map.of() : config);
            byte[] encrypted = cipher.doFinal(plain);
            return PREFIX + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array()
            );
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt alert channel configuration", exception);
        }
    }

    public Map<String, Object> decrypt(String value) {
        requireConfigured();
        if (value == null || !value.startsWith(PREFIX)) {
            throw new IllegalStateException("Alert channel configuration is not encrypted");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (combined.length <= IV_BYTES) throw new IllegalArgumentException("Invalid encrypted payload");
            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            System.arraycopy(combined, IV_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return objectMapper.readValue(cipher.doFinal(encrypted), MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to decrypt alert channel configuration", exception);
        }
    }

    public Map<String, Object> masked(Map<String, Object> config) {
        Map<String, Object> masked = new LinkedHashMap<>();
        if (config == null) return masked;
        config.forEach((key, value) -> masked.put(key, isSecret(key) && value != null && !value.toString().isBlank() ? "******" : value));
        return masked;
    }

    public boolean hasSecret(Map<String, Object> config) {
        return config != null && config.entrySet().stream()
                .anyMatch(entry -> isSecret(entry.getKey()) && entry.getValue() != null && !entry.getValue().toString().isBlank());
    }

    public Map<String, Object> retainExistingSecrets(Map<String, Object> existing, Map<String, Object> requested) {
        Map<String, Object> merged = new LinkedHashMap<>(requested == null ? Map.of() : requested);
        if (existing == null) return merged;
        existing.forEach((key, value) -> {
            if (isSecret(key) && (!merged.containsKey(key) || "******".equals(merged.get(key)))) {
                merged.put(key, value);
            }
        });
        return merged;
    }

    public String fingerprint(Map<String, Object> config) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(config == null ? Map.of() : config);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fingerprint alert configuration", exception);
        }
    }

    private void requireConfigured() {
        if (key == null) {
            throw new BizException(
                    ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "ALERTING_SECRETS_MASTER_KEY is not configured; channel secrets cannot be used"
            );
        }
    }

    private static boolean isSecret(String key) {
        return key != null && SECRET_KEYS.contains(key.toLowerCase(Locale.ROOT));
    }

    private static byte[] derive(String masterKey) {
        if (masterKey == null || masterKey.isBlank()) return null;
        if (masterKey.trim().length() < 32) {
            throw new IllegalStateException("ALERTING_SECRETS_MASTER_KEY must contain at least 32 characters");
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(masterKey.trim().getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to derive alerting encryption key", exception);
        }
    }
}
