package com.legendary.invention.saas.modules.plugin.runtime.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;

public class PluginRuntimeContext {

    private final String pluginCode;
    private final String version;
    private final String platformVersion;
    private final Path pluginHome;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PluginRuntimeContext(
            String pluginCode,
            String version,
            String platformVersion,
            Path pluginHome,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.pluginCode = pluginCode;
        this.version = version;
        this.platformVersion = platformVersion;
        this.pluginHome = pluginHome;
        this.jdbcTemplate = jdbcTemplate;
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

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
