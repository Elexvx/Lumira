package com.example.plugins.sms;

import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginDeclaredPermission;
import com.yourcompany.saas.modules.plugin.runtime.spi.PluginPermissionProvider;

import java.util.List;

public class SmsPluginPermissionProvider implements PluginPermissionProvider {

    @Override
    public List<PluginDeclaredPermission> permissions(PluginRuntimeContext context) {
        return List.of(
                new PluginDeclaredPermission("plugin:sms:view", "查看短信验证", "sms"),
                new PluginDeclaredPermission("plugin:sms:manage", "管理短信验证", "sms")
        );
    }
}
