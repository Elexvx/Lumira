package com.yourcompany.saas.infrastructure.security.service;

import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.infrastructure.security.SecurityProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class SecuritySettingsService {

    private static final long PLATFORM_TENANT_ID = 1001L;
    private static final String PLATFORM_SCOPE = "PLATFORM";
    private static final String IDLE_TIMEOUT_KEY = "security.idle-timeout-seconds";
    private static final String ACCESS_TOKEN_EXPIRE_KEY = "security.access-token-expire-seconds";
    private static final String REFRESH_TOKEN_EXPIRE_KEY = "security.refresh-token-expire-seconds";

    private final JdbcTemplate jdbcTemplate;
    private final SecurityProperties securityProperties;

    public SecuritySettingsService(JdbcTemplate jdbcTemplate, SecurityProperties securityProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.securityProperties = securityProperties;
    }

    public long getIdleTimeoutSeconds() {
        return loadSettings().getIdleTimeoutSeconds();
    }

    public long getAccessTokenExpireSeconds() {
        return loadSettings().getAccessTokenExpireSeconds();
    }

    public long getRefreshTokenExpireSeconds() {
        return loadSettings().getRefreshTokenExpireSeconds();
    }

    public SecuritySettingsSnapshot loadSettings() {
        return new SecuritySettingsSnapshot(
                resolveSeconds(IDLE_TIMEOUT_KEY, securityProperties.getIdleTimeoutSeconds()),
                resolveSeconds(ACCESS_TOKEN_EXPIRE_KEY, securityProperties.getAccessTokenExpireSeconds()),
                resolveSeconds(REFRESH_TOKEN_EXPIRE_KEY, securityProperties.getRefreshTokenExpireSeconds())
        );
    }

    public SecuritySettingsSnapshot updateSettings(SecuritySettingsSnapshot request) {
        validatePositive(request.getIdleTimeoutSeconds(), "空闲超时时间");
        validatePositive(request.getAccessTokenExpireSeconds(), "access token 过期时间");
        validatePositive(request.getRefreshTokenExpireSeconds(), "refresh token 刷新时限");

        upsertConfig(
                IDLE_TIMEOUT_KEY,
                "空闲超时时间",
                request.getIdleTimeoutSeconds(),
                "会话在无操作状态下允许保持的秒数"
        );
        upsertConfig(
                ACCESS_TOKEN_EXPIRE_KEY,
                "Access Token 过期时间",
                request.getAccessTokenExpireSeconds(),
                "Access Token 的有效秒数"
        );
        upsertConfig(
                REFRESH_TOKEN_EXPIRE_KEY,
                "Refresh Token 刷新时限",
                request.getRefreshTokenExpireSeconds(),
                "Refresh Token 的有效秒数"
        );

        return loadSettings();
    }

    private long resolveSeconds(String configKey, long defaultValue) {
        List<String> values = jdbcTemplate.query(
                """
                        select config_value
                        from sys_config
                        where tenant_id = ? and config_key = ? and config_scope = ? and deleted = 0
                        order by id desc
                        limit 1
                        """,
                (rs, rowNum) -> rs.getString("config_value"),
                PLATFORM_TENANT_ID,
                configKey,
                PLATFORM_SCOPE
        );
        if (values.isEmpty()) {
            return defaultValue;
        }

        String configValue = values.get(0);
        if (!StringUtils.hasText(configValue)) {
            return defaultValue;
        }

        try {
            long parsed = Long.parseLong(configValue.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private void validatePositive(long value, String fieldName) {
        if (value <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, fieldName + "必须大于0");
        }
    }

    private void upsertConfig(String configKey, String configName, long configValue, String remark) {
        jdbcTemplate.update(
                """
                        insert into sys_config (
                            tenant_id, config_key, config_name, config_value, config_scope, is_system, remark,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, 1, ?, 0, 0, 0)
                        on duplicate key update
                            config_name = values(config_name),
                            config_value = values(config_value),
                            config_scope = values(config_scope),
                            is_system = values(is_system),
                            remark = values(remark),
                            updated_by = values(updated_by),
                            deleted = 0
                        """,
                PLATFORM_TENANT_ID,
                configKey,
                configName,
                String.valueOf(configValue),
                PLATFORM_SCOPE,
                remark
        );
    }

    public static class SecuritySettingsSnapshot {
        private long idleTimeoutSeconds;
        private long accessTokenExpireSeconds;
        private long refreshTokenExpireSeconds;

        public SecuritySettingsSnapshot() {
        }

        public SecuritySettingsSnapshot(long idleTimeoutSeconds, long accessTokenExpireSeconds, long refreshTokenExpireSeconds) {
            this.idleTimeoutSeconds = idleTimeoutSeconds;
            this.accessTokenExpireSeconds = accessTokenExpireSeconds;
            this.refreshTokenExpireSeconds = refreshTokenExpireSeconds;
        }

        public long getIdleTimeoutSeconds() {
            return idleTimeoutSeconds;
        }

        public void setIdleTimeoutSeconds(long idleTimeoutSeconds) {
            this.idleTimeoutSeconds = idleTimeoutSeconds;
        }

        public long getAccessTokenExpireSeconds() {
            return accessTokenExpireSeconds;
        }

        public void setAccessTokenExpireSeconds(long accessTokenExpireSeconds) {
            this.accessTokenExpireSeconds = accessTokenExpireSeconds;
        }

        public long getRefreshTokenExpireSeconds() {
            return refreshTokenExpireSeconds;
        }

        public void setRefreshTokenExpireSeconds(long refreshTokenExpireSeconds) {
            this.refreshTokenExpireSeconds = refreshTokenExpireSeconds;
        }
    }
}
