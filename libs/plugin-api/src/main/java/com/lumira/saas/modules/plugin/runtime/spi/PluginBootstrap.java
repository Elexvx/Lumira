package com.lumira.saas.modules.plugin.runtime.spi;

import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;

public interface PluginBootstrap {

    void initialize(PluginRuntimeContext context) throws Exception;

    default void destroy(PluginRuntimeContext context) throws Exception {
    }
}
