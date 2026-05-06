package com.legendary.invention.saas.modules.plugin.runtime.spi;

import com.legendary.invention.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.legendary.invention.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginScheduledTask;

import java.util.List;

public interface PluginScheduledTaskProvider {

    List<PluginScheduledTask> scheduledTasks(PluginRuntimeContext context);
}
