package com.lumira.saas.infrastructure.security.service;

import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InitialPasswordChangeGuard {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String INITIAL_ADMIN_PASSWORD = "123456";

    private final MyBatisQueryOperations jdbcTemplate;
    private final ObjectProvider<PasswordEncoder> passwordEncoderProvider;

    public InitialPasswordChangeGuard(MyBatisQueryOperations jdbcTemplate, ObjectProvider<PasswordEncoder> passwordEncoderProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoderProvider = passwordEncoderProvider;
    }

    public boolean requiresPasswordChange(CurrentUser currentUser) {
        if (currentUser == null || !DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(currentUser.getUsername())) {
            return false;
        }

        String passwordHash = jdbcTemplate.queryForObject(
                "select password_hash from sys_user where username = ? and deleted = 0 limit 1",
                String.class,
                DEFAULT_ADMIN_USERNAME
        );
        return StringUtils.hasText(passwordHash)
                && passwordEncoderProvider.getObject().matches(INITIAL_ADMIN_PASSWORD, passwordHash);
    }
}
