package com.example.plugins.announcement;

import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginDeclaredPermission;
import com.yourcompany.saas.modules.plugin.runtime.spi.PluginPermissionProvider;

import java.util.List;

public class AnnouncementPluginPermissionProvider implements PluginPermissionProvider {

    @Override
    public List<PluginDeclaredPermission> permissions(PluginRuntimeContext context) {
        return List.of(
                new PluginDeclaredPermission("plugin:announcement:view", "查看公告", "announcement"),
                new PluginDeclaredPermission("plugin:announcement:write", "维护公告", "announcement")
        );
    }
}
