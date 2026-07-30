package com.lumira.saas.infrastructure.security.service;

import com.lumira.common.security.CurrentUser;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
public class InitialPasswordChangeGuard {

    private static final long INITIAL_PASSWORD_CACHE_TTL_MILLIS = 1_000L;
    private static final long INITIAL_PASSWORD_CACHE_MAX_ENTRIES = 512L;

    private final MyBatisQueryOperations jdbcTemplate;
    private final Cache<InitialPasswordCacheKey, Boolean> initialPasswordCache;

    public InitialPasswordChangeGuard(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.initialPasswordCache = CacheBuilder.newBuilder()
                .maximumSize(INITIAL_PASSWORD_CACHE_MAX_ENTRIES)
                .expireAfterWrite(INITIAL_PASSWORD_CACHE_TTL_MILLIS, TimeUnit.MILLISECONDS)
                .build();
    }

    public boolean requiresPasswordChange(CurrentUser currentUser) {
        if (!isTrustedCurrentUser(currentUser)) {
            return false;
        }
        if (Boolean.FALSE.equals(currentUser.getRequiresPasswordChange())) {
            return false;
        }
        String trustedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        Long trustedUserId = currentUser.getUserId();
        if (trustedUserId == null || trustedUserId <= 0 || !StringUtils.hasText(trustedUserUuid)) {
            return false;
        }
        InitialPasswordCacheKey cacheKey = InitialPasswordCacheKey.from(currentUser);
        try {
            return initialPasswordCache.get(cacheKey, () -> loadRequiresPasswordChange(trustedUserId, trustedUserUuid));
        } catch (UncheckedExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to evaluate initial password guard", cause);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to evaluate initial password guard", cause);
        }
    }

    void invalidate(CurrentUser currentUser) {
        if (isTrustedCurrentUser(currentUser)) {
            initialPasswordCache.invalidate(InitialPasswordCacheKey.from(currentUser));
        }
    }

    private boolean loadRequiresPasswordChange(Long userId, String userUuid) {
        Integer passwordChangeRequired = jdbcTemplate.queryForObject(
                """
                        select coalesce(max(password_change_required), 0)
                        from iam_user_credential
                        where user_id = ?
                          and user_uuid = ?
                          and credential_type = 'PASSWORD'
                          and status = 'ENABLED'
                          and deleted = 0
                        """,
                Integer.class,
                userId,
                userUuid
        );
        if (passwordChangeRequired == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Password change guard is unavailable");
        }
        return passwordChangeRequired == 1;
    }

    private boolean isTrustedCurrentUser(CurrentUser currentUser) {
        return AuthenticationTrustSupport.isTrustedCurrentUser(currentUser);
    }

    private record InitialPasswordCacheKey(
            Long userId,
            String userUuid,
            String username,
            String sessionId,
            Integer sessionVersion
    ) {
        private static InitialPasswordCacheKey from(CurrentUser currentUser) {
            return new InitialPasswordCacheKey(
                    currentUser.getUserId(),
                    currentUser.getUserUuid(),
                    currentUser.getUsername(),
                    currentUser.getSessionId(),
                    currentUser.getSessionVersion()
            );
        }
    }
}
