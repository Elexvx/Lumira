package com.lumira.saas.infrastructure.security;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldEncryptionMigrationRunnerTest {

    @Test
    void allowsStartupWhenSensitiveFieldsAreAlreadyEncrypted() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        FieldEncryptionMigrationRunner runner = new FieldEncryptionMigrationRunner(jdbcTemplate);

        assertDoesNotThrow(() -> runner.run(new DefaultApplicationArguments()));
    }

    @Test
    void rejectsLegacyPlaintextSensitiveFields() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations()
                .withCount("from sys_config", 2L);
        FieldEncryptionMigrationRunner runner = new FieldEncryptionMigrationRunner(jdbcTemplate);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments())
        );

        assertTrue(exception.getMessage().contains("sys_config.config_value=2"));
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {

        private final Map<String, Long> countsBySqlNeedle = new LinkedHashMap<>();

        private RecordingQueryOperations withCount(String sqlNeedle, long count) {
            countsBySqlNeedle.put(sqlNeedle, count);
            return this;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            for (Map.Entry<String, Long> entry : countsBySqlNeedle.entrySet()) {
                if (sql.contains(entry.getKey())) {
                    return requiredType.cast(entry.getValue());
                }
            }
            return requiredType.cast(0L);
        }
    }
}
