package com.lumira.saas.modules.audit.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
    private static final String STATUS_ENABLED = "ENABLED";

    private final SystemManagementAppService systemManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    public AuditController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(systemManagementAppService, securityContextFacade, permissionGuard, null, null, null, false);
    }

    public AuditController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(systemManagementAppService, securityContextFacade, permissionGuard, permissionSnapshotService, null, null, false);
    }

    public AuditController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(systemManagementAppService, securityContextFacade, permissionGuard, permissionSnapshotService, null, sessionAuthenticationService, false);
    }

    @Autowired
    public AuditController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(systemManagementAppService, securityContextFacade, permissionGuard, permissionSnapshotService, systemInternalApi, sessionAuthenticationService, true);
    }

    private AuditController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.systemManagementAppService = systemManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @GetMapping("/summary")
    public ApiResponse<java.util.Map<String, Integer>> summary() {
        CurrentUser currentUser = require("audit:view");
        PageResponse<SystemVO.AuditLogVO> login = systemManagementAppService.listLoginLogs(currentUser, null, 1, 1);
        PageResponse<SystemVO.AuditLogVO> operation = systemManagementAppService.listOperationLogs(currentUser, null, 1, 1);
        return ApiResponse.success(
                java.util.Map.of(
                        "loginCount", (int) login.getTotal(),
                        "operationCount", (int) operation.getTotal()
                ),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/login-logs")
    public ApiResponse<PageResponse<SystemVO.AuditLogVO>> loginLogs(
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "loginType", required = false) String loginType,
            @RequestParam(name = "startTime", required = false) String startTime,
            @RequestParam(name = "endTime", required = false) String endTime,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("audit:login:view");
        return ApiResponse.success(
                systemManagementAppService.listLoginLogs(currentUser, username, loginType, startTime, endTime, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/operation-logs")
    public ApiResponse<PageResponse<SystemVO.AuditLogVO>> operationLogs(
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "startTime", required = false) String startTime,
            @RequestParam(name = "endTime", required = false) String endTime,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("audit:operation:view");
        return ApiResponse.success(
                systemManagementAppService.listOperationLogs(currentUser, username, startTime, endTime, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/verification-logs")
    public ApiResponse<PageResponse<SystemVO.AuditLogVO>> verificationLogs(
            @RequestParam(name = "channel", required = false) String channel,
            @RequestParam(name = "scene", required = false) String scene,
            @RequestParam(name = "resultStatus", required = false) String resultStatus,
            @RequestParam(name = "startTime", required = false) String startTime,
            @RequestParam(name = "endTime", required = false) String endTime,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("audit:operation:view");
        return ApiResponse.success(
                systemManagementAppService.listVerificationLogs(currentUser, channel, scene, resultStatus, startTime, endTime, pageNo, pageSize),
                TraceContext.getRequestId()
        );
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
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
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
            String currentUsername = userSnapshot == null || !StringUtils.hasText(userSnapshot.username())
                    ? null
                    : userSnapshot.username().trim();
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
            if (!StringUtils.hasText(currentUsername)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            userId = userSnapshot.userId();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(currentUserUuid);
            currentUser.setUsername(currentUsername);
            normalizedUserUuid = currentUserUuid;
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        PermissionSnapshotService.PermissionSnapshot snapshot = simulatedRoleId != null
                ? permissionSnapshotService.loadGrantedRoleSnapshot(
                userId,
                normalizedUserUuid,
                simulatedRoleId
        )
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        if (snapshot == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
            }
            return;
        }
        currentUser.setSimulatedRoleId(simulatedRoleId);
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

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
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
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
    }
}
