package com.example.plugins.announcement;

import com.fasterxml.jackson.core.type.TypeReference;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpRequest;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpResponse;
import com.yourcompany.saas.modules.plugin.runtime.spi.PluginHttpHandler;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

public class AnnouncementPluginHttpHandler implements PluginHttpHandler {

    private static final TypeReference<Map<String, String>> BODY_TYPE = new TypeReference<>() {
    };

    @Override
    public PluginHttpResponse handle(PluginHttpRequest request, PluginRuntimeContext context) throws Exception {
        if ("GET".equalsIgnoreCase(request.method()) && "/list".equalsIgnoreCase(request.path())) {
            List<Map<String, Object>> items = context.getJdbcTemplate().query(
                    """
                            select id, title, content, created_at
                            from plugin_announcement_notice
                            where tenant_id = ?
                              and published_flag = 1
                            order by id desc
                            """,
                    (rs, rowNum) -> Map.of(
                            "id", rs.getLong("id"),
                            "title", rs.getString("title"),
                            "content", rs.getString("content"),
                            "createdAt", format(rs.getTimestamp("created_at"))
                    ),
                    request.tenantId()
            );
            return PluginHttpResponse.json(200, items);
        }
        if ("POST".equalsIgnoreCase(request.method()) && "/create".equalsIgnoreCase(request.path())) {
            Map<String, String> body = context.getObjectMapper().readValue(request.body(), BODY_TYPE);
            context.getJdbcTemplate().update(
                    """
                            insert into plugin_announcement_notice (tenant_id, title, content, published_flag)
                            values (?, ?, ?, 1)
                            """,
                    request.tenantId(),
                    body.getOrDefault("title", ""),
                    body.getOrDefault("content", "")
            );
            return PluginHttpResponse.json(200, Map.of("success", true));
        }
        return PluginHttpResponse.json(404, Map.of("message", "路径不存在"));
    }

    @Override
    public String requiredPermission(PluginHttpRequest request) {
        if ("GET".equalsIgnoreCase(request.method())) {
            return "plugin:announcement:view";
        }
        return "plugin:announcement:write";
    }

    private String format(Timestamp timestamp) {
        return timestamp == null ? "" : timestamp.toLocalDateTime().toString();
    }
}
