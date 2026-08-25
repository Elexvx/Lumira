package com.lumira.alerting.support;

import com.lumira.alerting.infrastructure.AlertingRepository;
import com.lumira.api.plugin.BuiltinPluginLifecycleHook;
import org.springframework.stereotype.Component;

@Component
public class BuiltinAlertingLifecycleHook implements BuiltinPluginLifecycleHook {
    private final AlertingRepository repository;

    public BuiltinAlertingLifecycleHook(AlertingRepository repository) {
        this.repository = repository;
    }

    @Override
    public String pluginCode() {
        return AlertingRepository.PLUGIN_CODE;
    }

    @Override
    public void onEnable(PluginLifecycleContext context) {
        repository.resumePendingDeliveries();
    }

    @Override
    public void onDisable(PluginLifecycleContext context) {
        repository.pausePendingDeliveries();
    }
}
