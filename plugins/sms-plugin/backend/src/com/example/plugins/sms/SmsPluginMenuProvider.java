package com.example.plugins.sms;

import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginDeclaredMenu;
import com.yourcompany.saas.modules.plugin.runtime.spi.PluginMenuProvider;

import java.util.List;

public class SmsPluginMenuProvider implements PluginMenuProvider {

    @Override
    public List<PluginDeclaredMenu> menus(PluginRuntimeContext context) {
        return List.of(new PluginDeclaredMenu(
                "plugin.sms",
                null,
                "短信验证码",
                "/plugins/sms",
                "MessageOutlined",
                "plugin:sms:view",
                201
        ));
    }
}
