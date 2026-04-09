package com.example.plugins.twofactor;

import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginDeclaredMenu;
import com.yourcompany.saas.modules.plugin.runtime.spi.PluginMenuProvider;

import java.util.List;

public class TwoFactorPluginMenuProvider implements PluginMenuProvider {

    @Override
    public List<PluginDeclaredMenu> menus(PluginRuntimeContext context) {
        return List.of(new PluginDeclaredMenu(
                "plugin.2fa",
                null,
                "2FA验证",
                "/plugins/2fa",
                "SafetyOutlined",
                "plugin:2fa:view",
                200
        ));
    }
}
