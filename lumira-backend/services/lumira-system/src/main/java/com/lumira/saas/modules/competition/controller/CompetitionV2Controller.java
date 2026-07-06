package com.lumira.saas.modules.competition.controller;

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
import com.lumira.saas.modules.competition.app.CompetitionManagementAppService;
import com.lumira.saas.modules.competition.dto.CompetitionDTO;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
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
@RequestMapping("/api/v2/aiadc/competitions")
public class CompetitionV2Controller {
    private static final String VIEW = "aiadc:competition:view";
    private static final String REGISTRATION_VIEW = "aiadc:registration:view";
    private static final String REGISTRATION_CREATE = "aiadc:registration:create";
    private static final String CREATE = "aiadc:competition:create";
    private static final String UPDATE = "aiadc:competition:update";
    private static final String DELETE = "aiadc:competition:delete";
    private static final String STATUS_ENABLED = "ENABLED";

    private final CompetitionManagementAppService competitionManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;

    public CompetitionV2Controller(
            CompetitionManagementAppService competitionManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(competitionManagementAppService, securityContextFacade, permissionGuard, null, null, null);
    }

    public CompetitionV2Controller(
            CompetitionManagementAppService competitionManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(competitionManagementAppService, securityContextFacade, permissionGuard, permissionSnapshotService, null, null);
    }

    public CompetitionV2Controller(
            CompetitionManagementAppService competitionManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                competitionManagementAppService,
                securityContextFacade,
                permissionGuard,
                permissionSnapshotService,
                null,
                sessionAuthenticationService
        );
    }

    @Autowired
    public CompetitionV2Controller(
            CompetitionManagementAppService competitionManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this.competitionManagementAppService = competitionManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<CompetitionVO.Competition>> competitions(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "locale", required = false) String locale,
            @RequestParam(name = "featured", required = false) Boolean featured,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = requireCompetitionListAccess(status);
        return ApiResponse.success(
                competitionManagementAppService.listCompetitions(currentUser, keyword, category, status, locale, featured, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<CompetitionVO.Competition> competition(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(VIEW);
        return ApiResponse.success(competitionManagementAppService.getCompetition(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/{competitionUuid}/settings")
    public ApiResponse<CompetitionVO.Settings> competitionSettings(@PathVariable("competitionUuid") String competitionUuid) {
        CurrentUser currentUser = requireTrustedUser(securityContextFacade.getCurrentUser());
        return ApiResponse.success(
                competitionManagementAppService.getCompetitionSettings(currentUser, competitionUuid),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/{competitionUuid}/settings/{module}")
    public ApiResponse<CompetitionVO.Settings> saveCompetitionSettingsModule(
            @PathVariable("competitionUuid") String competitionUuid,
            @PathVariable("module") String module,
            @Valid @RequestBody CompetitionDTO.SettingsModuleRequest request
    ) {
        CurrentUser currentUser = require(UPDATE);
        return ApiResponse.success(
                competitionManagementAppService.saveSettingsModule(currentUser, competitionUuid, module, request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/{competitionUuid}/settings/publish")
    @RepeatSubmit
    public ApiResponse<CompetitionVO.ConfigSet> publishCompetitionSettings(@PathVariable("competitionUuid") String competitionUuid) {
        CurrentUser currentUser = require(UPDATE);
        return ApiResponse.success(
                competitionManagementAppService.publishSettings(currentUser, competitionUuid),
                TraceContext.getRequestId()
        );
    }

    @PostMapping
    @RepeatSubmit
    public ApiResponse<CompetitionVO.Competition> createCompetition(@Valid @RequestBody CompetitionDTO.CompetitionUpsertRequest request) {
        CurrentUser currentUser = require(CREATE);
        return ApiResponse.success(competitionManagementAppService.createCompetition(currentUser, request), TraceContext.getRequestId());
    }

    @PostMapping("/drafts")
    public ApiResponse<CompetitionVO.Competition> createCompetitionDraft(@RequestBody CompetitionDTO.CompetitionUpsertRequest request) {
        CurrentUser currentUser = require(CREATE);
        return ApiResponse.success(competitionManagementAppService.createCompetitionDraft(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/drafts/{id}")
    public ApiResponse<CompetitionVO.Competition> updateCompetitionDraft(@PathVariable("id") Long id, @RequestBody CompetitionDTO.CompetitionUpsertRequest request) {
        CurrentUser currentUser = require(CREATE);
        return ApiResponse.success(competitionManagementAppService.updateCompetitionDraft(currentUser, id, request), TraceContext.getRequestId());
    }

    @PutMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<CompetitionVO.Competition> updateCompetition(@PathVariable("id") Long id, @RequestBody CompetitionDTO.CompetitionUpsertRequest request) {
        CurrentUser currentUser = require(UPDATE);
        return ApiResponse.success(competitionManagementAppService.updateCompetition(currentUser, id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteCompetition(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(DELETE);
        return ApiResponse.success(competitionManagementAppService.deleteCompetition(currentUser, id), TraceContext.getRequestId());
    }

    private CurrentUser require(String permissionKey) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        currentUser = requireTrustedUser(currentUser);
        permissionGuard.requirePermission(currentUser, permissionKey);
        return currentUser;
    }

    private CurrentUser requireCompetitionListAccess(String status) {
        CurrentUser currentUser = requireTrustedUser(securityContextFacade.getCurrentUser());
        if (permissionGuard.hasPermission(currentUser, VIEW)) {
            return currentUser;
        }
        boolean publishedOnly = "published".equalsIgnoreCase(status);
        if (publishedOnly && (
                permissionGuard.hasPermission(currentUser, REGISTRATION_VIEW)
                        || permissionGuard.hasPermission(currentUser, REGISTRATION_CREATE)
        )) {
            return currentUser;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "当前账号没有访问权限");
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
        target.setSimulatedRoleId(source.getSimulatedRoleId());
        target.setLoginType(source.getLoginType());
    }
}
