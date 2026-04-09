package com.example.plugins.sms;

import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHealthReport;
import com.yourcompany.saas.modules.plugin.runtime.spi.PluginHealthIndicator;

public class SmsPluginHealthIndicator implements PluginHealthIndicator {

    @Override
    public PluginHealthReport healthCheck(PluginRuntimeContext context) {
        return PluginHealthReport.healthy("短信插件运行正常");
    }
}
