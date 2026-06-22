package com.lumira.saas.infrastructure.security.service;

import com.lumira.common.security.CurrentUser;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.lumira.common.security.InitialAdminPassword;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
public class InitialPasswordChangeGuard {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final long INITIAL_PASSWORD_CACHE_TTL_MILLIS = 1_000L;
    private static final long INITIAL_PASSWORD_CACHE_MAX_ENTRIES = 512L;

    private final MyBatisQueryOperations jdbcTemplate;
    private final ObjectProvider<PasswordEncoder> passwordEncoderProvider;
    private final Environment environment;
    private final Cache<InitialPasswordCacheKey, Boolean> initialPasswordCache;

    protected InitialPasswordChangeGuard(MyBatisQueryOperations jdbcTemplate, ObjectProvider<PasswordEncoder> passwordEncoderProvider) {
        this(jdbcTemplate, passwordEncoderProvider, null);
    }

    @Autowired
    public InitialPasswordChangeGuard(
            MyBatisQueryOperations jdbcTemplate,
            ObjectProvider<PasswordEncoder> passwordEncoderProvider,
            Environment environment
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoderProvider = passwordEncoderProvider;
        this.environment = environment;
        this.initialPasswordCache = CacheBuilder.newBuilder()
                .maximumSize(INITIAL_PASSWORD_CACHE_MAX_ENTRIES)
                .expireAfterWrite(INITIAL_PASSWORD_CACHE_TTL_MILLIS, TimeUnit.MILLISECONDS)
                .build();
    }

    public boolean requiresPasswordChange(CurrentUser currentUser) {
        if (currentUser == null) {
            return false;
        }
        if (Boolean.FALSE.equals(currentUser.getRequiresPasswordChange())) {
            return false;
        }
        if (!DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(currentUser.getUsername())) {
            return false;
        }
        InitialPasswordCacheKey cacheKey = InitialPasswordCacheKey.from(currentUser);
        try {
            return initialPasswordCache.get(cacheKey, this::loadRequiresPasswordChange);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to evaluate initial password guard", cause);
        }
    }

    void invalidate(CurrentUser currentUser) {
        if (currentUser != null) {
            initialPasswordCache.invalidate(InitialPasswordCacheKey.from(currentUser));
        }
    }

    private boolean loadRequiresPasswordChange() {
        String passwordHash = jdbcTemplate.queryForObject(
                "select password_hash from sys_user where username = ? and deleted = 0 limit 1",
                String.class,
                DEFAULT_ADMIN_USERNAME
        );
        return StringUtils.hasText(passwordHash)
                && passwordEncoderProvider.getObject().matches(InitialAdminPassword.resolve(environment), passwordHash);
    }

    private record InitialPasswordCacheKey(
            Long userId,
            Long tenantId,
            String sessionId,
            Integer sessionVersion
    ) {
        private static InitialPasswordCacheKey from(CurrentUser currentUser) {
            return new InitialPasswordCacheKey(
                    currentUser.getUserId(),
                    currentUser.getCurrentTenantId(),
                    currentUser.getSessionId(),
                    currentUser.getSessionVersion()
            );
        }
    }
}
