package com.example.plugins.announcement;

import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHealthReport;
import com.yourcompany.saas.modules.plugin.runtime.spi.PluginHealthIndicator;

import java.util.Map;

public class AnnouncementPluginHealthIndicator implements PluginHealthIndicator {

    @Override
    public PluginHealthReport healthCheck(PluginRuntimeContext context) {
        Integer count = context.getJdbcTemplate().queryForObject(
                "select count(1) from plugin_announcement_notice",
                Integer.class
        );
        return new PluginHealthReport(true, "公告插件健康检查通过", Map.of("noticeCount", count == null ? 0 : count));
    }
}
