package com.legendary.invention.saas.modules.plugin.runtime.spi;

import com.legendary.invention.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.legendary.invention.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginDeclaredPermission;

import java.util.List;

public interface PluginPermissionProvider {

    List<PluginDeclaredPermission> permissions(PluginRuntimeContext context);
}
