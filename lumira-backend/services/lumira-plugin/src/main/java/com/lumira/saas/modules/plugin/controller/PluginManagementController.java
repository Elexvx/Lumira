package com.lumira.saas.modules.plugin.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.modules.plugin.app.PluginManagementAppService;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.runtime.PluginRuntimeSecurityPolicy;
import com.lumira.saas.modules.plugin.vo.PluginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/plugins")
public class PluginManagementController {
    private static final String STATUS_ENABLED = "ENABLED";

    private final PluginManagementAppService pluginManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final PluginRuntimeSecurityPolicy runtimeSecurityPolicy;
    private final SystemInternalApi systemInternalApi;
    private final boolean enforceTrustedUserResolution;

    public PluginManagementController(
            PluginManagementAppService pluginManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PluginRuntimeSecurityPolicy runtimeSecurityPolicy
    ) {
        this(pluginManagementAppService, securityContextFacade, permissionGuard, runtimeSecurityPolicy, null, false);
    }

    @Autowired
    public PluginManagementController(
            PluginManagementAppService pluginManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PluginRuntimeSecurityPolicy runtimeSecurityPolicy,
            SystemInternalApi systemInternalApi
    ) {
        this(pluginManagementAppService, securityContextFacade, permissionGuard, runtimeSecurityPolicy, systemInternalApi, true);
    }

    private PluginManagementController(
            PluginManagementAppService pluginManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PluginRuntimeSecurityPolicy runtimeSecurityPolicy,
            SystemInternalApi systemInternalApi,
            boolean enforceTrustedUserResolution
    ) {
        this.pluginManagementAppService = pluginManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.runtimeSecurityPolicy = runtimeSecurityPolicy;
        this.systemInternalApi = systemInternalApi;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @GetMapping("/definitions")
    public ApiResponse<List<PluginVO.PluginDefinitionVO>> definitions() {
        require("plugin:management:view");
        return ApiResponse.success(pluginManagementAppService.listDefinitions(), TraceContext.getRequestId());
    }

    @GetMapping("/{pluginCode}/versions")
    public ApiResponse<List<PluginVO.PluginVersionVO>> versions(@PathVariable("pluginCode") String pluginCode) {
        require("plugin:management:view");
        return ApiResponse.success(pluginManagementAppService.listVersions(pluginCode), TraceContext.getRequestId());
    }

    @GetMapping("/versions")
    public ApiResponse<Map<String, List<PluginVO.PluginVersionVO>>> versions() {
        require("plugin:management:view");
        return ApiResponse.success(pluginManagementAppService.listAllVersions(), TraceContext.getRequestId());
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

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RepeatSubmit
    public ApiResponse<PluginVO.PluginUploadVO> upload(@RequestParam("file") MultipartFile file) {
        require("plugin:management:upload");
        return ApiResponse.success(pluginManagementAppService.upload(file, currentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/{pluginCode}/{version}/validation")
    public ApiResponse<String> validation(@PathVariable("pluginCode") String pluginCode, @PathVariable("version") String version) {
        require("plugin:management:view");
        return ApiResponse.success(pluginManagementAppService.validationDetail(pluginCode, version), TraceContext.getRequestId());
    }

    @PostMapping("/install")
    @RepeatSubmit
    public ApiResponse<PluginVO.PluginVersionVO> install(@Valid @RequestBody PluginDTO.InstallRequest request) {
        require("plugin:management:install");
        return ApiResponse.success(
                pluginManagementAppService.install(request.getPluginCode(), request.getVersion(), currentUser()),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/upgrade")
    @RepeatSubmit
    public ApiResponse<PluginVO.PluginVersionVO> upgrade(@Valid @RequestBody PluginDTO.InstallRequest request) {
        require("plugin:management:upgrade");
        return ApiResponse.success(
                pluginManagementAppService.upgrade(request.getPluginCode(), request.getVersion(), currentUser()),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/rollback")
    @RepeatSubmit
    public ApiResponse<PluginVO.PluginVersionVO> rollback(@Valid @RequestBody PluginDTO.RollbackRequest request) {
        require("plugin:management:rollback");
        return ApiResponse.success(
                pluginManagementAppService.rollback(request.getPluginCode(), request.getTargetVersion(), currentUser()),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/enable")
    @RepeatSubmit
    public ApiResponse<Boolean> enable(@Valid @RequestBody PluginDTO.EnableRequest request) {
        require("plugin:management:enable");
        pluginManagementAppService.enable(request, currentUser());
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @PostMapping("/{pluginCode}/enable")
    @RepeatSubmit
    public ApiResponse<Boolean> enable(@PathVariable("pluginCode") String pluginCode, @RequestBody(required = false) PluginDTO.EnableRequest request) {
        require("plugin:management:enable");
        PluginDTO.EnableRequest enableRequest = request == null ? new PluginDTO.EnableRequest() : request;
        enableRequest.setPluginCode(pluginCode);
        pluginManagementAppService.enable(enableRequest, currentUser());
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @PostMapping("/disable")
    @RepeatSubmit
    public ApiResponse<Boolean> disable(@Valid @RequestBody PluginDTO.DisableRequest request) {
        require("plugin:management:disable");
        pluginManagementAppService.disable(request, currentUser());
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @PostMapping("/{pluginCode}/disable")
    @RepeatSubmit
    public ApiResponse<Boolean> disable(@PathVariable("pluginCode") String pluginCode, @RequestBody(required = false) PluginDTO.DisableRequest request) {
        require("plugin:management:disable");
        PluginDTO.DisableRequest disableRequest = request == null ? new PluginDTO.DisableRequest() : request;
        disableRequest.setPluginCode(pluginCode);
        pluginManagementAppService.disable(disableRequest, currentUser());
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @GetMapping("/{pluginCode}/status")
    public ApiResponse<PluginVO.PluginStatusVO> status(@PathVariable("pluginCode") String pluginCode) {
        require("plugin:management:view");
        return ApiResponse.success(pluginManagementAppService.status(pluginCode), TraceContext.getRequestId());
    }

    @PostMapping("/{pluginCode}/uninstall")
    @RepeatSubmit
    public ApiResponse<Boolean> uninstall(
            @PathVariable("pluginCode") String pluginCode,
            @RequestBody(required = false) PluginDTO.UninstallRequest request
    ) {
        require("plugin:management:disable");
        pluginManagementAppService.uninstall(pluginCode, request != null && request.isRemoveData(), currentUser());
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @DeleteMapping("/{pluginCode}")
    @RepeatSubmit
    public ApiResponse<Boolean> uninstallDelete(
            @PathVariable("pluginCode") String pluginCode,
            @RequestBody(required = false) PluginDTO.UninstallRequest request
    ) {
        require("plugin:management:disable");
        pluginManagementAppService.uninstall(pluginCode, request != null && request.isRemoveData(), currentUser());
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @GetMapping("/{pluginCode}/logs")
    public ApiResponse<List<PluginVO.PluginRuntimeLogVO>> logs(@PathVariable("pluginCode") String pluginCode) {
        require("plugin:management:logs");
        return ApiResponse.success(pluginManagementAppService.runtimeLogs(pluginCode), TraceContext.getRequestId());
    }

    @GetMapping("/current/available")
    public ApiResponse<List<PluginVO.PluginAvailabilityVO>> currentAvailable() {
        return ApiResponse.success(
                pluginManagementAppService.currentAvailablePlugins(permissionList(currentUser())),
                TraceContext.getRequestId()
        );
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

    @GetMapping("/current/{pluginCode}/manifest")
    public ResponseEntity<Resource> currentManifest(@PathVariable("pluginCode") String pluginCode) {
        PluginVO.PluginAvailabilityVO plugin = currentAvailablePlugin(pluginCode, currentUser());
        Path path = pluginManagementAppService.resolveManifestPath(pluginCode, plugin.getVersion());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FileSystemResource(path));
    }

    @GetMapping("/current/{pluginCode}/assets/**")
    public ResponseEntity<Resource> currentAsset(@PathVariable("pluginCode") String pluginCode, HttpServletRequest request) {
        PluginVO.PluginAvailabilityVO plugin = currentAvailablePlugin(pluginCode, currentUser());
        String prefix = "/api/v1/plugins/current/" + pluginCode + "/assets/";
        String assetPath = request.getRequestURI().substring(request.getRequestURI().indexOf(prefix) + prefix.length());
        Path path = pluginManagementAppService.resolvePluginAssetPath(pluginCode, plugin.getVersion(), assetPath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new FileSystemResource(path));
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

    private PluginVO.PluginAvailabilityVO currentAvailablePlugin(String pluginCode, CurrentUser currentUser) {
        return pluginManagementAppService.currentAvailablePlugins(permissionList(currentUser)).stream()
                .filter(item -> pluginCode.equals(item.getPluginCode()))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.FORBIDDEN, "Plugin is not available for current user"));
    }

    private CurrentUser refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return currentUser;
        }
        if (systemInternalApi == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
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
        String currentUsername = StringUtils.hasText(userSnapshot.username()) ? userSnapshot.username().trim() : null;
        if (!StringUtils.hasText(currentUsername)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
        }
        Long simulatedRoleId = currentUser.getSimulatedRoleId();
        if (simulatedRoleId != null && simulatedRoleId <= 0) {
            simulatedRoleId = null;
        }
        PermissionSnapshotDTO permissionSnapshot = simulatedRoleId == null
                ? systemInternalApi.permissionSnapshot(userId, userSnapshot.userUuid().trim())
                : systemInternalApi.simulatedRolePermissionSnapshot(userId, userSnapshot.userUuid().trim(), simulatedRoleId);
        if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permissions are unavailable");
        }
        currentUser.setUserId(userSnapshot.userId());
        currentUser.setUserUuid(userSnapshot.userUuid().trim());
        currentUser.setUsername(currentUsername);
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
