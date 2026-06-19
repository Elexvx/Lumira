package com.lumira.saas.modules.plugin.runtime.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;

public class PluginRuntimeContext {

    private final String pluginCode;
    private final String version;
    private final String platformVersion;
    private final Path pluginHome;
    private final Object dataOperations;
    private final ObjectMapper objectMapper;

    public PluginRuntimeContext(
            String pluginCode,
            String version,
            String platformVersion,
            Path pluginHome,
            Object dataOperations,
            ObjectMapper objectMapper
    ) {
        this.pluginCode = pluginCode;
        this.version = version;
        this.platformVersion = platformVersion;
        this.pluginHome = pluginHome;
        this.dataOperations = dataOperations;
        this.objectMapper = objectMapper;
    }

    public String getPluginCode() {
        return pluginCode;
    }

    public String getVersion() {
        return version;
    }

    public String getPlatformVersion() {
        return platformVersion;
    }

    public Path getPluginHome() {
        return pluginHome;
    }

    public Object getDataOperations() {
        return dataOperations;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
