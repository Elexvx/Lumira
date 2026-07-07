package com.lumira.ai.infrastructure.persistence;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;

abstract class JdbcAiRepositorySupport {
    protected final JdbcTemplate jdbcTemplate;

    protected JdbcAiRepositorySupport(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    protected Long insertAndReturnId(String sql, StatementBinder binder) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int inserted = jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            binder.bind(ps);
            return ps;
        }, keyHolder);
        requireSingleWrite(inserted, "AI repository insert changed, please retry");
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "新增记录失败");
        }
        return key.longValue();
    }

    protected void requireSingleWrite(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, message);
        }
    }

    protected Long requireTrustedUserId(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        Long userId = currentUser.getUserId();
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        return userId;
    }

    protected String requireTrustedUserUuid(CurrentUser currentUser) {
        requireTrustedUserId(currentUser);
        String userUuid = currentUser == null ? null : currentUser.getUserUuid();
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        return userUuid.trim();
    }

    protected boolean hasAllPermission(CurrentUser currentUser) {
        requireTrustedUserId(currentUser);
        return currentUser.getPermissions() != null && currentUser.getPermissions().contains("*");
    }

    @FunctionalInterface
    protected interface StatementBinder {
        void bind(PreparedStatement ps) throws java.sql.SQLException;
    }
}
