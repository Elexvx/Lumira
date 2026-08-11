package com.lumira.api.plugin;

/**
 * In-process lifecycle hook for a core plugin whose owned data lives in another
 * module of the modular monolith.
 */
public interface BuiltinPluginLifecycleHook {

    String pluginCode();

    default void onEnable(PluginLifecycleContext context) {
    }

    default void onDisable(PluginLifecycleContext context) {
    }

    record PluginLifecycleContext(Long operatorId, String operatorUuid) {
    }
}
