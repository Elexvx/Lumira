package com.lumira.saas.modules.config.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DatabaseVersionStartupRecorder {

    private static final Logger log = LoggerFactory.getLogger(DatabaseVersionStartupRecorder.class);
    private static final String CONFIG_KEY = "platform.database.version";

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    public DatabaseVersionStartupRecorder(JdbcTemplate jdbcTemplate, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recordDatabaseVersion() {
        String databaseVersion = environment.getProperty("DATABASE_VERSION");
        if (!StringUtils.hasText(databaseVersion)) {
            log.debug("Database version startup record skipped: DATABASE_VERSION is empty.");
            return;
        }

        String payload = "{"
                + jsonPair("databaseVersion", databaseVersion) + ","
                + jsonPair("frontendVersion", environment.getProperty("FRONTEND_VERSION")) + ","
                + jsonPair("backendVersion", firstText(environment.getProperty("BACKEND_VERSION"), environment.getProperty("BUILD_VERSION"))) + ","
                + jsonPair("buildTime", environment.getProperty("BUILD_TIME")) + ","
                + jsonPair("gitCommit", environment.getProperty("GIT_COMMIT")) + ","
                + jsonPair("gitBranch", environment.getProperty("GIT_BRANCH"))
                + "}";

        jdbcTemplate.update("""
                INSERT INTO `sys_config` (
                    `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`,
                    `created_by`, `updated_by`, `deleted`
                )
                VALUES (?, 'Database version', ?, 'PLATFORM', 1, 'Generated database identity for the current startup', 0, 0, 0)
                ON DUPLICATE KEY UPDATE
                    `config_name` = VALUES(`config_name`),
                    `config_value` = VALUES(`config_value`),
                    `config_scope` = VALUES(`config_scope`),
                    `is_system` = VALUES(`is_system`),
                    `remark` = VALUES(`remark`),
                    `updated_by` = VALUES(`updated_by`),
                    `updated_at` = CURRENT_TIMESTAMP,
                    `deleted` = 0
                """, CONFIG_KEY, payload);
        log.info("Recorded database version {}", databaseVersion);
    }

    private static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String jsonPair(String key, String value) {
        return "\"" + escapeJson(key) + "\":\"" + escapeJson(value == null ? "" : value) + "\"";
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index += 1) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.toString();
    }
}
