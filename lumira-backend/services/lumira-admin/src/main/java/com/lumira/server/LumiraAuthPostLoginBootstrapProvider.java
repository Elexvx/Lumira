package com.lumira.server;

import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.auth.service.AuthPostLoginBootstrapProvider;
import com.lumira.auth.service.AuthReadModelVersionProvider;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.plugin.app.PluginManagementAppService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.stereotype.Service;

@Service
public class LumiraAuthPostLoginBootstrapProvider implements AuthPostLoginBootstrapProvider {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Executor BLOCKING_IO_EXECUTOR = command -> Thread.ofVirtual().start(command);

    private final PluginManagementAppService pluginManagementAppService;
    private final SystemManagementAppService systemManagementAppService;
    private final ObjectMapper objectMapper;

    public LumiraAuthPostLoginBootstrapProvider(
            PluginManagementAppService pluginManagementAppService,
            SystemManagementAppService systemManagementAppService,
            ObjectMapper objectMapper
    ) {
        this.pluginManagementAppService = pluginManagementAppService;
        this.systemManagementAppService = systemManagementAppService;
        this.objectMapper = objectMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public AuthPostLoginBootstrapPayload load(CurrentUserDTO currentUser) {
        return load(currentUser, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AuthPostLoginBootstrapPayload load(
            CurrentUserDTO currentUser,
            AuthReadModelVersionProvider.AuthBootstrapReadModelVersions readModelVersions
    ) {
        if (currentUser == null) {
            return new AuthPostLoginBootstrapPayload(List.of(), List.of(), Map.of());
        }
        CurrentUser authenticatedCurrentUser = toAuthenticatedCurrentUser(currentUser);
        CompletableFuture<Map<String, Object>> bootstrapFuture = CompletableFuture.supplyAsync(
                () -> pluginManagementAppService.currentBootstrap(
                        currentUser.permissions(),
                        currentUser.permissionsVersion(),
                        readModelVersions == null ? null : readModelVersions.pluginBootstrapVersion(),
                        readModelVersions == null ? null : readModelVersions.platformMenuTreeVersion()
                ),
                BLOCKING_IO_EXECUTOR
        );
        CompletableFuture<Map<String, Object>> runtimeAppearanceSettingsFuture = CompletableFuture.supplyAsync(
                () -> objectMapper.convertValue(
                        systemManagementAppService.getRuntimeAppearanceSettings(authenticatedCurrentUser),
                        MAP_TYPE
                ),
                BLOCKING_IO_EXECUTOR
        );
        Map<String, Object> bootstrap = bootstrapFuture.join();
        Object menuTree = bootstrap.get("menuTree");
        Object availablePlugins = bootstrap.get("availablePlugins");
        return new AuthPostLoginBootstrapPayload(
                menuTree instanceof List<?> list ? (List<Map<String, Object>>) list : List.of(),
                availablePlugins instanceof List<?> list ? (List<Object>) list : List.of(),
                runtimeAppearanceSettingsFuture.join()
        );
    }

    private CurrentUser toAuthenticatedCurrentUser(CurrentUserDTO currentUser) {
        Set<String> permissions = currentUser.permissions() == null
                ? Set.of()
                : new LinkedHashSet<>(currentUser.permissions());
        CurrentUser authenticatedCurrentUser = new CurrentUser(
                currentUser.userId(),
                currentUser.username(),
                currentUser.sessionId(),
                currentUser.sessionVersion(),
                true,
                permissions
        );
        authenticatedCurrentUser.setPermissionsVersion(currentUser.permissionsVersion());
        authenticatedCurrentUser.setRoleIds(currentUser.roleIds() == null ? Set.of() : new LinkedHashSet<>(currentUser.roleIds()));
        authenticatedCurrentUser.setPrimaryDeptId(currentUser.primaryDeptId());
        authenticatedCurrentUser.setDeptIds(currentUser.deptIds() == null ? Set.of() : new LinkedHashSet<>(currentUser.deptIds()));
        authenticatedCurrentUser.setDescendantDeptIds(
                currentUser.descendantDeptIds() == null ? Set.of() : new LinkedHashSet<>(currentUser.descendantDeptIds())
        );
        authenticatedCurrentUser.setDataScopes(currentUser.dataScopes() == null ? List.of() : List.copyOf(currentUser.dataScopes()));
        authenticatedCurrentUser.setRequiresPasswordChange(currentUser.requiresPasswordChange());
        authenticatedCurrentUser.setDefaultHomePath(currentUser.defaultHomePath());
        return authenticatedCurrentUser;
    }
}
