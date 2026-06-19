package com.lumira.common.security;

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
public class FieldCryptoService {

    public static final String PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String fieldSecret;
    private final SecretKeySpec keySpec;

    public FieldCryptoService(@Value("${saas.security.field-secret:${FIELD_SECRET:}}") String fieldSecret) {
        this.fieldSecret = fieldSecret;
        this.keySpec = StringUtils.hasText(fieldSecret) ? new SecretKeySpec(deriveKeyBytes(fieldSecret), "AES") : null;
    }

    public boolean isEncrypted(String value) {
        return StringUtils.hasText(value) && value.startsWith(PREFIX);
    }

    public boolean isConfigured() {
        return keySpec != null;
    }

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return plainText;
        }
        if (isEncrypted(plainText)) {
            return plainText;
        }
        SecretKeySpec key = requireKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception exception) {
            throw new IllegalStateException("字段加密失败", exception);
        }
    }

    public String decrypt(String value) {
        if (!StringUtils.hasText(value) || !isEncrypted(value)) {
            return value;
        }
        SecretKeySpec key = requireKey();
        try {
            byte[] encoded = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(encoded);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("字段解密失败", exception);
        }
    }

    private SecretKeySpec requireKey() {
        if (keySpec == null) {
            throw new IllegalStateException("FIELD_SECRET must be configured before encrypting sensitive fields");
        }
        return keySpec;
    }

    private byte[] deriveKeyBytes(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("字段加密密钥派生失败", exception);
        }
    }
}
