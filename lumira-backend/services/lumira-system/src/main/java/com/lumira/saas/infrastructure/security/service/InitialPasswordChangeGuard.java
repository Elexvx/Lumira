package com.lumira.saas.infrastructure.security.service;

import com.lumira.common.security.CurrentUser;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.InitialAdminPassword;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
public class InitialPasswordChangeGuard {

    private static final Long DEFAULT_ADMIN_USER_ID = 1001L;
    private static final long INITIAL_PASSWORD_CACHE_TTL_MILLIS = 1_000L;
    private static final long INITIAL_PASSWORD_CACHE_MAX_ENTRIES = 512L;

    private final MyBatisQueryOperations jdbcTemplate;
    private final ObjectProvider<PasswordEncoder> passwordEncoderProvider;
    private final Cache<InitialPasswordCacheKey, Boolean> initialPasswordCache;

    public InitialPasswordChangeGuard(
            MyBatisQueryOperations jdbcTemplate,
            ObjectProvider<PasswordEncoder> passwordEncoderProvider
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoderProvider = passwordEncoderProvider;
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
        if (!DEFAULT_ADMIN_USER_ID.equals(trustedUserId)) {
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
        String passwordHash = jdbcTemplate.queryForObject(
                """
                        select password_hash
                        from sys_user
                        where id = ?
                          and uuid = ?
                          and status = 'ENABLED'
                          and deleted = 0
                        limit 1
                        """,
                String.class,
                userId,
                userUuid
        );
        PasswordEncoder passwordEncoder = passwordEncoderProvider.getIfAvailable();
        if (passwordEncoder == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Initial password guard is unavailable");
        }
        return StringUtils.hasText(passwordHash)
                && passwordEncoder.matches(InitialAdminPassword.DEFAULT_PASSWORD, passwordHash);
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
