package com.lumira.saas.infrastructure.security;

import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FieldEncryptionMigrationRunner implements ApplicationRunner {

    private final MyBatisQueryOperations jdbcTemplate;

    public FieldEncryptionMigrationRunner(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> legacyPlaintextTargets = new ArrayList<>();
        check(legacyPlaintextTargets, "sys_config.config_value", """
                select count(1)
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
                  and config_value not like ?
                """, encryptedPrefixPattern());
        check(legacyPlaintextTargets, "file_storage_space.access_key_secret", """
                select count(1)
                from file_storage_space
                where deleted = 0
                  and access_key_secret is not null
                  and access_key_secret <> ''
                  and access_key_secret not like ?
                """, encryptedPrefixPattern());
        check(legacyPlaintextTargets, "sys_verification_binding.secret_key", """
                select count(1)
                from sys_verification_binding
                where deleted = 0
                  and secret_key is not null
                  and secret_key <> ''
                  and secret_key not like ?
                """, encryptedPrefixPattern());
        check(legacyPlaintextTargets, "sys_verification_binding.recovery_codes_json", """
                select count(1)
                from sys_verification_binding
                where deleted = 0
                  and recovery_codes_json is not null
                  and trim(recovery_codes_json) <> ''
                  and trim(recovery_codes_json) not like ?
                  and trim(recovery_codes_json) not like ?
                """, quotedEncryptedPrefixPattern(), encryptedPrefixPattern());
        check(legacyPlaintextTargets, "sys_verification_challenge.setup_secret", """
                select count(1)
                from sys_verification_challenge
                where deleted = 0
                  and setup_secret is not null
                  and setup_secret <> ''
                  and setup_secret not like ?
                """, encryptedPrefixPattern());
        check(legacyPlaintextTargets, "sys_verification_challenge.recovery_codes_json", """
                select count(1)
                from sys_verification_challenge
                where deleted = 0
                  and recovery_codes_json is not null
                  and trim(recovery_codes_json) <> ''
                  and trim(recovery_codes_json) not like ?
                  and trim(recovery_codes_json) not like ?
                """, quotedEncryptedPrefixPattern(), encryptedPrefixPattern());
        check(legacyPlaintextTargets, "ai_llm_service.api_key_encrypted", """
                select count(1)
                from ai_llm_service
                where is_deleted = 0
                  and api_key_encrypted is not null
                  and api_key_encrypted <> ''
                  and api_key_encrypted not like ?
                """, encryptedPrefixPattern());
        check(legacyPlaintextTargets, "payment_provider_config.encrypted_config_json", """
                select count(1)
                from payment_provider_config
                where deleted = 0
                  and encrypted_config_json is not null
                  and encrypted_config_json <> ''
                  and encrypted_config_json not like ?
                """, encryptedPrefixPattern());

        if (!legacyPlaintextTargets.isEmpty()) {
            throw new IllegalStateException(
                    "Legacy plaintext sensitive data detected; startup no longer mutates database automatically. "
                            + "Migrate these fields offline before starting: "
                            + String.join(", ", legacyPlaintextTargets)
            );
        }
    }

    private void check(List<String> targets, String label, String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        if (count != null && count > 0) {
            targets.add(label + "=" + count);
        }
    }

    private String encryptedPrefixPattern() {
        return FieldCryptoService.PREFIX + "%";
    }

    private String quotedEncryptedPrefixPattern() {
        return "\"" + FieldCryptoService.PREFIX + "%";
    }
}
