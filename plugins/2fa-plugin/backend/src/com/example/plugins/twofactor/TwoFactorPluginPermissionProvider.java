package com.example.plugins.twofactor;

import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginDeclaredPermission;
import com.yourcompany.saas.modules.plugin.runtime.spi.PluginPermissionProvider;

import java.util.List;

public class TwoFactorPluginPermissionProvider implements PluginPermissionProvider {

    @Override
    public List<PluginDeclaredPermission> permissions(PluginRuntimeContext context) {
        return List.of(
                new PluginDeclaredPermission("plugin:2fa:view", "查看 2FA 验证", "2fa"),
                new PluginDeclaredPermission("plugin:2fa:manage", "管理 2FA 验证", "2fa")
        );
    }
}
