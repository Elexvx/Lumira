package com.lumira.saas.infrastructure.security.service;

import com.lumira.saas.common.constant.CacheKeyConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.redis.CacheTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private final long registerIpMaxCount;
    private final long registerIpWindowMinutes;

    @Autowired
    public LoginProtectionService(
            CacheTemplate cacheTemplate,
            SecuritySettingsService securitySettingsService,
            @Value("${saas.security.register-ip-max-count:20}") long registerIpMaxCount,
            @Value("${saas.security.register-ip-window-minutes:10}") long registerIpWindowMinutes
    ) {
        this.cacheTemplate = cacheTemplate;
        this.securitySettingsService = securitySettingsService;
        this.registerIpMaxCount = registerIpMaxCount;
        this.registerIpWindowMinutes = registerIpWindowMinutes;
    }

    public LoginProtectionService(CacheTemplate cacheTemplate, SecuritySettingsService securitySettingsService) {
        this(cacheTemplate, securitySettingsService, 20L, 10L);
    }

    public void ensureCanAttempt(String account, String loginIp) {
        String scope = scope(account, loginIp);
        long maxValidationAttempts = securitySettingsService.getLoginMaxValidationAttempts();
        long maxFailureCount = securitySettingsService.getLoginMaxFailureCount();
        long validationAttempts = getCounter(loginAttemptKey(scope));
        long failureCount = getCounter(loginFailureKey(scope));
        long accountFailureCount = getCounter(loginAccountFailureKey(normalize(account)));
        long ipFailureCount = getCounter(loginIpFailureKey(normalize(loginIp)));
        if (validationAttempts >= maxValidationAttempts
                || failureCount >= maxFailureCount
                || accountFailureCount >= maxFailureCount
                || ipFailureCount >= maxFailureCount) {
            throw new BizException(ErrorCode.LOGIN_RATE_LIMITED, "登录失败次数过多，请稍后再试", ErrorCode.LOGIN_RATE_LIMITED.getDefaultUserMessage());
        }
    }

    public void ensureCanRegister(String loginIp) {
        if (getCounter(registerIpKey(normalize(loginIp))) >= Math.max(1L, registerIpMaxCount)) {
            throw new BizException(ErrorCode.LOGIN_RATE_LIMITED, "注册过于频繁，请稍后再试", ErrorCode.LOGIN_RATE_LIMITED.getDefaultUserMessage());
        }
    }

    public void recordRegistration(String loginIp) {
        increment(registerIpKey(normalize(loginIp)), Duration.ofMinutes(Math.max(1L, registerIpWindowMinutes)));
    }

    public void recordAttempt(String account, String loginIp) {
        increment(loginAttemptKey(scope(account, loginIp)));
    }

    public void recordFailure(String account, String loginIp) {
        increment(loginFailureKey(scope(account, loginIp)));
        increment(loginAccountFailureKey(normalize(account)));
        increment(loginIpFailureKey(normalize(loginIp)));
    }

    public void clearFailureState(String account, String loginIp) {
        String scope = scope(account, loginIp);
        cacheTemplate.remove(loginFailureKey(scope));
        cacheTemplate.remove(loginAccountFailureKey(normalize(account)));
        cacheTemplate.remove(loginIpFailureKey(normalize(loginIp)));
    }

    private long increment(String key) {
        Duration ttl = Duration.ofMinutes(Math.max(1L, securitySettingsService.getLoginDefenseWindowMinutes()));
        return increment(key, ttl);
    }

    private long increment(String key, Duration ttl) {
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

    private String loginAccountFailureKey(String account) {
        return String.join(":", CacheKeyConstants.PREFIX, CacheKeyConstants.LOGIN_FAILURE, "account", sha256Hex(account));
    }

    private String loginIpFailureKey(String loginIp) {
        return String.join(":", CacheKeyConstants.PREFIX, CacheKeyConstants.LOGIN_FAILURE, "ip", sha256Hex(loginIp));
    }

    private String registerIpKey(String loginIp) {
        return String.join(":", CacheKeyConstants.PREFIX, "register", "ip", sha256Hex(loginIp));
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
