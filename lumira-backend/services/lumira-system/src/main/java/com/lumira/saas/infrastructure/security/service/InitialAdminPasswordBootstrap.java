package com.lumira.saas.infrastructure.security.service;

import com.lumira.common.security.InitialAdminPassword;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InitialAdminPasswordBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminPasswordBootstrap.class);
    private static final String DEFAULT_ADMIN_USERNAME = "admin";

    private final MyBatisQueryOperations jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public InitialAdminPasswordBootstrap(
            MyBatisQueryOperations jdbcTemplate,
            PasswordEncoder passwordEncoder,
            Environment environment
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String configuredInitialPassword = InitialAdminPassword.resolve(environment);
        Long adminUserId = jdbcTemplate.queryForObject(
                "select id from sys_user where username = ? and deleted = 0 limit 1",
                Long.class,
                DEFAULT_ADMIN_USERNAME
        );
        if (adminUserId == null) {
            log.warn("Initial admin password bootstrap skipped because admin user is missing");
            return;
        }

        String currentPasswordHash = jdbcTemplate.queryForObject(
                "select password_hash from sys_user where id = ? and deleted = 0 limit 1",
                String.class,
                adminUserId
        );
        if (!StringUtils.hasText(currentPasswordHash)) {
            log.warn("Initial admin password bootstrap skipped because admin password hash is missing");
            return;
        }
        if (passwordEncoder.matches(configuredInitialPassword, currentPasswordHash)) {
            return;
        }
        if (!passwordEncoder.matches(InitialAdminPassword.DEFAULT_PASSWORD, currentPasswordHash)) {
            log.info("Initial admin password bootstrap skipped because admin password is no longer the factory default");
            return;
        }

        String nextPasswordHash = passwordEncoder.encode(configuredInitialPassword);
        jdbcTemplate.update(
                "update sys_user set password_hash = ?, updated_by = ?, updated_at = current_timestamp where id = ? and deleted = 0",
                nextPasswordHash,
                0L,
                adminUserId
        );
        jdbcTemplate.update(
                """
                        insert into iam_user_credential (
                            user_id, credential_type, credential_secret, algorithm, version, status, deleted
                        ) values (?, 'PASSWORD', ?, 'BCRYPT', 1, 'ENABLED', 0)
                        on duplicate key update
                            credential_secret = values(credential_secret),
                            algorithm = values(algorithm),
                            status = values(status),
                            last_changed_at = current_timestamp,
                            updated_at = current_timestamp,
                            deleted = values(deleted)
                        """,
                adminUserId,
                nextPasswordHash
        );
        log.info("Initial admin password bootstrap applied configured password override");
    }
}
