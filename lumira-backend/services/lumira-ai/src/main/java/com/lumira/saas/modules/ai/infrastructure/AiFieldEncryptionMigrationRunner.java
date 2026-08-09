package com.lumira.saas.modules.ai.infrastructure;

import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.modules.ai.infrastructure.persistence.support.MyBatisQueryOperations;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI-owned startup guard for legacy plaintext LLM credentials.
 *
 * <p>Keeping this check beside the owning table prevents System from reading
 * AI persistence merely to perform a security migration preflight.</p>
 */
@Component
@ConditionalOnProperty(
        name = "saas.security.field-encryption-migration-check-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AiFieldEncryptionMigrationRunner implements ApplicationRunner {

    private final MyBatisQueryOperations database;

    public AiFieldEncryptionMigrationRunner(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long legacyPlaintextCount = database.queryForObject("""
                select count(1)
                from ai_llm_service
                where is_deleted = 0
                  and api_key_encrypted is not null
                  and api_key_encrypted <> ''
                  and api_key_encrypted not like ?
                """, Long.class, FieldCryptoService.PREFIX + "%");
        if (legacyPlaintextCount != null && legacyPlaintextCount > 0L) {
            throw new IllegalStateException(
                    "Legacy plaintext sensitive data detected; migrate ai_llm_service.api_key_encrypted="
                            + legacyPlaintextCount + " offline before starting"
            );
        }
    }
}
