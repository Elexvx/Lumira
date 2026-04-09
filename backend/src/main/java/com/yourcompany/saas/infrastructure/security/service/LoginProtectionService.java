package com.yourcompany.saas.infrastructure.security.service;

import com.yourcompany.saas.common.constant.CacheKeyConstants;
import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.infrastructure.redis.CacheTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Component
public class LoginProtectionService {

    private final CacheTemplate cacheTemplate;
    private final SecuritySettingsService securitySettingsService;

    public LoginProtectionService(CacheTemplate cacheTemplate, SecuritySettingsService securitySettingsService) {
        this.cacheTemplate = cacheTemplate;
        this.securitySettingsService = securitySettingsService;
    }

    public void ensureCanAttempt(String account, String loginIp) {
        String scope = scope(account, loginIp);
        long maxValidationAttempts = securitySettingsService.getLoginMaxValidationAttempts();
        long maxFailureCount = securitySettingsService.getLoginMaxFailureCount();
        long validationAttempts = getCounter(loginAttemptKey(scope));
        long failureCount = getCounter(loginFailureKey(scope));
        if (validationAttempts >= maxValidationAttempts || failureCount >= maxFailureCount) {
            throw new BizException(ErrorCode.LOGIN_RATE_LIMITED, "登录失败次数过多，请稍后再试", ErrorCode.LOGIN_RATE_LIMITED.getDefaultUserMessage());
        }
    }

    public void recordAttempt(String account, String loginIp) {
        increment(loginAttemptKey(scope(account, loginIp)));
    }

    public void recordFailure(String account, String loginIp) {
        increment(loginFailureKey(scope(account, loginIp)));
    }

    public void clearFailureState(String account, String loginIp) {
        String scope = scope(account, loginIp);
        cacheTemplate.remove(loginFailureKey(scope));
    }

    private long increment(String key) {
        Duration ttl = Duration.ofMinutes(Math.max(1L, securitySettingsService.getLoginDefenseWindowMinutes()));
        cacheTemplate.putIfAbsent(key, "0", ttl);
        Long value = cacheTemplate.increment(key);
        return value == null ? 0L : value;
    }

    private long getCounter(String key) {
        String value = cacheTemplate.get(key);
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String scope(String account, String loginIp) {
        String normalized = String.join("|", normalize(account), normalize(loginIp));
        return sha256Hex(normalized);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : "";
    }

    private String loginAttemptKey(String scope) {
        return String.join(":", CacheKeyConstants.PREFIX, CacheKeyConstants.LOGIN_ATTEMPT, scope);
    }

    private String loginFailureKey(String scope) {
        return String.join(":", CacheKeyConstants.PREFIX, CacheKeyConstants.LOGIN_FAILURE, scope);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
