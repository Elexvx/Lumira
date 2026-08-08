package com.lumira.saas.modules.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lumira.saas.modules.ai.infrastructure.persistence.support.MyBatisQueryOperations;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class AiFieldEncryptionMigrationRunnerTest {

    @Test
    void allowsStartupWhenAiCredentialIsAlreadyEncrypted() {
        AiFieldEncryptionMigrationRunner runner = new AiFieldEncryptionMigrationRunner(new CountingOperations(0L));

        assertThatCode(() -> runner.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void rejectsLegacyPlaintextAiCredentialWithinAiOwner() {
        AiFieldEncryptionMigrationRunner runner = new AiFieldEncryptionMigrationRunner(new CountingOperations(1L));

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ai_llm_service.api_key_encrypted=1");
    }

    private static final class CountingOperations extends MyBatisQueryOperations {
        private final Long count;

        private CountingOperations(Long count) {
            this.count = count;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return requiredType.cast(count);
        }
    }
}
