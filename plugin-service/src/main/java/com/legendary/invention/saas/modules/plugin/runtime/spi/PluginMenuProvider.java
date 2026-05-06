package com.legendary.invention.saas.modules.plugin.runtime.spi;

import com.legendary.invention.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.legendary.invention.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginDeclaredMenu;

import java.util.List;

public interface PluginMenuProvider {

    List<PluginDeclaredMenu> menus(PluginRuntimeContext context);
}
