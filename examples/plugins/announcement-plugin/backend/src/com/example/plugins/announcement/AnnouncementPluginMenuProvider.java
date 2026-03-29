package com.example.plugins.announcement;

import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginDeclaredMenu;
import com.yourcompany.saas.modules.plugin.runtime.spi.PluginMenuProvider;

import java.util.List;

public class AnnouncementPluginMenuProvider implements PluginMenuProvider {

    @Override
    public List<PluginDeclaredMenu> menus(PluginRuntimeContext context) {
        return List.of(new PluginDeclaredMenu(
                "plugin.announcement",
                null,
                "公告管理",
                "/plugins/announcement",
                "NotificationOutlined",
                "plugin:announcement:view",
                200
        ));
    }
}
