package com.yourcompany.saas.modules.plugin.runtime.spi;

import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHealthReport;

public interface PluginHealthIndicator {

    PluginHealthReport healthCheck(PluginRuntimeContext context);
}
