package com.lumira.saas.modules.plugin.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.saas.modules.plugin.app.PluginManagementAppService;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.runtime.PluginRuntimeSecurityPolicy;
import com.lumira.saas.modules.plugin.vo.PluginVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v2/plugins")
public class PluginV2Controller {
    private static final String STATUS_ENABLED = "ENABLED";

    private final PluginManagementAppService pluginManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final PluginRuntimeSecurityPolicy runtimeSecurityPolicy;
    private final SystemInternalApi systemInternalApi;

    public PluginV2Controller(
            PluginManagementAppService pluginManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PluginRuntimeSecurityPolicy runtimeSecurityPolicy
    ) {
        this(pluginManagementAppService, securityContextFacade, permissionGuard, runtimeSecurityPolicy, null);
    }

    @Autowired
    public PluginV2Controller(
            PluginManagementAppService pluginManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PluginRuntimeSecurityPolicy runtimeSecurityPolicy,
            SystemInternalApi systemInternalApi
    ) {
        this.pluginManagementAppService = pluginManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.runtimeSecurityPolicy = runtimeSecurityPolicy;
        this.systemInternalApi = systemInternalApi;
    }

    @GetMapping("/definitions")
    public ApiResponse<List<PluginVO.PluginDefinitionVO>> definitions() {
        require("plugin:management:view");
        return ApiResponse.success(pluginManagementAppService.listDefinitions(), TraceContext.getRequestId());
    }

    @GetMapping("/versions")
    public ApiResponse<Map<String, List<PluginVO.PluginVersionVO>>> versions() {
        require("plugin:management:view");
        return ApiResponse.success(pluginManagementAppService.listAllVersions(), TraceContext.getRequestId());
    }

    @GetMapping("/{pluginCode}/versions")
    public ApiResponse<List<PluginVO.PluginVersionVO>> versions(@PathVariable("pluginCode") String pluginCode) {
        require("plugin:management:view");
        return ApiResponse.success(pluginManagementAppService.listVersions(pluginCode), TraceContext.getRequestId());
    }

    @GetMapping("/{pluginCode}/status")
    public ApiResponse<PluginVO.PluginStatusVO> status(@PathVariable("pluginCode") String pluginCode) {
        require("plugin:management:view");
        return ApiResponse.success(pluginManagementAppService.status(pluginCode), TraceContext.getRequestId());
    }

    @PostMapping("/enable")
    @RepeatSubmit
    public ApiResponse<Boolean> enable(@Valid @RequestBody PluginDTO.EnableRequest request) {
        require("plugin:management:enable");
        pluginManagementAppService.enable(request, currentUser());
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @PostMapping("/disable")
    @RepeatSubmit
    public ApiResponse<Boolean> disable(@Valid @RequestBody PluginDTO.DisableRequest request) {
        require("plugin:management:disable");
        pluginManagementAppService.disable(request, currentUser());
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @GetMapping("/current/available")
    public ApiResponse<List<PluginVO.PluginAvailabilityVO>> currentAvailable() {
        return ApiResponse.success(pluginManagementAppService.currentAvailablePlugins(permissionList(currentUser())), TraceContext.getRequestId());
    }

    @GetMapping("/current/bootstrap")
    public ApiResponse<Map<String, Object>> currentBootstrap() {
        CurrentUser currentUser = currentUser();
        return ApiResponse.success(
                pluginManagementAppService.currentBootstrap(permissionList(currentUser), permissionsVersion(currentUser)),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/current/menus")
    public ApiResponse<List<Map<String, Object>>> currentMenus() {
        CurrentUser currentUser = currentUser();
        return ApiResponse.success(
                pluginManagementAppService.currentMenus(permissionList(currentUser), permissionsVersion(currentUser)),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/current/permissions")
    public ApiResponse<List<String>> currentPermissions() {
        return ApiResponse.success(permissionList(currentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/runtime/security-policy")
    public ApiResponse<PluginVO.RuntimeSecurityPolicyVO> runtimeSecurityPolicy() {
        require("plugin:management:view");
        var snapshot = runtimeSecurityPolicy.snapshot();
        PluginVO.RuntimeSecurityPolicyVO vo = new PluginVO.RuntimeSecurityPolicyVO();
        vo.setMaxGatewayBodyBytes(snapshot.maxGatewayBodyBytes());
        vo.setRequireHttpPermission(snapshot.requireHttpPermission());
        vo.setAllowedMethods(snapshot.allowedMethods().stream().sorted().toList());
        vo.setBlockedHeaders(snapshot.blockedHeaders().stream().sorted().toList());
        return ApiResponse.success(vo, TraceContext.getRequestId());
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        return refreshTrustedCurrentUser(currentUser);
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(currentUser(), permissionKey);
    }

    private List<String> permissionList(CurrentUser currentUser) {
        return !isTrustedCurrentUser(currentUser) || currentUser.getPermissions() == null
                ? List.of()
                : currentUser.getPermissions().stream().sorted().toList();
    }

    private String permissionsVersion(CurrentUser currentUser) {
        return isTrustedCurrentUser(currentUser) ? currentUser.getPermissionsVersion() : null;
    }

    private boolean isTrustedCurrentUser(CurrentUser currentUser) {
        return AuthenticationTrustSupport.isTrustedCurrentUser(currentUser);
    }

    private CurrentUser refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser) || systemInternalApi == null) {
            return currentUser;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = currentUser.getUserUuid() == null ? null : currentUser.getUserUuid().trim();
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
        if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!StringUtils.hasText(userSnapshot.userUuid())
                || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!StringUtils.hasText(userSnapshot.status())
                || !STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        PermissionSnapshotDTO permissionSnapshot = systemInternalApi.permissionSnapshot(
                userId,
                userSnapshot.userUuid().trim()
        );
        if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permissions are unavailable");
        }
        currentUser.setUserId(userSnapshot.userId());
        currentUser.setUserUuid(userSnapshot.userUuid().trim());
        currentUser.setUsername(userSnapshot.username());
        currentUser.setPermissions(permissionSnapshot.permissions() == null ? Set.of() : Set.copyOf(permissionSnapshot.permissions()));
        currentUser.setRoleIds(permissionSnapshot.roleIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.roleIds()));
        currentUser.setPrimaryDeptId(permissionSnapshot.primaryDeptId());
        currentUser.setDeptIds(permissionSnapshot.deptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.deptIds()));
        currentUser.setDescendantDeptIds(
                permissionSnapshot.descendantDeptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.descendantDeptIds())
        );
        currentUser.setDataScopes(permissionSnapshot.dataScopes() == null ? List.of() : List.copyOf(permissionSnapshot.dataScopes()));
        currentUser.setPermissionsVersion(permissionSnapshot.version().trim());
        currentUser.setDefaultHomePath(permissionSnapshot.defaultHomePath());
        return currentUser;
    }
}
