package com.legendary.invention.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class PaymentConfigCryptoService {

    private static final String PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String fieldSecret;
    private SecretKeySpec keySpec;

    public PaymentConfigCryptoService(
            ObjectMapper objectMapper,
            @Value("${saas.security.field-secret:${FIELD_SECRET:payment-service-field-secret-change-me-2026}}") String fieldSecret
    ) {
        this.objectMapper = objectMapper;
        this.fieldSecret = fieldSecret;
    }

    @PostConstruct
    void initialize() {
        this.keySpec = new SecretKeySpec(deriveKeyBytes(fieldSecret), "AES");
    }

    public String encryptJson(Object payload) {
        try {
            byte[] plain = objectMapper.writeValueAsBytes(payload);
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain);
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new IllegalStateException("支付配置加密失败", ex);
        }
    }

    public <T> T decryptJson(String payload, Class<T> valueType) {
        try {
            if (!StringUtils.hasText(payload)) {
                return objectMapper.readValue("{}", valueType);
            }
            String normalized = payload.startsWith(PREFIX) ? payload.substring(PREFIX.length()) : payload;
            byte[] encoded = Base64.getDecoder().decode(normalized);
            ByteBuffer buffer = ByteBuffer.wrap(encoded);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(encrypted);
            return objectMapper.readValue(plain, valueType);
        } catch (Exception ex) {
            throw new IllegalStateException("支付配置解密失败", ex);
        }
    }

    private byte[] deriveKeyBytes(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8));
            byte[] keyBytes = new byte[32];
            System.arraycopy(hash, 0, keyBytes, 0, keyBytes.length);
            return keyBytes;
        } catch (Exception ex) {
            throw new IllegalStateException("支付配置密钥派生失败", ex);
        }
    }
}
