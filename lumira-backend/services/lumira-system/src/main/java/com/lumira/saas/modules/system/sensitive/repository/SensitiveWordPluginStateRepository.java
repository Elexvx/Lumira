package com.lumira.saas.modules.system.sensitive.repository;

public interface SensitiveWordPluginStateRepository {
    boolean isPluginEnabled(String pluginCode);
    boolean hasRequiredSchema(int requiredColumnCount);
}
