package com.legendary.invention.saas.modules.ai.infrastructure;

import com.legendary.invention.saas.infrastructure.security.SecurityProperties;
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
public class AiSecretCryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] secretKeyBytes;

    public AiSecretCryptoService(SecurityProperties securityProperties) {
        String secretSeed = StringUtils.hasText(securityProperties.getFieldSecret())
                ? securityProperties.getFieldSecret()
                : securityProperties.getJwtSecret();
        if (!StringUtils.hasText(secretSeed)) {
            throw new IllegalStateException("FIELD_SECRET or JWT_SECRET must be configured before encrypting sensitive fields");
        }
        this.secretKeyBytes = resolveKey(secretSeed);
    }

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secretKeyBytes, "AES"), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception exception) {
            throw new IllegalStateException("AI secret encryption failed", exception);
        }
    }

    public String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKeyBytes, "AES"), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("AI secret decryption failed", exception);
        }
    }

    public String mask(String plainOrCipherText) {
        if (!StringUtils.hasText(plainOrCipherText)) {
            return null;
        }
        return "******";
    }

    private byte[] resolveKey(String secretSeed) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secretSeed.getBytes(StandardCharsets.UTF_8));
            byte[] key = new byte[16];
            System.arraycopy(digest, 0, key, 0, key.length);
            return key;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to derive AI secret key", exception);
        }
    }
}
