package com.example.plugins.twofactor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorChallenge;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorProfile;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorVerification;
import com.yourcompany.saas.modules.plugin.runtime.spi.PluginSecondFactorProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.StringUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class TwoFactorPluginSecondFactorProvider implements PluginSecondFactorProvider {

    private static final String PLUGIN_CODE = "2fa";
    private static final String PLUGIN_NAME = "2FA验证插件";
    private static final String FACTOR_CODE = "totp";
    private static final String FACTOR_NAME = "TOTP 验证";
    private static final int TOTP_DIGITS = 6;
    private static final int TOTP_STEP_SECONDS = 30;
    private static final int TOTP_WINDOW = 1;
    private static final int SECRET_BYTES = 20;
    private static final int RECOVERY_CODE_COUNT = 8;
    private static final int RECOVERY_CODE_LENGTH = 10;
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public String factorCode() {
        return FACTOR_CODE;
    }

    @Override
    public String factorName() {
        return FACTOR_NAME;
    }

    @Override
    public boolean requiresEmail() {
        return true;
    }

    @Override
    public PluginSecondFactorProfile profile(PluginRuntimeContext context, Long tenantId, Long userId) {
        BindingRecord binding = loadBinding(context, tenantId, userId);
        boolean enabled = binding != null && binding.enabled;
        boolean bound = binding != null && StringUtils.hasText(binding.secretBase32);
        String maskedContact = binding == null ? "" : maskContact(defaultIfBlank(binding.email, binding.mobile));
        String statusMessage = bound ? "已绑定 TOTP 验证" : "请先完成 TOTP 绑定";
        return new PluginSecondFactorProfile(
                PLUGIN_CODE,
                PLUGIN_NAME,
                FACTOR_CODE,
                FACTOR_NAME,
                enabled,
                bound,
                true,
                maskedContact,
                statusMessage
        );
    }

    @Override
    public PluginSecondFactorChallenge prepareChallenge(PluginRuntimeContext context, Long tenantId, Long userId) {
        BindingRecord binding = requireBinding(context, tenantId, userId);
        String challengeId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        context.getJdbcTemplate().update(
                """
                        insert into plugin_2fa_challenge (
                            challenge_id, tenant_id, user_id, challenge_type, expires_at, consumed_flag, created_at, updated_at, deleted
                        ) values (?, ?, ?, 'LOGIN', ?, 0, current_timestamp, current_timestamp, 0)
                        on duplicate key update
                            tenant_id = values(tenant_id),
                            user_id = values(user_id),
                            challenge_type = values(challenge_type),
                            expires_at = values(expires_at),
                            consumed_flag = 0,
                            updated_at = current_timestamp,
                            deleted = 0
                        """,
                challengeId,
                tenantId,
                userId,
                Timestamp.from(now.plus(CHALLENGE_TTL))
        );
        audit(context, tenantId, userId, "LOGIN_CHALLENGE", "创建登录验证码挑战");
        return new PluginSecondFactorChallenge(
                PLUGIN_CODE,
                PLUGIN_NAME,
                FACTOR_CODE,
                FACTOR_NAME,
                challengeId,
                maskContact(defaultIfBlank(binding.email, binding.mobile)),
                "请输入当前 TOTP 验证码",
                null,
                null,
                List.of()
        );
    }

    @Override
    public PluginSecondFactorVerification verify(PluginRuntimeContext context, String challengeId, String verificationCode) {
        ChallengeRecord challenge = loadChallenge(context, challengeId);
        if (challenge == null) {
            return PluginSecondFactorVerification.failure("验证码挑战不存在或已失效");
        }
        if (challenge.consumedFlag || challenge.expiresAt == null || challenge.expiresAt.toInstant().isBefore(Instant.now())) {
            return PluginSecondFactorVerification.failure("验证码已过期，请重新登录");
        }
        BindingRecord binding = requireBinding(context, challenge.tenantId, challenge.userId);
        if (!verifyTotp(binding.secretBase32, verificationCode)) {
            return PluginSecondFactorVerification.failure("验证码错误，请重试");
        }
        context.getJdbcTemplate().update(
                "update plugin_2fa_challenge set consumed_flag = 1, updated_at = current_timestamp where challenge_id = ? and deleted = 0",
                challengeId
        );
        audit(context, challenge.tenantId, challenge.userId, "VERIFY", "TOTP 验证成功");
        return PluginSecondFactorVerification.success(challenge.tenantId, challenge.userId, getUsername(context, challenge.userId), "验证成功");
    }

    @Override
    public PluginSecondFactorChallenge bind(PluginRuntimeContext context, Long tenantId, Long userId, String email, String mobile) {
        String secretBase32 = generateSecret();
        List<String> recoveryCodes = generateRecoveryCodes();
        String recoveryHashesJson = toJson(hashRecoveryCodes(recoveryCodes), context.getObjectMapper());
        context.getJdbcTemplate().update(
                """
                        insert into plugin_2fa_binding (
                            tenant_id, user_id, secret_base32, email, mobile, enabled, recovery_hashes_json, created_at, updated_at, deleted
                        ) values (?, ?, ?, ?, ?, 1, ?, current_timestamp, current_timestamp, 0)
                        on duplicate key update
                            secret_base32 = values(secret_base32),
                            email = values(email),
                            mobile = values(mobile),
                            enabled = 1,
                            recovery_hashes_json = values(recovery_hashes_json),
                            updated_at = current_timestamp,
                            deleted = 0
                        """,
                tenantId,
                userId,
                secretBase32,
                email,
                mobile,
                recoveryHashesJson
        );
        audit(context, tenantId, userId, "BIND", "完成 TOTP 绑定");
        return new PluginSecondFactorChallenge(
                PLUGIN_CODE,
                PLUGIN_NAME,
                FACTOR_CODE,
                FACTOR_NAME,
                "bind-" + UUID.randomUUID(),
                maskContact(defaultIfBlank(email, mobile)),
                "请使用扫码工具或手动输入密钥完成绑定",
                buildOtpauthUri(context, tenantId, userId, secretBase32),
                secretBase32,
                recoveryCodes
        );
    }

    @Override
    public void unbind(PluginRuntimeContext context, Long tenantId, Long userId) {
        context.getJdbcTemplate().update(
                """
                        update plugin_2fa_binding
                        set enabled = 0, deleted = 1, updated_at = current_timestamp
                        where tenant_id = ? and user_id = ? and deleted = 0
                        """,
                tenantId,
                userId
        );
        context.getJdbcTemplate().update(
                "update plugin_2fa_challenge set deleted = 1, updated_at = current_timestamp where tenant_id = ? and user_id = ? and deleted = 0",
                tenantId,
                userId
        );
        audit(context, tenantId, userId, "UNBIND", "解绑 TOTP 验证");
    }

    private boolean verifyTotp(String secretBase32, String verificationCode) {
        if (!StringUtils.hasText(secretBase32) || !StringUtils.hasText(verificationCode)) {
            return false;
        }
        String normalizedCode = verificationCode.trim();
        if (!normalizedCode.matches("\\d{6}")) {
            return false;
        }
        long currentStep = Instant.now().getEpochSecond() / TOTP_STEP_SECONDS;
        for (long step = currentStep - TOTP_WINDOW; step <= currentStep + TOTP_WINDOW; step++) {
            if (generateTotp(secretBase32, step).equals(normalizedCode)) {
                return true;
            }
        }
        return false;
    }

    private String generateTotp(String secretBase32, long step) {
        try {
            byte[] key = base32Decode(secretBase32);
            ByteBuffer buffer = ByteBuffer.allocate(8);
            buffer.putLong(step);
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(buffer.array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, TOTP_DIGITS);
            return String.format("%0" + TOTP_DIGITS + "d", otp);
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "TOTP 计算失败: " + exception.getMessage());
        }
    }

    private String generateSecret() {
        byte[] randomBytes = new byte[SECRET_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return base32Encode(randomBytes);
    }

    private List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            codes.add(randomCode(RECOVERY_CODE_LENGTH));
        }
        return codes;
    }

    private String randomCode(int length) {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet.charAt(SECURE_RANDOM.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    private List<String> hashRecoveryCodes(List<String> recoveryCodes) {
        return recoveryCodes.stream().map(this::sha256Hex).toList();
    }

    private String buildOtpauthUri(PluginRuntimeContext context, Long tenantId, Long userId, String secretBase32) {
        String username = getUsername(context, userId);
        String issuer = tenantName(context, tenantId);
        String label = urlEncode(issuer + ":" + username);
        String encodedIssuer = urlEncode(issuer);
        return "otpauth://totp/" + label + "?secret=" + secretBase32 + "&issuer=" + encodedIssuer + "&algorithm=SHA1&digits=6&period=30";
    }

    private String tenantName(PluginRuntimeContext context, Long tenantId) {
        try {
            String tenantName = context.getJdbcTemplate().queryForObject(
                    "select tenant_name from tenant_info where id = ? and deleted = 0",
                    String.class,
                    tenantId
            );
            return StringUtils.hasText(tenantName) ? tenantName : PLUGIN_NAME;
        } catch (Exception ignored) {
            return PLUGIN_NAME;
        }
    }

    private String getUsername(PluginRuntimeContext context, Long userId) {
        try {
            String username = context.getJdbcTemplate().queryForObject(
                    "select username from sys_user where id = ? and deleted = 0",
                    String.class,
                    userId
            );
            return StringUtils.hasText(username) ? username : String.valueOf(userId);
        } catch (Exception ignored) {
            return String.valueOf(userId);
        }
    }

    private BindingRecord requireBinding(PluginRuntimeContext context, Long tenantId, Long userId) {
        BindingRecord binding = loadBinding(context, tenantId, userId);
        if (binding == null || !StringUtils.hasText(binding.secretBase32)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先完成 TOTP 绑定");
        }
        return binding;
    }

    private BindingRecord loadBinding(PluginRuntimeContext context, Long tenantId, Long userId) {
        List<BindingRecord> records = context.getJdbcTemplate().query(
                """
                        select tenant_id, user_id, secret_base32, email, mobile, enabled, recovery_hashes_json, created_at, updated_at
                        from plugin_2fa_binding
                        where tenant_id = ? and user_id = ? and deleted = 0
                        order by id desc
                        limit 1
                        """,
                bindingRowMapper(),
                tenantId,
                userId
        );
        return records.stream().findFirst().orElse(null);
    }

    private ChallengeRecord loadChallenge(PluginRuntimeContext context, String challengeId) {
        List<ChallengeRecord> records = context.getJdbcTemplate().query(
                """
                        select challenge_id, tenant_id, user_id, challenge_type, expires_at, consumed_flag
                        from plugin_2fa_challenge
                        where challenge_id = ? and deleted = 0
                        limit 1
                        """,
                challengeRowMapper(),
                challengeId
        );
        return records.stream().findFirst().orElse(null);
    }

    private RowMapper<BindingRecord> bindingRowMapper() {
        return (rs, rowNum) -> new BindingRecord(
                rs.getLong("tenant_id"),
                rs.getLong("user_id"),
                rs.getString("secret_base32"),
                rs.getString("email"),
                rs.getString("mobile"),
                rs.getInt("enabled") == 1,
                rs.getString("recovery_hashes_json")
        );
    }

    private RowMapper<ChallengeRecord> challengeRowMapper() {
        return (rs, rowNum) -> new ChallengeRecord(
                rs.getString("challenge_id"),
                rs.getLong("tenant_id"),
                rs.getLong("user_id"),
                rs.getString("challenge_type"),
                rs.getTimestamp("expires_at"),
                rs.getInt("consumed_flag") == 1
        );
    }

    private String maskContact(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.contains("@")) {
            int atIndex = trimmed.indexOf("@");
            String local = trimmed.substring(0, atIndex);
            String domain = trimmed.substring(atIndex);
            return local.length() <= 2 ? "**" + domain : local.substring(0, 2) + "****" + domain;
        }
        if (trimmed.length() <= 4) {
            return "****";
        }
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 2);
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return value;
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成恢复码哈希", exception);
        }
    }

    private String toJson(Object value, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("JSON 序列化失败", exception);
        }
    }

    private String base32Encode(byte[] bytes) {
        final char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        StringBuilder builder = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte current : bytes) {
            buffer = (buffer << 8) | (current & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1f;
                bitsLeft -= 5;
                builder.append(alphabet[index]);
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1f;
            builder.append(alphabet[index]);
        }
        return builder.toString();
    }

    private byte[] base32Decode(String value) {
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase();
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        int buffer = 0;
        int bitsLeft = 0;
        ByteBuffer output = ByteBuffer.allocate(normalized.length() * 5 / 8 + 1);
        for (char c : normalized.toCharArray()) {
            int index = alphabet.indexOf(c);
            if (index < 0) {
                continue;
            }
            buffer = (buffer << 5) | index;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output.put((byte) ((buffer >> (bitsLeft - 8)) & 0xff));
                bitsLeft -= 8;
            }
        }
        output.flip();
        byte[] bytes = new byte[output.remaining()];
        output.get(bytes);
        return bytes;
    }

    private void audit(PluginRuntimeContext context, Long tenantId, Long userId, String actionType, String detail) {
        context.getJdbcTemplate().update(
                """
                        insert into plugin_2fa_audit (
                            tenant_id, user_id, action_type, detail_message, created_at, deleted
                        ) values (?, ?, ?, ?, current_timestamp, 0)
                        """,
                tenantId,
                userId,
                actionType,
                detail
        );
    }

    private record BindingRecord(
            Long tenantId,
            Long userId,
            String secretBase32,
            String email,
            String mobile,
            boolean enabled,
            String recoveryHashesJson
    ) {
    }

    private record ChallengeRecord(
            String challengeId,
            Long tenantId,
            Long userId,
            String challengeType,
            Timestamp expiresAt,
            boolean consumedFlag
    ) {
    }
}
