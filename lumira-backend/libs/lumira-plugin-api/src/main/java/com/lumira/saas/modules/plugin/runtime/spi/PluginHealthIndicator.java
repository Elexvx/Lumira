package com.lumira.saas.modules.plugin.runtime.spi;

import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHealthReport;

public interface PluginHealthIndicator {

    PluginHealthReport healthCheck(PluginRuntimeContext context);
}
