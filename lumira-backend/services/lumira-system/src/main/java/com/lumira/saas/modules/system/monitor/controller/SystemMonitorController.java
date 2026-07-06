package com.lumira.saas.modules.system.monitor.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.TraceContext;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.monitor.app.SystemMonitorAppService;
import com.lumira.saas.modules.system.monitor.vo.SystemMonitorVO;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;
import java.util.Locale;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

@RestController
@RequestMapping("/api/v1/system/monitor")
public class SystemMonitorController {

    private static final String API_DOCS_PATH = "/api-docs";
    private static final String STATUS_ENABLED = "ENABLED";

    private final SystemMonitorAppService systemMonitorAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final ObjectProvider<OpenApiWebMvcResource> openApiWebMvcResourceProvider;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;

    public SystemMonitorController(
            SystemMonitorAppService systemMonitorAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            ObjectProvider<OpenApiWebMvcResource> openApiWebMvcResourceProvider
    ) {
        this(systemMonitorAppService, securityContextFacade, permissionGuard, openApiWebMvcResourceProvider, null, null, null);
    }

    public SystemMonitorController(
            SystemMonitorAppService systemMonitorAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            ObjectProvider<OpenApiWebMvcResource> openApiWebMvcResourceProvider,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(systemMonitorAppService, securityContextFacade, permissionGuard, openApiWebMvcResourceProvider, permissionSnapshotService, null, null);
    }

    public SystemMonitorController(
            SystemMonitorAppService systemMonitorAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            ObjectProvider<OpenApiWebMvcResource> openApiWebMvcResourceProvider,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(systemMonitorAppService, securityContextFacade, permissionGuard, openApiWebMvcResourceProvider, permissionSnapshotService, null, sessionAuthenticationService);
    }

    @Autowired
    public SystemMonitorController(
            SystemMonitorAppService systemMonitorAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            ObjectProvider<OpenApiWebMvcResource> openApiWebMvcResourceProvider,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this.systemMonitorAppService = systemMonitorAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.openApiWebMvcResourceProvider = openApiWebMvcResourceProvider;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    @GetMapping("/service")
    public ApiResponse<SystemMonitorVO.ServiceMonitorVO> serviceMonitor() {
        CurrentUser currentUser = require("system:monitor:service:view");
        return ApiResponse.success(systemMonitorAppService.getServiceMonitor(currentUser), TraceContext.getRequestId());
    }

    @GetMapping("/redis")
    public ApiResponse<SystemMonitorVO.RedisMonitorVO> redisMonitor() {
        CurrentUser currentUser = require("system:monitor:redis:view");
        return ApiResponse.success(systemMonitorAppService.getRedisMonitor(currentUser), TraceContext.getRequestId());
    }

    @GetMapping("/api-docs")
    public ResponseEntity<byte[]> apiDocs(HttpServletRequest request, Locale locale) throws IOException {
        require("system:monitor:docs:view");

        OpenApiWebMvcResource openApiResource = openApiWebMvcResourceProvider.getIfAvailable();
        if (openApiResource == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(openApiResource.openapiJson(request, API_DOCS_PATH, locale));
    }

    private CurrentUser require(String permissionKey) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        currentUser = requireTrustedUser(currentUser);
        permissionGuard.requirePermission(currentUser, permissionKey);
        return currentUser;
    }

    private CurrentUser requireTrustedUser(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser;
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!isTrustedCurrentUser(currentUser)) {
            return;
        }
        if (sessionAuthenticationService != null) {
            CurrentUser refreshedUser = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    )
            );
            copyTrustedCurrentUser(currentUser, refreshedUser);
            return;
        }
        if (permissionSnapshotService == null) {
            return;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            String currentUserUuid = userSnapshot == null || !StringUtils.hasText(userSnapshot.userUuid())
                    ? null
                    : userSnapshot.userUuid().trim();
            if (userSnapshot == null
                    || userSnapshot.userId() == null
                    || !userId.equals(userSnapshot.userId())
                    || !StringUtils.hasText(currentUserUuid)
                    || !normalizedUserUuid.equals(currentUserUuid)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            userId = userSnapshot.userId();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(currentUserUuid);
            currentUser.setUsername(userSnapshot.username());
            normalizedUserUuid = currentUserUuid;
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        PermissionSnapshotService.PermissionSnapshot snapshot = currentUser.getSimulatedRoleId() != null
                ? permissionSnapshotService.loadRoleSnapshot(currentUser.getSimulatedRoleId())
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        currentUser.setUserUuid(normalizedUserUuid);
        currentUser.setPermissions(snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()));
        currentUser.setRoleIds(snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()));
        currentUser.setPrimaryDeptId(snapshot.getPrimaryDeptId());
        currentUser.setDeptIds(snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()));
        currentUser.setDescendantDeptIds(snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()));
        currentUser.setDataScopes(snapshot.getDataScopes() == null ? java.util.List.of() : java.util.List.copyOf(snapshot.getDataScopes()));
        currentUser.setPermissionsVersion(snapshot.getVersion());
        currentUser.setDefaultHomePath(snapshot.getDefaultHomePath());
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        CurrentUser refreshedUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!isTrustedCurrentUser(refreshedUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return refreshedUser;
    }

    private void copyTrustedCurrentUser(CurrentUser target, CurrentUser source) {
        target.setUserId(source.getUserId());
        target.setUserUuid(source.getUserUuid());
        target.setUsername(source.getUsername());
        target.setSessionId(source.getSessionId());
        target.setSessionVersion(source.getSessionVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setPermissions(source.getPermissions() == null ? Set.of() : Set.copyOf(source.getPermissions()));
        target.setRoleIds(source.getRoleIds() == null ? Set.of() : Set.copyOf(source.getRoleIds()));
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds() == null ? Set.of() : Set.copyOf(source.getDeptIds()));
        target.setDescendantDeptIds(source.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(source.getDescendantDeptIds()));
        target.setDataScopes(source.getDataScopes() == null ? java.util.List.of() : java.util.List.copyOf(source.getDataScopes()));
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(source.getSimulatedRoleId());
        target.setLoginType(source.getLoginType());
    }
}
