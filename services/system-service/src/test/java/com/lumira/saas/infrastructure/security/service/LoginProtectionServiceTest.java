package com.lumira.saas.infrastructure.security.service;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.redis.CacheTemplate;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginProtectionServiceTest {

    @Test
    void shouldBlockAfterValidationAttemptsReachLimit() {
        Map<String, String> store = new HashMap<>();
        LoginProtectionService service = new LoginProtectionService(
                new MapBackedCacheTemplate(store),
                stubSecuritySettingsService(5, 2, 10)
        );

        assertDoesNotThrow(() -> service.ensureCanAttempt("admin", "127.0.0.1"));
        service.recordAttempt("admin", "127.0.0.1");
        assertDoesNotThrow(() -> service.ensureCanAttempt("admin", "127.0.0.1"));
        service.recordAttempt("admin", "127.0.0.1");

        BizException exception = assertThrows(BizException.class, () -> service.ensureCanAttempt("admin", "127.0.0.1"));

        assertEquals(ErrorCode.LOGIN_RATE_LIMITED, exception.getErrorCode());
    }

    @Test
    void shouldBlockAfterFailureCountReachLimitAndClearFailureState() {
        Map<String, String> store = new HashMap<>();
        LoginProtectionService service = new LoginProtectionService(
                new MapBackedCacheTemplate(store),
                stubSecuritySettingsService(5, 100, 2)
        );

        service.recordFailure("admin", "127.0.0.1");
        assertDoesNotThrow(() -> service.ensureCanAttempt("admin", "127.0.0.1"));
        service.recordFailure("admin", "127.0.0.1");

        BizException exception = assertThrows(BizException.class, () -> service.ensureCanAttempt("admin", "127.0.0.1"));
        assertEquals(ErrorCode.LOGIN_RATE_LIMITED, exception.getErrorCode());

        service.clearFailureState("admin", "127.0.0.1");
        assertDoesNotThrow(() -> service.ensureCanAttempt("admin", "127.0.0.1"));
    }

    private SecuritySettingsService stubSecuritySettingsService(long windowMinutes, long validationAttempts, long failureCount) {
        SecuritySettingsService.SecuritySettingsSnapshot snapshot = new SecuritySettingsService.SecuritySettingsSnapshot();
        snapshot.setLoginDefenseWindowMinutes(windowMinutes);
        snapshot.setLoginMaxValidationAttempts(validationAttempts);
        snapshot.setLoginMaxFailureCount(failureCount);
        return new SecuritySettingsService(null, null) {
            @Override
            public SecuritySettingsSnapshot loadSettings() {
                return snapshot;
            }
        };
    }

    private static final class MapBackedCacheTemplate extends CacheTemplate {
        private final Map<String, String> store;

        private MapBackedCacheTemplate(Map<String, String> store) {
            super(null);
            this.store = store;
        }

        @Override
        public boolean putIfAbsent(String key, String value, Duration ttl) {
            if (store.containsKey(key)) {
                return false;
            }
            store.put(key, value);
            return true;
        }

        @Override
        public String get(String key) {
            return store.get(key);
        }

        @Override
        public Long increment(String key) {
            long value = Long.parseLong(store.getOrDefault(key, "0")) + 1;
            store.put(key, String.valueOf(value));
            return value;
        }

        @Override
        public void remove(String key) {
            store.remove(key);
        }
    }
}
