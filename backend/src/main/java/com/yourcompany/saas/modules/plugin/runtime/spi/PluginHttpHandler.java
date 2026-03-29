package com.yourcompany.saas.modules.plugin.runtime.spi;

import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpRequest;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpResponse;

public interface PluginHttpHandler {

    PluginHttpResponse handle(PluginHttpRequest request, PluginRuntimeContext context) throws Exception;

    default String requiredPermission(PluginHttpRequest request) {
        return null;
    }
}
