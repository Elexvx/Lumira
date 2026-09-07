package com.lumira.saas.modules.plugin.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class PluginLifecycleStateMachine {

    public enum State {
        UPLOADED,
        VERIFIED,
        MIGRATION_PENDING,
        MIGRATED,
        RUNTIME_VERIFIED,
        ACTIVATED,
        FAILED,
        ROLLBACK_BLOCKED
    }

    private static final Map<State, EnumSet<State>> TRANSITIONS = new EnumMap<>(State.class);

    static {
        allow(State.UPLOADED, State.VERIFIED, State.FAILED);
        allow(State.VERIFIED, State.MIGRATION_PENDING, State.MIGRATED, State.FAILED);
        allow(State.MIGRATION_PENDING, State.MIGRATED, State.FAILED, State.ROLLBACK_BLOCKED);
        allow(State.MIGRATED, State.RUNTIME_VERIFIED, State.FAILED, State.ROLLBACK_BLOCKED);
        allow(State.RUNTIME_VERIFIED, State.ACTIVATED, State.FAILED, State.ROLLBACK_BLOCKED);
        allow(State.ACTIVATED, State.ROLLBACK_BLOCKED, State.FAILED);
        allow(State.FAILED, State.MIGRATION_PENDING, State.ROLLBACK_BLOCKED);
        allow(State.ROLLBACK_BLOCKED, State.MIGRATION_PENDING, State.FAILED);
    }

    private PluginLifecycleStateMachine() {
    }

    public static void requireTransition(State from, State to) {
        if (from == to) {
            return;
        }
        if (!TRANSITIONS.getOrDefault(from, EnumSet.noneOf(State.class)).contains(to)) {
            throw new IllegalStateException("Illegal plugin lifecycle transition: " + from + " -> " + to);
        }
    }

    private static void allow(State from, State... targets) {
        TRANSITIONS.put(from, EnumSet.of(targets[0], targets));
    }
}
