package com.example.plugins.twofactor;

import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHealthReport;
import com.yourcompany.saas.modules.plugin.runtime.spi.PluginHealthIndicator;

public class TwoFactorPluginHealthIndicator implements PluginHealthIndicator {

    @Override
    public PluginHealthReport healthCheck(PluginRuntimeContext context) {
        return PluginHealthReport.healthy("2FA 插件运行正常");
    }
}
