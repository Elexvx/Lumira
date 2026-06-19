package com.lumira.saas.modules.plugin.registry;

import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginDeclaredMenu;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginDeclaredPermission;
import com.lumira.saas.modules.plugin.runtime.spi.PluginBootstrap;
import com.lumira.saas.modules.plugin.runtime.spi.PluginHealthIndicator;
import com.lumira.saas.modules.plugin.runtime.spi.PluginHttpHandler;
import com.lumira.saas.modules.plugin.runtime.spi.PluginSecondFactorProvider;

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

public class PluginRuntimeDescriptor {

    private final String pluginCode;
    private final String version;
    private final URLClassLoader classLoader;
    private final PluginRuntimeContext runtimeContext;
    private final PluginBootstrap bootstrap;
    private final PluginHttpHandler httpHandler;
    private final PluginHealthIndicator healthIndicator;
    private final PluginSecondFactorProvider secondFactorProvider;
    private final List<PluginDeclaredPermission> permissions;
    private final List<PluginDeclaredMenu> menus;
    private final List<ScheduledExecutorService> scheduledExecutors;

    public PluginRuntimeDescriptor(
            String pluginCode,
            String version,
            URLClassLoader classLoader,
            PluginRuntimeContext runtimeContext,
            PluginBootstrap bootstrap,
            PluginHttpHandler httpHandler,
            PluginHealthIndicator healthIndicator,
            PluginSecondFactorProvider secondFactorProvider,
            List<PluginDeclaredPermission> permissions,
            List<PluginDeclaredMenu> menus,
            List<ScheduledExecutorService> scheduledExecutors
    ) {
        this.pluginCode = pluginCode;
        this.version = version;
        this.classLoader = classLoader;
        this.runtimeContext = runtimeContext;
        this.bootstrap = bootstrap;
        this.httpHandler = httpHandler;
        this.healthIndicator = healthIndicator;
        this.secondFactorProvider = secondFactorProvider;
        this.permissions = permissions;
        this.menus = menus;
        this.scheduledExecutors = scheduledExecutors;
    }

    public String getPluginCode() {
        return pluginCode;
    }

    public String getVersion() {
        return version;
    }

    public URLClassLoader getClassLoader() {
        return classLoader;
    }

    public PluginRuntimeContext getRuntimeContext() {
        return runtimeContext;
    }

    public PluginBootstrap getBootstrap() {
        return bootstrap;
    }

    public PluginHttpHandler getHttpHandler() {
        return httpHandler;
    }

    public PluginHealthIndicator getHealthIndicator() {
        return healthIndicator;
    }

    public PluginSecondFactorProvider getSecondFactorProvider() {
        return secondFactorProvider;
    }

    public List<PluginDeclaredPermission> getPermissions() {
        return permissions;
    }

    public List<PluginDeclaredMenu> getMenus() {
        return menus;
    }

    public void close() throws Exception {
        for (ScheduledExecutorService executor : scheduledExecutors) {
            executor.shutdownNow();
        }
        bootstrap.destroy(runtimeContext);
        try {
            classLoader.close();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
