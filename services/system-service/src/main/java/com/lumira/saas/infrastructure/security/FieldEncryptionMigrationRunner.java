package com.lumira.saas.infrastructure.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.infrastructure.AiSecretCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Component
public class FieldEncryptionMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FieldEncryptionMigrationRunner.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final MyBatisQueryOperations jdbcTemplate;
    private final FieldCryptoService fieldCryptoService;
    private final AiSecretCryptoService aiSecretCryptoService;
    private final ObjectMapper objectMapper;

    public FieldEncryptionMigrationRunner(
            MyBatisQueryOperations jdbcTemplate,
            FieldCryptoService fieldCryptoService,
            AiSecretCryptoService aiSecretCryptoService,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.fieldCryptoService = fieldCryptoService;
        this.aiSecretCryptoService = aiSecretCryptoService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!fieldCryptoService.isConfigured()) {
            log.warn("FIELD_SECRET is not configured; sensitive field encryption migration is skipped");
            return;
        }
        migrateSysConfig();
        migrateFileStorageSecrets();
        migrateVerificationBindings();
        migrateVerificationChallenges();
        migrateAiLlmApiKeys();
        migratePaymentProviderConfigs();
    }

    private void migrateSysConfig() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        select id, config_value as value
                        from sys_config
                        where deleted = 0
                          and config_value is not null
                          and config_value <> ''
                          and (
                            lower(config_key) like '%.password'
                            or lower(config_key) like '%.secret'
                            or lower(config_key) like '%.app-secret'
                            or lower(config_key) like '%.access-key-secret'
                            or lower(config_key) like '%.private-key'
                            or lower(config_key) like '%.token'
                            or lower(config_key) like '%.credential'
                          )
                        """
        );
        int migrated = 0;
        for (Map<String, Object> row : rows) {
            String value = text(row.get("value"));
            if (shouldEncrypt(value)) {
                jdbcTemplate.update("update sys_config set config_value = ?, updated_at = current_timestamp where id = ?", fieldCryptoService.encrypt(value), row.get("id"));
                migrated += 1;
            }
        }
        logMigration("sys_config", migrated);
    }

    private void migrateFileStorageSecrets() {
        migrateTextColumn("file_storage_space", "access_key_secret", "deleted = 0");
    }

    private void migrateVerificationBindings() {
        migrateTextColumn("sys_verification_binding", "secret_key", "deleted = 0");
        migrateJsonStringListColumn("sys_verification_binding", "recovery_codes_json", "deleted = 0");
    }

    private void migrateVerificationChallenges() {
        migrateTextColumn("sys_verification_challenge", "setup_secret", "deleted = 0");
        migrateJsonStringListColumn("sys_verification_challenge", "recovery_codes_json", "deleted = 0");
    }

    private void migrateAiLlmApiKeys() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        select id, api_key_encrypted as value
                        from ai_llm_service
                        where is_deleted = 0
                          and api_key_encrypted is not null
                          and api_key_encrypted <> ''
                        """
        );
        int migrated = 0;
        for (Map<String, Object> row : rows) {
            String value = text(row.get("value"));
            if (shouldEncrypt(value)) {
                String plainText = aiSecretCryptoService.decrypt(value);
                jdbcTemplate.update("update ai_llm_service set api_key_encrypted = ?, update_time = current_timestamp where id = ?", fieldCryptoService.encrypt(plainText), row.get("id"));
                migrated += 1;
            }
        }
        logMigration("ai_llm_service.api_key_encrypted", migrated);
    }

    private void migratePaymentProviderConfigs() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        select id, encrypted_config_json as value
                        from payment_provider_config
                        where deleted = 0
                          and encrypted_config_json is not null
                          and encrypted_config_json <> ''
                        """
        );
        int migrated = 0;
        for (Map<String, Object> row : rows) {
            String value = text(row.get("value"));
            if (shouldEncrypt(value)) {
                jdbcTemplate.update(
                        "update payment_provider_config set encrypted_config_json = ?, updated_at = current_timestamp where id = ?",
                        fieldCryptoService.encrypt(value),
                        row.get("id")
                );
                migrated += 1;
            }
        }
        logMigration("payment_provider_config.encrypted_config_json", migrated);
    }

    private void migrateTextColumn(String tableName, String columnName, String whereClause) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                ("select id, %s as value from %s where %s and %s is not null and %s <> ''")
                        .formatted(columnName, tableName, whereClause, columnName, columnName)
        );
        int migrated = 0;
        for (Map<String, Object> row : rows) {
            String value = text(row.get("value"));
            if (shouldEncrypt(value)) {
                jdbcTemplate.update(("update %s set %s = ?, updated_at = current_timestamp where id = ?").formatted(tableName, columnName), fieldCryptoService.encrypt(value), row.get("id"));
                migrated += 1;
            }
        }
        logMigration(tableName + "." + columnName, migrated);
    }

    private void migrateJsonStringListColumn(String tableName, String columnName, String whereClause) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                ("select id, %s as value from %s where %s and %s is not null")
                        .formatted(columnName, tableName, whereClause, columnName)
        );
        int migrated = 0;
        for (Map<String, Object> row : rows) {
            String value = text(row.get("value"));
            String encryptedJson = encryptJsonStringListValue(value);
            if (encryptedJson != null) {
                jdbcTemplate.update(("update %s set %s = ?, updated_at = current_timestamp where id = ?").formatted(tableName, columnName), encryptedJson, row.get("id"));
                migrated += 1;
            }
        }
        logMigration(tableName + "." + columnName, migrated);
    }

    private String encryptJsonStringListValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.startsWith("[")) {
                List<String> values = objectMapper.readValue(trimmed, STRING_LIST);
                return objectMapper.writeValueAsString(fieldCryptoService.encrypt(objectMapper.writeValueAsString(values)));
            }
            if (trimmed.startsWith("\"")) {
                String textValue = objectMapper.readValue(trimmed, String.class);
                if (!StringUtils.hasText(textValue) || fieldCryptoService.isEncrypted(textValue)) {
                    return null;
                }
                return objectMapper.writeValueAsString(fieldCryptoService.encrypt(textValue));
            }
            if (fieldCryptoService.isEncrypted(trimmed)) {
                return objectMapper.writeValueAsString(trimmed);
            }
            return objectMapper.writeValueAsString(fieldCryptoService.encrypt(trimmed));
        } catch (Exception exception) {
            log.warn("Failed to prepare encrypted JSON list value for migration", exception);
            return null;
        }
    }

    private boolean shouldEncrypt(String value) {
        return StringUtils.hasText(value) && !fieldCryptoService.isEncrypted(value);
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private void logMigration(String target, int migrated) {
        if (migrated > 0) {
            log.info("Migrated {} sensitive field(s) to AES-GCM storage for {}", migrated, target);
        }
    }
}
