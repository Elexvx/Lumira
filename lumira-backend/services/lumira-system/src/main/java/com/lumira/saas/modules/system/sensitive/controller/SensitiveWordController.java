package com.lumira.saas.modules.system.sensitive.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.common.vo.PageResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.sensitive.app.SensitiveWordService;
import com.lumira.saas.modules.system.sensitive.dto.SensitiveWordDTO;
import com.lumira.saas.modules.system.sensitive.vo.SensitiveWordVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.util.Set;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

@RestController
@RequestMapping("/api/v1/sensitive-words")
public class SensitiveWordController {
    private static final String STATUS_ENABLED = "ENABLED";

    private final SensitiveWordService sensitiveWordService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    public SensitiveWordController(
            SensitiveWordService sensitiveWordService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(sensitiveWordService, securityContextFacade, permissionGuard, null, null, null, false);
    }

    public SensitiveWordController(
            SensitiveWordService sensitiveWordService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(sensitiveWordService, securityContextFacade, permissionGuard, permissionSnapshotService, null, null, false);
    }

    public SensitiveWordController(
            SensitiveWordService sensitiveWordService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(sensitiveWordService, securityContextFacade, permissionGuard, permissionSnapshotService, null, sessionAuthenticationService, false);
    }

    @Autowired
    public SensitiveWordController(
            SensitiveWordService sensitiveWordService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(sensitiveWordService, securityContextFacade, permissionGuard, permissionSnapshotService, systemInternalApi, sessionAuthenticationService, true);
    }

    private SensitiveWordController(
            SensitiveWordService sensitiveWordService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.sensitiveWordService = sensitiveWordService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @GetMapping
    public ApiResponse<PageResponse<SensitiveWordVO.WordRecord>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "enabled", required = false) Boolean enabled,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("plugin:sensitive-words:view");
        return ApiResponse.success(
                sensitiveWordService.listWords(currentUser, keyword, enabled, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<SensitiveWordVO.WordRecord> detail(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("plugin:sensitive-words:view");
        return ApiResponse.success(sensitiveWordService.getWord(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping
    @RepeatSubmit
    public ApiResponse<SensitiveWordVO.WordRecord> create(@Valid @RequestBody SensitiveWordDTO.UpsertRequest request) {
        CurrentUser currentUser = require("plugin:sensitive-words:manage");
        return ApiResponse.success(sensitiveWordService.createWord(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<SensitiveWordVO.WordRecord> update(@PathVariable("id") Long id, @Valid @RequestBody SensitiveWordDTO.UpsertRequest request) {
        CurrentUser currentUser = require("plugin:sensitive-words:manage");
        return ApiResponse.success(sensitiveWordService.updateWord(currentUser, id, request), TraceContext.getRequestId());
    }

    @PatchMapping("/{id}/status")
    @RepeatSubmit
    public ApiResponse<Boolean> updateStatus(@PathVariable("id") Long id, @RequestBody SensitiveWordDTO.StatusRequest request) {
        CurrentUser currentUser = require("plugin:sensitive-words:manage");
        Boolean enabled = request == null ? null : request.getEnabled();
        return ApiResponse.success(sensitiveWordService.updateStatus(currentUser, id, enabled), TraceContext.getRequestId());
    }

    @DeleteMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> delete(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("plugin:sensitive-words:manage");
        return ApiResponse.success(sensitiveWordService.deleteWord(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RepeatSubmit
    public ApiResponse<SensitiveWordVO.ImportResult> importWords(@RequestParam("file") MultipartFile file) {
        CurrentUser currentUser = require("plugin:sensitive-words:import");
        return ApiResponse.success(sensitiveWordService.importWords(currentUser, file), TraceContext.getRequestId());
    }

    @PostMapping("/check")
    public ApiResponse<SensitiveWordVO.CheckResult> check(@RequestBody SensitiveWordDTO.CheckRequest request) {
        CurrentUser currentUser = require("plugin:sensitive-words:view");
        String text = request == null ? null : request.getText();
        String fieldPath = request == null ? null : request.getFieldPath();
        return ApiResponse.success(
                sensitiveWordService.checkText(currentUser, text, fieldPath),
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
            String currentUsername = StringUtils.hasText(userSnapshot.username()) ? userSnapshot.username().trim() : null;
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
        currentUser.setUserUuid(normalizedUserUuid);
        currentUser.setSimulatedRoleId(simulatedRoleId);
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
