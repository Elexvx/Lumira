package com.lumira.auth.service;

import com.lumira.common.constant.CacheKeyConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service("authLoginProtectionService")
public class LoginProtectionService {

    private final StringRedisTemplate redisTemplate;
    private final SecuritySettingsService securitySettingsService;

    public LoginProtectionService(StringRedisTemplate redisTemplate, SecuritySettingsService securitySettingsService) {
        this.redisTemplate = redisTemplate;
        this.securitySettingsService = securitySettingsService;
    }

    public void ensureCanAttempt(String account, String loginIp) {
        String scope = scope(account, loginIp);
        long validationAttempts = getCounter(loginAttemptKey(scope));
        long failureCount = getCounter(loginFailureKey(scope));
        if (validationAttempts >= securitySettingsService.getLoginMaxValidationAttempts()
                || failureCount >= securitySettingsService.getLoginMaxFailureCount()) {
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
        redisTemplate.delete(loginFailureKey(scope(account, loginIp)));
    }

    private long increment(String key) {
        Duration ttl = Duration.ofMinutes(Math.max(1L, securitySettingsService.getLoginDefenseWindowMinutes()));
        redisTemplate.opsForValue().setIfAbsent(key, "0", ttl);
        Long value = redisTemplate.opsForValue().increment(key);
        return value == null ? 0L : value;
    }

    private long getCounter(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String scope(String account, String loginIp) {
        return (account == null ? "" : account.trim()) + ":" + (loginIp == null ? "" : loginIp.trim());
    }

    private String loginAttemptKey(String scope) {
        return CacheKeyConstants.loginAttemptKey(scope);
    }

    private String loginFailureKey(String scope) {
        return CacheKeyConstants.loginFailureKey(scope);
    }
}
