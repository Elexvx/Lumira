package com.lumira.api.plugin;

/**
 * Read-only internal contract for feature modules that are guarded by a plugin.
 *
 * <p>The write probe is expected to take a shared database lock for the duration
 * of the caller transaction. This lets plugin disable wait for in-flight writes
 * and prevents new writes after the disable transaction commits.</p>
 */
public interface PluginFeatureStateApi {

    boolean isPluginEnabled(String pluginCode);

    boolean isPluginEnabledForWrite(String pluginCode);
}
