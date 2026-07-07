package com.lumira.saas.modules.activity.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.activity.app.ActivityManagementAppService;
import com.lumira.saas.modules.activity.dto.ActivityDTO;
import com.lumira.saas.modules.activity.vo.ActivityVO;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

@RestController
@RequestMapping("/api/v2/aiadc/activities")
public class ActivityV2Controller {
    private static final String VIEW = "aiadc:activity:view";
    private static final String CREATE = "aiadc:activity:create";
    private static final String UPDATE = "aiadc:activity:update";
    private static final String DELETE = "aiadc:activity:delete";
    private static final String STATUS_ENABLED = "ENABLED";

    private final ActivityManagementAppService activityManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    public ActivityV2Controller(
            ActivityManagementAppService activityManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(activityManagementAppService, securityContextFacade, permissionGuard, null, null, null, false);
    }

    public ActivityV2Controller(
            ActivityManagementAppService activityManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(activityManagementAppService, securityContextFacade, permissionGuard, permissionSnapshotService, null, null, false);
    }

    public ActivityV2Controller(
            ActivityManagementAppService activityManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(activityManagementAppService, securityContextFacade, permissionGuard, permissionSnapshotService, null, sessionAuthenticationService, false);
    }

    @Autowired
    public ActivityV2Controller(
            ActivityManagementAppService activityManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(activityManagementAppService, securityContextFacade, permissionGuard, permissionSnapshotService, systemInternalApi, sessionAuthenticationService, true);
    }

    private ActivityV2Controller(
            ActivityManagementAppService activityManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.activityManagementAppService = activityManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @GetMapping
    public ApiResponse<PageResponse<ActivityVO.Activity>> activities(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "locale", required = false) String locale,
            @RequestParam(name = "featured", required = false) Boolean featured,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require(VIEW);
        return ApiResponse.success(
                activityManagementAppService.listActivities(currentUser, keyword, status, locale, featured, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<ActivityVO.Activity> activity(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(VIEW);
        return ApiResponse.success(activityManagementAppService.getActivity(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping
    @RepeatSubmit
    public ApiResponse<ActivityVO.Activity> createActivity(@Valid @RequestBody ActivityDTO.ActivityUpsertRequest request) {
        CurrentUser currentUser = require(CREATE);
        return ApiResponse.success(activityManagementAppService.createActivity(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<ActivityVO.Activity> updateActivity(@PathVariable("id") Long id, @Valid @RequestBody ActivityDTO.ActivityUpsertRequest request) {
        CurrentUser currentUser = require(UPDATE);
        return ApiResponse.success(activityManagementAppService.updateActivity(currentUser, id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteActivity(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(DELETE);
        return ApiResponse.success(activityManagementAppService.deleteActivity(currentUser, id), TraceContext.getRequestId());
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
            if (!StringUtils.hasText(userSnapshot.username())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            userId = userSnapshot.userId();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(currentUserUuid);
            currentUser.setUsername(userSnapshot.username().trim());
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
        currentUser.setDataScopes(snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes()));
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
        target.setDataScopes(source.getDataScopes() == null ? List.of() : List.copyOf(source.getDataScopes()));
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
    }
}
