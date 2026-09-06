package com.lumira.asyncruntime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecoveryFenceRegistryTest {
    private static final String TOKEN_ONE = "recovery-token-one-1234567890";
    private static final String TOKEN_TWO = "recovery-token-two-1234567890";

    @Test
    void rejectsOldEpochAndSameEpochWithDifferentToken() {
        RecoveryFenceRegistry registry = new RecoveryFenceRegistry();

        registry.assertCurrent("payment", 10L, TOKEN_ONE);

        assertThatThrownBy(() -> registry.assertCurrent("payment", 9L, TOKEN_ONE))
                .isInstanceOf(RecoveryFenceRegistry.StaleRecoveryFenceException.class);
        assertThatThrownBy(() -> registry.assertCurrent("payment", 10L, TOKEN_TWO))
                .isInstanceOf(RecoveryFenceRegistry.StaleRecoveryFenceException.class);
        assertThat(registry.currentEpoch("payment")).isEqualTo(10L);
    }

    @Test
    void permitsIdempotentRetryAndHigherEpochTakeover() {
        RecoveryFenceRegistry registry = new RecoveryFenceRegistry();

        registry.assertCurrent("file", 10L, TOKEN_ONE);
        assertThatCode(() -> registry.assertCurrent("file", 10L, TOKEN_ONE)).doesNotThrowAnyException();
        assertThatCode(() -> registry.assertCurrent("file", 11L, TOKEN_TWO)).doesNotThrowAnyException();
        assertThat(registry.currentEpoch("file")).isEqualTo(11L);
    }

    @Test
    void bindsRelayTakeoverToTheValidatedRecoveryEpoch() {
        RecoveryFenceRegistry registry = new RecoveryFenceRegistry();

        registry.assertCurrent("plugin", 10L, TOKEN_ONE);
        registry.assertCurrent("plugin", 11L, TOKEN_TWO);

        assertThatThrownBy(() -> registry.takeover("plugin", "job-recovery", 10L, TOKEN_ONE))
                .isInstanceOf(RecoveryFenceRegistry.StaleRecoveryFenceException.class);
        assertThatCode(() -> registry.takeover("plugin", "job-recovery", 11L, TOKEN_TWO))
                .doesNotThrowAnyException();
    }
}
