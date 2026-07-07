package com.lumira.saas.modules.system.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.TraceContext;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.auth.dto.SecondFactorBindRequest;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.auth.dto.SecondFactorVerifyRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

@RestController
@RequestMapping("/api/v1/system/verification")
public class SystemVerificationController {
    private static final String STATUS_ENABLED = "ENABLED";

    private final SystemVerificationAppService verificationAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    public SystemVerificationController(
            SystemVerificationAppService verificationAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(verificationAppService, securityContextFacade, permissionGuard, null, null, null, false);
    }

    public SystemVerificationController(
            SystemVerificationAppService verificationAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(verificationAppService, securityContextFacade, permissionGuard, permissionSnapshotService, null, null, false);
    }

    public SystemVerificationController(
            SystemVerificationAppService verificationAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(verificationAppService, securityContextFacade, permissionGuard, permissionSnapshotService, null, sessionAuthenticationService, false);
    }

    @Autowired
    public SystemVerificationController(
            SystemVerificationAppService verificationAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(verificationAppService, securityContextFacade, permissionGuard, permissionSnapshotService, systemInternalApi, sessionAuthenticationService, true);
    }

    private SystemVerificationController(
            SystemVerificationAppService verificationAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.verificationAppService = verificationAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @GetMapping("/providers")
    public ApiResponse<List<SystemVO.VerificationProviderVO>> providers() {
        CurrentUser currentUser = requireView();
        return ApiResponse.success(
                verificationAppService.listProviders(currentUser),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/providers/{factorCode}")
    public ApiResponse<SystemVO.VerificationProviderVO> provider(@PathVariable("factorCode") String factorCode) {
        CurrentUser currentUser = requireView();
        return ApiResponse.success(
                verificationAppService.provider(currentUser, factorCode),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/sms-settings")
    public ApiResponse<SystemVO.SmsVerificationSettingsVO> smsSettings() {
        CurrentUser currentUser = requireView();
        return ApiResponse.success(
                verificationAppService.getSmsSettings(currentUser),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/wechat-settings")
    public ApiResponse<SystemVO.WechatLoginSettingsVO> wechatSettings() {
        CurrentUser currentUser = requireView();
        return ApiResponse.success(
                verificationAppService.getWechatSettings(currentUser),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/passkey-settings")
    public ApiResponse<SystemVO.PasskeySettingsVO> passkeySettings() {
        CurrentUser currentUser = requireView();
        return ApiResponse.success(
                verificationAppService.getPasskeySettings(currentUser),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/settings")
    public ApiResponse<SystemVO.VerificationSettingsVO> verificationSettings() {
        CurrentUser currentUser = requireView();
        return ApiResponse.success(
                verificationAppService.getVerificationSettings(currentUser),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/providers/{factorCode}/bind")
    @RepeatSubmit
    public ApiResponse<SystemVO.VerificationChallengeVO> bind(
            @PathVariable("factorCode") String factorCode,
            @RequestBody(required = false) @Valid SecondFactorBindRequest request
    ) {
        CurrentUser currentUser = require("system:verification:manage");
        return ApiResponse.success(
                verificationAppService.bindCurrentUser(
                        currentUser,
                        factorCode,
                        request == null ? null : request.getCurrentPassword(),
                        request == null ? null : request.getCurrentFactorCode(),
                        request == null ? null : request.getCurrentChallengeId(),
                        request == null ? null : request.getCurrentVerificationCode()
                ),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/providers/{factorCode}/unbind")
    @RepeatSubmit
    public ApiResponse<Boolean> unbind(
            @PathVariable("factorCode") String factorCode,
            @Valid @RequestBody SecondFactorVerifyRequest request
    ) {
        CurrentUser currentUser = require("system:verification:manage");
        if (!factorCode.equalsIgnoreCase(request.getFactorCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证方式不匹配");
        }
        return ApiResponse.success(
                verificationAppService.unbindCurrentUser(
                        currentUser,
                        factorCode,
                        request.getChallengeId(),
                        request.getVerificationCode()
                ),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/providers/{factorCode}/challenge")
    @RepeatSubmit
    public ApiResponse<SystemVO.VerificationChallengeVO> challenge(@PathVariable("factorCode") String factorCode) {
        CurrentUser currentUser = require("system:verification:manage");
        return ApiResponse.success(
                verificationAppService.challengeCurrentUser(currentUser, factorCode),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/providers/{factorCode}/verify")
    @RepeatSubmit
    public ApiResponse<SystemVO.VerificationVerificationVO> verify(
            @PathVariable("factorCode") String factorCode,
            @Valid @RequestBody SecondFactorVerifyRequest request
    ) {
        CurrentUser currentUser = require("system:verification:manage");
        if (!factorCode.equalsIgnoreCase(request.getFactorCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Verification factor does not match");
        }
        return ApiResponse.success(
                verificationAppService.completeBindCurrentUser(
                        currentUser,
                        factorCode,
                        request.getChallengeId(),
                        request.getVerificationCode()
                ),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/sms-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.SmsVerificationSettingsVO> updateSmsSettings(@Valid @RequestBody SystemDTO.SmsVerificationSettingsRequest request) {
        CurrentUser currentUser = requireConfigManage();
        return ApiResponse.success(
                verificationAppService.updateSmsSettings(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @DeleteMapping("/sms-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.SmsVerificationSettingsVO> resetSmsSettings() {
        CurrentUser currentUser = requireConfigManage();
        return ApiResponse.success(
                verificationAppService.resetSmsSettings(currentUser),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/wechat-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.WechatLoginSettingsVO> updateWechatSettings(@Valid @RequestBody SystemDTO.WechatLoginSettingsRequest request) {
        CurrentUser currentUser = requireConfigManage();
        return ApiResponse.success(
                verificationAppService.updateWechatSettings(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @DeleteMapping("/wechat-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.WechatLoginSettingsVO> resetWechatSettings() {
        CurrentUser currentUser = requireConfigManage();
        return ApiResponse.success(
                verificationAppService.resetWechatSettings(currentUser),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/passkey-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.PasskeySettingsVO> updatePasskeySettings(@Valid @RequestBody SystemDTO.PasskeySettingsRequest request) {
        CurrentUser currentUser = requireConfigManage();
        return ApiResponse.success(
                verificationAppService.updatePasskeySettings(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @DeleteMapping("/passkey-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.PasskeySettingsVO> resetPasskeySettings() {
        CurrentUser currentUser = requireConfigManage();
        return ApiResponse.success(
                verificationAppService.resetPasskeySettings(currentUser),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.VerificationSettingsVO> updateVerificationSettings(@Valid @RequestBody SystemDTO.VerificationSettingsRequest request) {
        CurrentUser currentUser = requireConfigManage();
        return ApiResponse.success(
                verificationAppService.updateVerificationSettings(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    private CurrentUser require(String permissionKey) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        currentUser = requireTrustedUser(currentUser);
        permissionGuard.requirePermission(currentUser, permissionKey);
        return currentUser;
    }

    private CurrentUser requireView() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        currentUser = requireTrustedUser(currentUser);
        if (hasAnyPermission(currentUser, "system:verification:view", "system:verification:manage", "system:config:view", "system:config:update")) {
            return currentUser;
        }
        permissionGuard.requirePermission(currentUser, "system:verification:view");
        return currentUser;
    }

    private CurrentUser requireConfigManage() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        currentUser = requireTrustedUser(currentUser);
        if (hasAnyPermission(currentUser, "system:verification:manage", "system:config:update")) {
            return currentUser;
        }
        permissionGuard.requirePermission(currentUser, "system:verification:manage");
        return currentUser;
    }

    private boolean hasAnyPermission(CurrentUser currentUser, String... permissionKeys) {
        refreshTrustedCurrentUser(currentUser);
        if (!isTrustedCurrentUser(currentUser) || currentUser.getPermissions() == null) {
            return false;
        }
        if (currentUser.getPermissions().contains("*")) {
            return true;
        }
        for (String permissionKey : permissionKeys) {
            if (currentUser.getPermissions().contains(permissionKey)) {
                return true;
            }
        }
        return false;
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
