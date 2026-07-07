package com.lumira.ai.integration;

import com.lumira.ai.config.AiOwnerIntegrationProperties;
import com.lumira.ai.vo.AiToolVO;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.MenuNodeDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AiConfigAccessPolicy;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.InternalServiceTokenPolicy;
import com.lumira.common.web.TrustedServiceBaseUrlValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RemoteAiOwnerToolGateway implements AiOwnerToolGateway {

    private static final String INTERNAL_TOKEN_HEADER = "X-Job-Token";

    private final AiOwnerIntegrationProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectProvider<SystemInternalApi> systemInternalApiProvider;
    private final boolean enforceTrustedUserResolution;

    public RemoteAiOwnerToolGateway(AiOwnerIntegrationProperties properties, RestClient.Builder restClientBuilder) {
        this(properties, restClientBuilder, null, false);
    }

    @Autowired
    public RemoteAiOwnerToolGateway(
            AiOwnerIntegrationProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider
    ) {
        this(properties, restClientBuilder, systemInternalApiProvider, true);
    }

    private RemoteAiOwnerToolGateway(
            AiOwnerIntegrationProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider,
            boolean enforceTrustedUserResolution
    ) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
        this.systemInternalApiProvider = systemInternalApiProvider;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @Override
    public ToolExecution execute(CurrentUser currentUser, AiToolVO tool, Map<String, Object> arguments) {
        CurrentUser trustedUser = requireTrustedUser(currentUser);
        if (tool == null || !StringUtils.hasText(tool.toolCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Tool code is required");
        }
        requireToolPermission(trustedUser, tool);
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        return switch (tool.toolCode()) {
            case "system.permission.snapshot" -> permissionSnapshot(trustedUser);
            case "system.config.read" -> platformConfig(trustedUser, safeArguments);
            case "system.menu.list" -> builtinMenus(trustedUser);
            case "file.object.search" -> searchFiles(trustedUser, safeArguments);
            case "system.user.search", "audit.ai_call.search" -> degraded(tool, "remote owner read contract is not configured", safeArguments);
            case "system.user.create", "system.user.update" -> new ToolExecution(Map.of(
                    "dryRun", true,
                    "message", "Standalone AI service requires IAM owner API for real writes.",
                    "arguments", safeArguments
            ), false, false);
            default -> degraded(tool, "undeclared AI owner tool", safeArguments);
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
        Long userId = trustedUserId(currentUser);
        String userUuid = trustedUserUuid(currentUser);
        if (!properties.getIam().configured()) {
            return localPermissionSnapshot(currentUser, "iam-owner-not-configured");
        }
        try {
            String path = "/internal/system/permissions/snapshot";
            PermissionSnapshotDTO snapshot = client(properties.getIam(), path)
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParam("userId", userId)
                            .queryParam("userUuid", userUuid)
                            .build())
                    .retrieve()
                    .body(PermissionSnapshotDTO.class);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userId", userId);
            data.put("userUuid", userUuid);
            data.put("version", snapshot == null ? null : snapshot.version());
            data.put("permissions", snapshot == null ? List.of() : snapshot.permissions());
            data.put("roleIds", snapshot == null ? List.of() : snapshot.roleIds());
            data.put("defaultHomePath", snapshot == null ? null : snapshot.defaultHomePath());
            return new ToolExecution(data, true, false);
        } catch (RuntimeException exception) {
            return degraded("system.permission.snapshot", "iam-owner-call-failed", Map.of(
                    "userId", userId,
                    "userUuid", userUuid
            ));
        }
    }

    private ToolExecution localPermissionSnapshot(CurrentUser currentUser, String reason) {
        return new ToolExecution(Map.of(
                "userId", trustedUserId(currentUser),
                "userUuid", trustedUserUuid(currentUser),
                "username", trustedUsername(currentUser),
                "permissions", currentUser.getPermissions() == null ? List.of() : currentUser.getPermissions(),
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
            String path = "/internal/system/config/ai-platform-values";
            Map<?, ?> values = client(properties.getPlatform(), path)
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParam("keys", keys.toArray())
                            .build())
                    .retrieve()
                    .body(Map.class);
            return new ToolExecution(Map.of("keys", keys, "values", values == null ? Map.of() : values), true, false);
        } catch (RuntimeException exception) {
            return degraded("platform", "platform-owner-call-failed", arguments);
        }
    }

    private ToolExecution builtinMenus(CurrentUser currentUser) {
        if (!properties.getPlatform().configured()) {
            return degraded("platform", "platform-owner-not-configured", Map.of());
        }
        try {
            String path = "/internal/system/menus/ai-visible";
            MenuNodeDTO[] menus = client(properties.getPlatform(), path)
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParam("userId", trustedUserId(currentUser))
                            .queryParam("userUuid", trustedUserUuid(currentUser))
                            .build())
                    .retrieve()
                    .body(MenuNodeDTO[].class);
            List<MenuNodeDTO> visibleMenus = filterVisibleMenus(menus == null ? List.of() : Arrays.asList(menus), trustedPermissions(currentUser));
            return new ToolExecution(Map.of("menus", visibleMenus), true, false);
        } catch (RuntimeException exception) {
            return degraded("platform", "platform-owner-call-failed", Map.of());
        }
    }

    private List<MenuNodeDTO> filterVisibleMenus(List<MenuNodeDTO> menus, Set<String> permissions) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        List<MenuNodeDTO> visibleMenus = new ArrayList<>();
        for (MenuNodeDTO menu : menus) {
            MenuNodeDTO visibleMenu = filterVisibleMenu(menu, permissions);
            if (visibleMenu != null) {
                visibleMenus.add(visibleMenu);
            }
        }
        return visibleMenus;
    }

    private MenuNodeDTO filterVisibleMenu(MenuNodeDTO menu, Set<String> permissions) {
        if (menu == null) {
            return null;
        }
        List<MenuNodeDTO> visibleChildren = filterVisibleMenus(menu.getChildren(), permissions);
        if (!isMenuAllowed(menu, permissions) && visibleChildren.isEmpty()) {
            return null;
        }
        MenuNodeDTO visibleMenu = new MenuNodeDTO();
        visibleMenu.setId(menu.getId());
        visibleMenu.setParentId(menu.getParentId());
        visibleMenu.setMenuCode(menu.getMenuCode());
        visibleMenu.setName(menu.getName());
        visibleMenu.setPath(menu.getPath());
        visibleMenu.setComponent(menu.getComponent());
        visibleMenu.setIcon(menu.getIcon());
        visibleMenu.setPermissionKey(menu.getPermissionKey());
        visibleMenu.setPluginCode(menu.getPluginCode());
        visibleMenu.setSortNo(menu.getSortNo());
        visibleMenu.setChildren(visibleChildren);
        return visibleMenu;
    }

    private boolean isMenuAllowed(MenuNodeDTO menu, Set<String> permissions) {
        if (menu == null || !StringUtils.hasText(menu.getPermissionKey())) {
            return true;
        }
        return permissions.contains("*") || permissions.contains(menu.getPermissionKey().trim());
    }

    private ToolExecution searchFiles(CurrentUser currentUser, Map<String, Object> arguments) {
        Long userId = trustedUserId(currentUser);
        String userUuid = trustedUserUuid(currentUser);
        String username = trustedUsername(currentUser);
        if (!properties.getFile().configured()) {
            return degraded("file", "file-owner-not-configured", arguments);
        }
        int limit = intArg(arguments, "limit", 20, 1, 50);
        try {
            String path = "/internal/files/search";
            FileObjectDTO[] files = client(properties.getFile(), path)
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParam("userId", userId)
                            .queryParam("userUuid", userUuid)
                            .queryParam("username", username)
                            .queryParam("keyword", stringArg(arguments, "keyword"))
                            .queryParam("contentType", stringArg(arguments, "contentType"))
                            .queryParam("status", stringArg(arguments, "status"))
                            .queryParam("sharedScope", false)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .body(FileObjectDTO[].class);
            return new ToolExecution(Map.of("files", files == null ? List.of() : Arrays.asList(files)), true, false);
        } catch (RuntimeException exception) {
            return degraded("file", "file-owner-call-failed", arguments);
        }
    }

    private RestClient client(AiOwnerIntegrationProperties.OwnerEndpoint endpoint, String path) {
        RestClient.Builder builder = restClientBuilder.clone()
                .baseUrl(TrustedServiceBaseUrlValidator.requireHttpBaseUrl(
                        endpoint.getBaseUrl(),
                        "lumira.ai.owner-integrations endpoint baseUrl"
                ));
        String token = scopedTokenForOwnerPath(path);
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("Scoped internal token is not configured for " + path);
        }
        builder.defaultHeader(INTERNAL_TOKEN_HEADER, token);
        builder.defaultHeader(HttpHeaders.ACCEPT, "application/json");
        return builder.build();
    }

    String scopedTokenForOwnerPath(String path) {
        return InternalServiceTokenPolicy.tokenForPath(
                path,
                properties.getSystemToken(),
                null,
                properties.getAuthSystemToken(),
                properties.getFileToken(),
                null,
                null,
                null,
                null
        );
    }

    private CurrentUser requireTrustedUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user context is required");
        }
        if (systemInternalApiProvider == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return currentUser;
        }
        Long userId = currentUser.getUserId();
        String userUuid = currentUser.getUserUuid() == null ? null : currentUser.getUserUuid().trim();
        if (userId == null || userId <= 0 || !StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user context is required");
        }
        SystemInternalApi systemInternalApi = systemInternalApiProvider.getIfAvailable();
        if (systemInternalApi == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
        }
        SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
        if (userSnapshot == null || userSnapshot.userId() == null || !userSnapshot.userId().equals(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user does not exist");
        }
        if (!StringUtils.hasText(userSnapshot.userUuid()) || !userSnapshot.userUuid().trim().equals(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity mismatch");
        }
        if (!StringUtils.hasText(userSnapshot.status()) || !"ENABLED".equalsIgnoreCase(userSnapshot.status().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled");
        }
        if (!StringUtils.hasText(userSnapshot.username())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
        }
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        PermissionSnapshotDTO permissionSnapshot = simulatedRoleId == null
                ? systemInternalApi.permissionSnapshot(userId, userUuid)
                : systemInternalApi.simulatedRolePermissionSnapshot(userId, userUuid, simulatedRoleId);
        if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permissions are unavailable");
        }
        currentUser.setUserId(userSnapshot.userId());
        currentUser.setUserUuid(userUuid);
        currentUser.setUsername(userSnapshot.username().trim());
        currentUser.setPermissions(permissionSnapshot.permissions() == null
                ? Set.of()
                : new HashSet<>(permissionSnapshot.permissions()));
        currentUser.setPermissionsVersion(permissionSnapshot.version().trim());
        currentUser.setSimulatedRoleId(simulatedRoleId);
        return currentUser;
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private Long trustedUserId(CurrentUser currentUser) {
        return requireTrustedUser(currentUser).getUserId();
    }

    private String trustedUserUuid(CurrentUser currentUser) {
        return requireTrustedUser(currentUser).getUserUuid();
    }

    private String trustedUsername(CurrentUser currentUser) {
        return requireTrustedUser(currentUser).getUsername();
    }

    private Set<String> trustedPermissions(CurrentUser currentUser) {
        CurrentUser trustedUser = requireTrustedUser(currentUser);
        return trustedUser.getPermissions() == null ? Set.of() : new HashSet<>(trustedUser.getPermissions());
    }

    private void requireToolPermission(CurrentUser currentUser, AiToolVO tool) {
        if (tool == null || !StringUtils.hasText(tool.requiredPermission())) {
            return;
        }
        Set<String> permissions = trustedPermissions(currentUser);
        String requiredPermission = tool.requiredPermission().trim();
        if (!permissions.contains("*") && !permissions.contains(requiredPermission)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + requiredPermission);
        }
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
            if (list.size() > 50) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Too many config keys");
            }
            List<String> normalizedKeys = new ArrayList<>();
            for (Object value : list) {
                if (value == null || !StringUtils.hasText(String.valueOf(value))) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "Config key cannot be blank");
                }
                String normalized = String.valueOf(value).trim();
                if (isAiConfigKeyAllowed(normalized) && !normalizedKeys.contains(normalized)) {
                    normalizedKeys.add(normalized);
                }
            }
            return normalizedKeys;
        }
        String key = stringArg(arguments, "key");
        if (!StringUtils.hasText(key)) {
            return List.of();
        }
        String normalizedKey = key.trim();
        return isAiConfigKeyAllowed(normalizedKey) ? List.of(normalizedKey) : List.of();
    }

    private boolean isAiConfigKeyAllowed(String value) {
        return AiConfigAccessPolicy.isAiManageableConfigKey(value);
    }

    private String stringArg(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private int intArg(Map<String, Object> arguments, String key, int defaultValue, int min, int max) {
        Object value = arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            if (parsed < min || parsed > max) {
                throw new BizException(ErrorCode.BAD_REQUEST, key + " must be between " + min + " and " + max);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new BizException(ErrorCode.BAD_REQUEST, key + " must be a number");
        }
    }
}
