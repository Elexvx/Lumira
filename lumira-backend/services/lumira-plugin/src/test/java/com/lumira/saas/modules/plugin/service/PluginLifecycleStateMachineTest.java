package com.lumira.saas.modules.plugin.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginLifecycleStateMachineTest {
    @Test
    void happyPathIsExplicit() {
        assertThatCode(() -> {
            PluginLifecycleStateMachine.requireTransition(PluginLifecycleStateMachine.State.UPLOADED, PluginLifecycleStateMachine.State.VERIFIED);
            PluginLifecycleStateMachine.requireTransition(PluginLifecycleStateMachine.State.VERIFIED, PluginLifecycleStateMachine.State.MIGRATION_PENDING);
            PluginLifecycleStateMachine.requireTransition(PluginLifecycleStateMachine.State.MIGRATION_PENDING, PluginLifecycleStateMachine.State.MIGRATED);
            PluginLifecycleStateMachine.requireTransition(PluginLifecycleStateMachine.State.MIGRATED, PluginLifecycleStateMachine.State.RUNTIME_VERIFIED);
            PluginLifecycleStateMachine.requireTransition(PluginLifecycleStateMachine.State.RUNTIME_VERIFIED, PluginLifecycleStateMachine.State.ACTIVATED);
        }).doesNotThrowAnyException();
    }

    @Test
    void activationCannotSkipMigrationAndRuntimeVerification() {
        assertThatThrownBy(() -> PluginLifecycleStateMachine.requireTransition(
                PluginLifecycleStateMachine.State.VERIFIED, PluginLifecycleStateMachine.State.ACTIVATED))
                .isInstanceOf(IllegalStateException.class);
    }
}
