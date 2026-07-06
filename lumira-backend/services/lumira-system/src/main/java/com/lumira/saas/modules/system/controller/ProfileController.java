package com.lumira.saas.modules.system.controller;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.modules.auth.vo.CurrentUserVO;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.dto.ProfileDTO;
import com.lumira.saas.modules.system.vo.SystemVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
    private static final String STATUS_ENABLED = "ENABLED";

    private final SystemManagementAppService systemManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final FileInternalApi fileInternalApi;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;

    public ProfileController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            FileInternalApi fileInternalApi
    ) {
        this(systemManagementAppService, securityContextFacade, fileInternalApi, null, null, null);
    }

    public ProfileController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            FileInternalApi fileInternalApi,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(systemManagementAppService, securityContextFacade, fileInternalApi, permissionSnapshotService, null, null);
    }

    public ProfileController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            FileInternalApi fileInternalApi,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(systemManagementAppService, securityContextFacade, fileInternalApi, permissionSnapshotService, null, sessionAuthenticationService);
    }

    @Autowired
    public ProfileController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            FileInternalApi fileInternalApi,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this.systemManagementAppService = systemManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.fileInternalApi = fileInternalApi;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    @GetMapping("/summary")
    public ApiResponse<SystemVO.ProfileSummaryVO> summary() {
        CurrentUser currentUser = requireTrustedCurrentUser();
        return ApiResponse.success(
                systemManagementAppService.profileSummary(currentUser),
                TraceContext.getRequestId()
        );
    }

    @PutMapping
    @RepeatSubmit
    public ApiResponse<CurrentUserVO> updateBasicInfo(@Valid @RequestBody ProfileDTO.BasicInfoUpdateRequest request) {
        CurrentUser currentUser = requireTrustedCurrentUser();
        return ApiResponse.success(
                systemManagementAppService.updateCurrentUserProfile(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/email")
    @RepeatSubmit
    public ApiResponse<CurrentUserVO> updateEmail(@Valid @RequestBody ProfileDTO.EmailUpdateRequest request) {
        CurrentUser currentUser = requireTrustedCurrentUser();
        return ApiResponse.success(
                systemManagementAppService.updateCurrentUserEmail(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/contact-bind/challenge")
    @RepeatSubmit
    public ApiResponse<SystemVO.VerificationChallengeVO> contactBindChallenge(@Valid @RequestBody ProfileDTO.ContactBindChallengeRequest request) {
        CurrentUser currentUser = requireTrustedCurrentUser();
        return ApiResponse.success(
                systemManagementAppService.startCurrentUserContactBindChallenge(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/contact-bind")
    @RepeatSubmit
    public ApiResponse<CurrentUserVO> contactBind(@Valid @RequestBody ProfileDTO.ContactBindRequest request) {
        CurrentUser currentUser = requireTrustedCurrentUser();
        return ApiResponse.success(
                systemManagementAppService.updateCurrentUserContactBinding(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/locale")
    @RepeatSubmit
    public ApiResponse<CurrentUserVO> updateLocale(@Valid @RequestBody ProfileDTO.LocaleUpdateRequest request) {
        CurrentUser currentUser = requireTrustedCurrentUser();
        return ApiResponse.success(
                systemManagementAppService.updateCurrentUserLocale(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/password")
    @RepeatSubmit
    public ApiResponse<Boolean> updatePassword(@Valid @RequestBody ProfileDTO.PasswordUpdateRequest request) {
        CurrentUser currentUser = requireTrustedCurrentUser();
        return ApiResponse.success(
                systemManagementAppService.updateCurrentUserPassword(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping(value = "/uploads/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RepeatSubmit
    public ApiResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        CurrentUser currentUser = requireTrustedCurrentUser();
        FileObjectDTO uploaded = fileInternalApi.uploadImageForUser(
                file,
                "\u5934\u50cf",
                "\u4e2a\u4eba\u5934\u50cf\u4e0a\u4f20",
                "avatar",
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                currentUser.getUsername()
        );
        return ApiResponse.success(uploaded.publicUrl(), TraceContext.getRequestId());
    }

    private CurrentUser requireTrustedCurrentUser() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        refreshTrustedCurrentUser(currentUser);
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
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
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
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
                throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
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
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
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
