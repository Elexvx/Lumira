package com.yourcompany.saas.modules.plugin.controller;

import com.yourcompany.saas.common.api.ApiResponse;
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import com.yourcompany.saas.infrastructure.security.CurrentUser;
import com.yourcompany.saas.infrastructure.security.SecurityContextFacade;
import com.yourcompany.saas.modules.iam.service.PermissionGuard;
import com.yourcompany.saas.modules.iam.service.PlatformMenuService;
import com.yourcompany.saas.modules.plugin.app.PluginManagementAppService;
import com.yourcompany.saas.modules.plugin.dto.PluginDTO;
import com.yourcompany.saas.modules.plugin.vo.PluginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/plugins")
public class PluginManagementController {

    private final PluginManagementAppService pluginManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final PlatformMenuService platformMenuService;

    public PluginManagementController(
            PluginManagementAppService pluginManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PlatformMenuService platformMenuService
    ) {
        this.pluginManagementAppService = pluginManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.platformMenuService = platformMenuService;
    }

    @GetMapping("/definitions")
    public ApiResponse<List<PluginVO.PluginDefinitionVO>> definitions() {
        require("plugin:management:view");
        return ApiResponse.success(pluginManagementAppService.listDefinitions(), TraceContext.getRequestId());
    }

    @GetMapping("/{pluginCode}/versions")
    public ApiResponse<List<PluginVO.PluginVersionVO>> versions(@PathVariable String pluginCode) {
        require("plugin:management:view");
        return ApiResponse.success(pluginManagementAppService.listVersions(pluginCode), TraceContext.getRequestId());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PluginVO.PluginUploadVO> upload(@RequestParam("file") MultipartFile file) {
        require("plugin:management:upload");
        return ApiResponse.success(pluginManagementAppService.upload(file, currentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/{pluginCode}/{version}/validation")
    public ApiResponse<String> validation(@PathVariable String pluginCode, @PathVariable String version) {
        require("plugin:management:view");
        return ApiResponse.success(pluginManagementAppService.validationDetail(pluginCode, version), TraceContext.getRequestId());
    }

    @PostMapping("/install")
    public ApiResponse<PluginVO.PluginVersionVO> install(@Valid @RequestBody PluginDTO.InstallRequest request) {
        require("plugin:management:install");
        return ApiResponse.success(
                pluginManagementAppService.install(request.getPluginCode(), request.getVersion(), currentUser()),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/upgrade")
    public ApiResponse<PluginVO.PluginVersionVO> upgrade(@Valid @RequestBody PluginDTO.InstallRequest request) {
        require("plugin:management:upgrade");
        return ApiResponse.success(
                pluginManagementAppService.upgrade(request.getPluginCode(), request.getVersion(), currentUser()),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/rollback")
    public ApiResponse<PluginVO.PluginVersionVO> rollback(@Valid @RequestBody PluginDTO.RollbackRequest request) {
        require("plugin:management:rollback");
        return ApiResponse.success(
                pluginManagementAppService.rollback(request.getPluginCode(), request.getTargetVersion(), currentUser()),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/enable")
    public ApiResponse<Boolean> enable(@Valid @RequestBody PluginDTO.EnableRequest request) {
        require("plugin:management:enable");
        pluginManagementAppService.enable(request, currentUser());
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @PostMapping("/disable")
    public ApiResponse<Boolean> disable(@Valid @RequestBody PluginDTO.DisableRequest request) {
        require("plugin:management:disable");
        pluginManagementAppService.disable(request, currentUser());
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @GetMapping("/{pluginCode}/logs")
    public ApiResponse<List<PluginVO.PluginRuntimeLogVO>> logs(@PathVariable String pluginCode) {
        require("plugin:management:logs");
        return ApiResponse.success(pluginManagementAppService.runtimeLogs(pluginCode), TraceContext.getRequestId());
    }

    @GetMapping("/current/available")
    public ApiResponse<List<PluginVO.TenantPluginVO>> currentAvailable() {
        CurrentUser currentUser = currentUser();
        return ApiResponse.success(
                pluginManagementAppService.availablePlugins(currentUser.getCurrentTenantId()),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/current/menus")
    public ApiResponse<List<Map<String, Object>>> currentMenus() {
        CurrentUser currentUser = currentUser();
        List<String> permissions = currentUser.getPermissions() == null ? List.of() : currentUser.getPermissions().stream().toList();
        return ApiResponse.success(
                platformMenuService.buildTenantMenuTree(currentUser.getCurrentTenantId(), permissions),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/current/permissions")
    public ApiResponse<List<String>> currentPermissions() {
        CurrentUser currentUser = currentUser();
        return ApiResponse.success(currentUser.getPermissions() == null ? List.of() : currentUser.getPermissions().stream().toList(), TraceContext.getRequestId());
    }

    @GetMapping("/current/{pluginCode}/manifest")
    public ResponseEntity<Resource> currentManifest(@PathVariable String pluginCode) {
        PluginVO.TenantPluginVO plugin = pluginManagementAppService.availablePlugins(currentUser().getCurrentTenantId()).stream()
                .filter(item -> pluginCode.equals(item.getPluginCode()))
                .findFirst()
                .orElseThrow();
        Path path = pluginManagementAppService.resolveManifestPath(pluginCode, plugin.getVersion());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FileSystemResource(path));
    }

    @GetMapping("/current/{pluginCode}/assets/**")
    public ResponseEntity<Resource> currentAsset(@PathVariable String pluginCode, HttpServletRequest request) {
        PluginVO.TenantPluginVO plugin = pluginManagementAppService.availablePlugins(currentUser().getCurrentTenantId()).stream()
                .filter(item -> pluginCode.equals(item.getPluginCode()))
                .findFirst()
                .orElseThrow();
        String prefix = "/api/v1/plugins/current/" + pluginCode + "/assets/";
        String assetPath = request.getRequestURI().substring(request.getRequestURI().indexOf(prefix) + prefix.length());
        Path path = pluginManagementAppService.resolvePluginAssetPath(pluginCode, plugin.getVersion(), assetPath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new FileSystemResource(path));
    }

    private CurrentUser currentUser() {
        return securityContextFacade.getCurrentUser();
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(currentUser(), permissionKey);
    }
}
