package com.lumira.saas.modules.system.verification;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class TotpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    String generateSecret() {
        byte[] secret = new byte[20];
        RANDOM.nextBytes(secret);
        return Base32Codec.encode(secret);
    }

    String buildSetupUri(String issuer, String accountName, String secret, int digits, int periodSeconds) {
        String normalizedIssuer = safeUriComponent(issuer);
        String normalizedAccount = safeUriComponent(accountName);
        return "otpauth://totp/" + normalizedIssuer + ":" + normalizedAccount
                + "?secret=" + secret
                + "&issuer=" + normalizedIssuer
                + "&algorithm=SHA1"
                + "&digits=" + digits
                + "&period=" + periodSeconds;
    }

    boolean verifyCode(String secret, String verificationCode, int digits, int periodSeconds) {
        return verifyCode(secret, verificationCode, digits, periodSeconds, 1);
    }

    boolean verifyCode(String secret, String verificationCode, int digits, int periodSeconds, int allowedDrift) {
        if (secret == null || verificationCode == null || !verificationCode.matches("\\d{" + digits + "}")) {
            return false;
        }
        long counter = Instant.now().getEpochSecond() / periodSeconds;
        for (long offset = -allowedDrift; offset <= allowedDrift; offset++) {
            String expected = generateCode(secret, counter + offset, digits);
            if (Objects.equals(expected, verificationCode)) {
                return true;
            }
        }
        return false;
    }

    String generateCode(String secret, long counter, int digits) {
        byte[] key = Base32Codec.decode(secret);
        byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
        byte[] hash = hmacSha1(key, data);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int modulus = (int) Math.pow(10, digits);
        return String.format(Locale.ROOT, "%0" + digits + "d", binary % modulus);
    }

    List<String> generateRecoveryCodes(int count, int length) {
        List<String> result = new ArrayList<>(count);
        while (result.size() < count) {
            result.add(randomRecoveryCode(length));
        }
        return result;
    }

    String randomRecoveryCode(int length) {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    boolean matchesRecoveryCode(List<String> recoveryCodes, String input) {
        if (recoveryCodes == null || recoveryCodes.isEmpty() || input == null) {
            return false;
        }
        return recoveryCodes.stream().anyMatch(code -> code.equalsIgnoreCase(input.trim()));
    }

    String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 计算失败", exception);
        }
    }

    private String safeUriComponent(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return value == null ? "" : value;
        }
    }

    private byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(data);
        } catch (Exception exception) {
            throw new IllegalStateException("TOTP 计算失败", exception);
        }
    }
}
