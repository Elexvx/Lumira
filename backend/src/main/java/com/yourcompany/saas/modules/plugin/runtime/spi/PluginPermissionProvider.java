package com.yourcompany.saas.modules.plugin.runtime.spi;

import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginDeclaredPermission;

import java.util.List;

public interface PluginPermissionProvider {

    List<PluginDeclaredPermission> permissions(PluginRuntimeContext context);
}
