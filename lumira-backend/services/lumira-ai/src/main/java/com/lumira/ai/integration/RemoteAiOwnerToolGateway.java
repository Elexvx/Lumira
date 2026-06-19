package com.lumira.ai.integration;

import com.lumira.ai.config.AiOwnerIntegrationProperties;
import com.lumira.ai.vo.AiToolVO;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.system.MenuNodeDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.common.security.CurrentUser;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RemoteAiOwnerToolGateway implements AiOwnerToolGateway {

    private static final String INTERNAL_TOKEN_HEADER = "X-Job-Token";

    private final AiOwnerIntegrationProperties properties;
    private final RestClient.Builder restClientBuilder;

    public RemoteAiOwnerToolGateway(AiOwnerIntegrationProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public ToolExecution execute(CurrentUser currentUser, AiToolVO tool, Map<String, Object> arguments) {
        return switch (tool.toolCode()) {
            case "system.permission.snapshot" -> permissionSnapshot(currentUser);
            case "system.config.read" -> platformConfig(currentUser, arguments);
            case "system.menu.list" -> builtinMenus();
            case "file.object.search" -> searchFiles(currentUser, arguments);
            case "system.user.search", "audit.ai_call.search" -> degraded(tool, "该工具仍处于只读远端契约补齐阶段", arguments);
            case "system.user.create", "system.user.update" -> new ToolExecution(Map.of(
                    "dryRun", true,
                    "message", "独立 AI 服务已接管确认链路；真实 IAM 写入必须通过 IAM owner API。",
                    "arguments", arguments
            ), false, false);
            default -> degraded(tool, "未声明的 AI owner 工具", arguments);
        };
    }

    @Override
    public List<String> configuredOwners() {
        List<String> owners = new ArrayList<>();
        if (properties.getIam().configured()) {
            owners.add("iam");
        }
        if (properties.getPlatform().configured()) {
            owners.add("platform");
        }
        if (properties.getFile().configured()) {
            owners.add("file");
        }
        return owners;
    }

    @Override
    public List<String> degradedOwners() {
        List<String> owners = new ArrayList<>();
        if (!properties.getIam().configured()) {
            owners.add("iam");
        }
        if (!properties.getPlatform().configured()) {
            owners.add("platform");
        }
        if (!properties.getFile().configured()) {
            owners.add("file");
        }
        return owners;
    }

    private ToolExecution permissionSnapshot(CurrentUser currentUser) {
        if (!properties.getIam().configured()) {
            return localPermissionSnapshot(currentUser, "iam-owner-not-configured");
        }
        try {
            PermissionSnapshotDTO snapshot = client(properties.getIam())
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/system/permissions/snapshot")
                            .queryParam("tenantId", currentUser.getCurrentTenantId())
                            .queryParam("userId", currentUser.getUserId())
                            .build())
                    .retrieve()
                    .body(PermissionSnapshotDTO.class);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tenantId", currentUser.getCurrentTenantId());
            data.put("userId", currentUser.getUserId());
            data.put("version", snapshot == null ? null : snapshot.version());
            data.put("permissions", snapshot == null ? List.of() : snapshot.permissions());
            data.put("roleIds", snapshot == null ? List.of() : snapshot.roleIds());
            data.put("defaultHomePath", snapshot == null ? null : snapshot.defaultHomePath());
            return new ToolExecution(data, true, false);
        } catch (RuntimeException exception) {
            return localPermissionSnapshot(currentUser, "iam-owner-call-failed");
        }
    }

    private ToolExecution localPermissionSnapshot(CurrentUser currentUser, String reason) {
        return new ToolExecution(Map.of(
                "userId", currentUser.getUserId(),
                "tenantId", currentUser.getCurrentTenantId(),
                "username", currentUser.getUsername(),
                "permissions", currentUser.getPermissions(),
                "degradedReason", reason
        ), false, true);
    }

    private ToolExecution platformConfig(CurrentUser currentUser, Map<String, Object> arguments) {
        if (!properties.getPlatform().configured()) {
            return degraded("platform", "platform-owner-not-configured", arguments);
        }
        List<String> keys = configKeys(arguments);
        if (keys.isEmpty()) {
            return new ToolExecution(Map.of("values", Map.of(), "limitedBy", "empty-config-key-list"), false, false);
        }
        try {
            Map<?, ?> values = client(properties.getPlatform())
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/system/config/platform-values")
                            .queryParam("tenantId", currentUser.getCurrentTenantId())
                            .queryParam("keys", keys.toArray())
                            .build())
                    .retrieve()
                    .body(Map.class);
            return new ToolExecution(Map.of("keys", keys, "values", values == null ? Map.of() : values), true, false);
        } catch (RuntimeException exception) {
            return degraded("platform", "platform-owner-call-failed", arguments);
        }
    }

    private ToolExecution builtinMenus() {
        if (!properties.getPlatform().configured()) {
            return degraded("platform", "platform-owner-not-configured", Map.of());
        }
        try {
            MenuNodeDTO[] menus = client(properties.getPlatform())
                    .get()
                    .uri("/internal/system/menus/builtin")
                    .retrieve()
                    .body(MenuNodeDTO[].class);
            return new ToolExecution(Map.of("menus", menus == null ? List.of() : Arrays.asList(menus)), true, false);
        } catch (RuntimeException exception) {
            return degraded("platform", "platform-owner-call-failed", Map.of());
        }
    }

    private ToolExecution searchFiles(CurrentUser currentUser, Map<String, Object> arguments) {
        if (!properties.getFile().configured()) {
            return degraded("file", "file-owner-not-configured", arguments);
        }
        try {
            FileObjectDTO[] files = client(properties.getFile())
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/files/search")
                            .queryParam("tenantId", currentUser.getCurrentTenantId())
                            .queryParam("userId", currentUser.getUserId())
                            .queryParam("username", currentUser.getUsername())
                            .queryParam("keyword", stringArg(arguments, "keyword"))
                            .queryParam("contentType", stringArg(arguments, "contentType"))
                            .queryParam("status", stringArg(arguments, "status"))
                            .queryParam("tenantScope", booleanArg(arguments, "tenantScope", true))
                            .queryParam("limit", intArg(arguments, "limit", 20, 1, 50))
                            .build())
                    .retrieve()
                    .body(FileObjectDTO[].class);
            return new ToolExecution(Map.of("files", files == null ? List.of() : Arrays.asList(files)), true, false);
        } catch (RuntimeException exception) {
            return degraded("file", "file-owner-call-failed", arguments);
        }
    }

    private RestClient client(AiOwnerIntegrationProperties.OwnerEndpoint endpoint) {
        RestClient.Builder builder = restClientBuilder.clone()
                .baseUrl(endpoint.getBaseUrl());
        if (StringUtils.hasText(properties.getInternalToken())) {
            builder.defaultHeader(INTERNAL_TOKEN_HEADER, properties.getInternalToken());
        }
        builder.defaultHeader(HttpHeaders.ACCEPT, "application/json");
        return builder.build();
    }

    private ToolExecution degraded(AiToolVO tool, String reason, Map<String, Object> arguments) {
        return degraded(tool.toolCode(), reason, arguments);
    }

    private ToolExecution degraded(String ownerOrTool, String reason, Map<String, Object> arguments) {
        return new ToolExecution(Map.of(
                "result", List.of(),
                "limitedBy", "standalone-ai-service",
                "ownerOrTool", ownerOrTool,
                "degradedReason", reason,
                "arguments", arguments
        ), false, true);
    }

    private List<String> configKeys(Map<String, Object> arguments) {
        Object keys = arguments.get("keys");
        if (keys instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .limit(50)
                    .toList();
        }
        String key = stringArg(arguments, "key");
        return StringUtils.hasText(key) ? List.of(key) : List.of();
    }

    private String stringArg(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private boolean booleanArg(Map<String, Object> arguments, String key, boolean defaultValue) {
        Object value = arguments.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private int intArg(Map<String, Object> arguments, String key, int defaultValue, int min, int max) {
        Object value = arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Math.min(Math.max(Integer.parseInt(String.valueOf(value)), min), max);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }
}
