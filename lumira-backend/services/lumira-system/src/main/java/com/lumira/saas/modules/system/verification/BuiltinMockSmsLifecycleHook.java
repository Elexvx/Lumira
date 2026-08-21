package com.lumira.saas.modules.system.verification;

import com.lumira.api.plugin.BuiltinPluginLifecycleHook;
import com.lumira.saas.modules.system.support.BuiltinMockSmsAvailability;
import org.springframework.stereotype.Component;

@Component
public class BuiltinMockSmsLifecycleHook implements BuiltinPluginLifecycleHook {

    private final SystemVerificationSettingsAppService settingsAppService;

    public BuiltinMockSmsLifecycleHook(SystemVerificationSettingsAppService settingsAppService) {
        this.settingsAppService = settingsAppService;
    }

    @Override
    public String pluginCode() {
        return BuiltinMockSmsAvailability.PLUGIN_CODE;
    }

    @Override
    public void onEnable(PluginLifecycleContext context) {
        settingsAppService.onBuiltinMockSmsPluginStateChanged("enabled");
    }

    @Override
    public void onDisable(PluginLifecycleContext context) {
        settingsAppService.onBuiltinMockSmsPluginStateChanged("disabled");
    }
}
