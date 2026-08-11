package com.lumira.saas.modules.plugin.service;

import com.lumira.api.plugin.PluginFeatureStateApi;
import com.lumira.saas.modules.plugin.mapper.PluginPersistenceMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service("pluginFeatureStateApi")
@Primary
public class PluginFeatureStateService implements PluginFeatureStateApi {

    private final PluginPersistenceMapper pluginPersistenceMapper;

    public PluginFeatureStateService(PluginPersistenceMapper pluginPersistenceMapper) {
        this.pluginPersistenceMapper = pluginPersistenceMapper;
    }

    @Override
    public boolean isPluginEnabled(String pluginCode) {
        if (!StringUtils.hasText(pluginCode)) {
            return false;
        }
        return Integer.valueOf(1).equals(pluginPersistenceMapper.isPluginEnabled(pluginCode.trim()));
    }

    @Override
    public boolean isPluginEnabledForWrite(String pluginCode) {
        if (!StringUtils.hasText(pluginCode)) {
            return false;
        }
        return Integer.valueOf(1).equals(pluginPersistenceMapper.isPluginEnabledForWrite(pluginCode.trim()));
    }
}
