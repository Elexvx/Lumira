package com.legendary.invention.saas.modules.plugin.runtime.spi;

import com.legendary.invention.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.legendary.invention.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHealthReport;

public interface PluginHealthIndicator {

    PluginHealthReport healthCheck(PluginRuntimeContext context);
}
