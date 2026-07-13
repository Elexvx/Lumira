package com.lumira.saas.modules.system.workorder.repository;

public interface WorkOrderPluginStateRepository {
    boolean isPluginEnabled(String pluginCode);
    boolean hasFeedbackTable();
}
