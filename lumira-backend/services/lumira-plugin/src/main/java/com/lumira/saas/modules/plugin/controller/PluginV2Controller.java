package com.lumira.saas.modules.plugin.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/plugins")
public class PluginV2Controller {

    private final PluginManagementAppService pluginManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final PluginRuntimeSecurityPolicy runtimeSecurityPolicy;

    public PluginV2Controller(
            PluginManagementAppService pluginManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PluginRuntimeSecurityPolicy runtimeSecurityPolicy
    ) {
        this.pluginManagementAppService = pluginManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.runtimeSecurityPolicy = runtimeSecurityPolicy;
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
        return ApiResponse.success(pluginManagementAppService.status(currentTenantId(), pluginCode), TraceContext.getRequestId());
    }

    @PostMapping("/enable")
    @RepeatSubmit
    public ApiResponse<Boolean> enable(@Valid @RequestBody PluginDTO.EnableRequest request) {
        require("plugin:management:enable");
        requireCurrentTenant(request.getTenantId());
        pluginManagementAppService.enable(request, currentUser());
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @PostMapping("/disable")
    @RepeatSubmit
    public ApiResponse<Boolean> disable(@Valid @RequestBody PluginDTO.DisableRequest request) {
        require("plugin:management:disable");
        requireCurrentTenant(request.getTenantId());
        pluginManagementAppService.disable(request, currentUser());
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @GetMapping("/current/available")
    public ApiResponse<List<PluginVO.TenantPluginVO>> currentAvailable() {
        return ApiResponse.success(pluginManagementAppService.availablePlugins(currentTenantId()), TraceContext.getRequestId());
    }

    @GetMapping("/current/bootstrap")
    public ApiResponse<Map<String, Object>> currentBootstrap() {
        CurrentUser currentUser = currentUser();
        return ApiResponse.success(
                pluginManagementAppService.currentBootstrap(currentTenantId(), permissionList(currentUser)),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/current/menus")
    public ApiResponse<List<Map<String, Object>>> currentMenus() {
        CurrentUser currentUser = currentUser();
        return ApiResponse.success(
                pluginManagementAppService.currentMenus(currentTenantId(), permissionList(currentUser)),
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
        return securityContextFacade.getCurrentUser();
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(currentUser(), permissionKey);
    }

    private void requireCurrentTenant(Long tenantId) {
        if (tenantId == null || !tenantId.equals(currentTenantId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能管理当前租户的插件");
        }
    }

    private Long currentTenantId() {
        CurrentUser currentUser = currentUser();
        Long tenantId = currentUser == null ? null : currentUser.getCurrentTenantId();
        return tenantId == null ? PlatformConstants.PLATFORM_TENANT_ID : tenantId;
    }

    private List<String> permissionList(CurrentUser currentUser) {
        return currentUser == null || currentUser.getPermissions() == null
                ? List.of()
                : currentUser.getPermissions().stream().sorted().toList();
    }
}
