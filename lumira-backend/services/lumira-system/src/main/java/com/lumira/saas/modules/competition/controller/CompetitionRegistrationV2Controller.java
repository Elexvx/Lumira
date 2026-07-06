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
import com.lumira.saas.modules.competition.app.CompetitionRegistrationAppService;
import com.lumira.saas.modules.competition.dto.CompetitionRegistrationDTO;
import com.lumira.saas.modules.competition.vo.CompetitionRegistrationVO;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

@RestController
@RequestMapping("/api/v2/aiadc")
public class CompetitionRegistrationV2Controller {
    private static final String REGISTRATION_VIEW = "aiadc:registration:view";
    private static final String REGISTRATION_CREATE = "aiadc:registration:create";
    private static final String REGISTRATION_UPDATE = "aiadc:registration:update";
    private static final String REGISTRATION_PAY = "aiadc:registration:pay";
    private static final String MATERIAL_VIEW = "aiadc:material:view";
    private static final String MATERIAL_SUBMIT = "aiadc:material:submit";
    private static final String STAGE_VIEW = "aiadc:stage:view";
    private static final String STAGE_MANAGE = "aiadc:stage:manage";
    private static final String PAYMENT_ORDER_VIEW = "payment:order:view";
    private static final String STATUS_ENABLED = "ENABLED";

    private final CompetitionRegistrationAppService registrationAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;

    public CompetitionRegistrationV2Controller(
            CompetitionRegistrationAppService registrationAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(registrationAppService, securityContextFacade, permissionGuard, null, null, null);
    }

    public CompetitionRegistrationV2Controller(
            CompetitionRegistrationAppService registrationAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(registrationAppService, securityContextFacade, permissionGuard, permissionSnapshotService, null, null);
    }

    public CompetitionRegistrationV2Controller(
            CompetitionRegistrationAppService registrationAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(registrationAppService, securityContextFacade, permissionGuard, permissionSnapshotService, null, sessionAuthenticationService);
    }

    @Autowired
    public CompetitionRegistrationV2Controller(
            CompetitionRegistrationAppService registrationAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this.registrationAppService = registrationAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    @GetMapping("/registrations")
    public ApiResponse<PageResponse<CompetitionRegistrationVO.Registration>> registrations(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = requireRegistrationReadAccess();
        return ApiResponse.success(
                registrationAppService.listRegistrations(currentUser, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/payments")
    public ApiResponse<PageResponse<CompetitionRegistrationVO.PaymentRecord>> payments(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "paymentStatus", required = false) String paymentStatus,
            @RequestParam(name = "registrationStatus", required = false) String registrationStatus,
            @RequestParam(name = "providerCode", required = false) String providerCode
    ) {
        CurrentUser currentUser = require(PAYMENT_ORDER_VIEW);
        return ApiResponse.success(
                registrationAppService.listPaymentRecords(
                        currentUser,
                        pageNo,
                        pageSize,
                        keyword,
                        paymentStatus,
                        registrationStatus,
                        providerCode
                ),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/registrations/{id}")
    public ApiResponse<CompetitionRegistrationVO.Registration> registration(@PathVariable("id") Long id) {
        CurrentUser currentUser = requireRegistrationReadAccess();
        return ApiResponse.success(registrationAppService.getRegistration(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/registrations")
    @RepeatSubmit
    public ApiResponse<CompetitionRegistrationVO.Registration> createRegistration(@Valid @RequestBody CompetitionRegistrationDTO.RegistrationCreateRequest request) {
        CurrentUser currentUser = require(REGISTRATION_CREATE);
        return ApiResponse.success(registrationAppService.createRegistration(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/registrations/{id}")
    @RepeatSubmit
    public ApiResponse<CompetitionRegistrationVO.Registration> updateRegistration(
            @PathVariable("id") Long id,
            @Valid @RequestBody CompetitionRegistrationDTO.RegistrationCreateRequest request
    ) {
        CurrentUser currentUser = require(REGISTRATION_UPDATE);
        return ApiResponse.success(registrationAppService.updateRegistration(currentUser, id, request), TraceContext.getRequestId());
    }

    @PostMapping("/registrations/{id}/materials")
    @RepeatSubmit
    public ApiResponse<CompetitionRegistrationVO.Registration> submitMaterials(
            @PathVariable("id") Long id,
            @Valid @RequestBody CompetitionRegistrationDTO.MaterialSubmitRequest request
    ) {
        CurrentUser currentUser = require(MATERIAL_SUBMIT);
        return ApiResponse.success(registrationAppService.submitMaterials(currentUser, id, request), TraceContext.getRequestId());
    }

    @GetMapping("/registrations/{id}/materials")
    public ApiResponse<List<CompetitionRegistrationVO.MaterialSubmission>> materials(@PathVariable("id") Long id) {
        CurrentUser currentUser = requireRegistrationReadAccess();
        return ApiResponse.success(registrationAppService.listMaterials(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/registrations/{id}/payment-order")
    @RepeatSubmit
    public ApiResponse<CompetitionRegistrationVO.PaymentOrder> createPaymentOrder(
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) CompetitionRegistrationDTO.PaymentOrderRequest request
    ) {
        CurrentUser currentUser = require(REGISTRATION_PAY);
        return ApiResponse.success(
                registrationAppService.createPaymentOrder(
                        currentUser,
                        id,
                        Optional.ofNullable(request).orElseGet(CompetitionRegistrationDTO.PaymentOrderRequest::new)
                ),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/registrations/{id}/payment-status")
    public ApiResponse<CompetitionRegistrationVO.PaymentOrder> paymentStatus(@PathVariable("id") Long id) {
        CurrentUser currentUser = requireRegistrationReadAccess();
        return ApiResponse.success(registrationAppService.getPaymentStatus(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/competitions/{competitionId}/stages")
    public ApiResponse<List<CompetitionRegistrationVO.Stage>> stages(@PathVariable("competitionId") Long competitionId) {
        CurrentUser currentUser = requireStageReadAccess();
        return ApiResponse.success(registrationAppService.listStages(currentUser, competitionId), TraceContext.getRequestId());
    }

    @PostMapping("/competitions/{competitionId}/stages")
    @RepeatSubmit
    public ApiResponse<CompetitionRegistrationVO.Stage> createStage(
            @PathVariable("competitionId") Long competitionId,
            @Valid @RequestBody CompetitionRegistrationDTO.StageUpsertRequest request
    ) {
        CurrentUser currentUser = require(STAGE_MANAGE);
        return ApiResponse.success(registrationAppService.createStage(currentUser, competitionId, request), TraceContext.getRequestId());
    }

    @GetMapping("/stages/{stageId}/form")
    public ApiResponse<CompetitionRegistrationVO.StageForm> stageForm(@PathVariable("stageId") Long stageId) {
        CurrentUser currentUser = requireStageReadAccess();
        return ApiResponse.success(registrationAppService.getStageForm(currentUser, stageId), TraceContext.getRequestId());
    }

    @PutMapping("/stages/{stageId}/form")
    @RepeatSubmit
    public ApiResponse<CompetitionRegistrationVO.StageForm> upsertStageForm(
            @PathVariable("stageId") Long stageId,
            @Valid @RequestBody CompetitionRegistrationDTO.StageFormUpsertRequest request
    ) {
        CurrentUser currentUser = require(STAGE_MANAGE);
        return ApiResponse.success(registrationAppService.upsertStageForm(currentUser, stageId, request), TraceContext.getRequestId());
    }

    private CurrentUser require(String permissionKey) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        currentUser = requireTrustedUser(currentUser);
        permissionGuard.requirePermission(currentUser, permissionKey);
        return currentUser;
    }

    private CurrentUser requireRegistrationReadAccess() {
        CurrentUser currentUser = requireTrustedUser(securityContextFacade.getCurrentUser());
        if (
                permissionGuard.hasPermission(currentUser, REGISTRATION_VIEW)
                        || permissionGuard.hasPermission(currentUser, REGISTRATION_CREATE)
                        || permissionGuard.hasPermission(currentUser, REGISTRATION_UPDATE)
                        || permissionGuard.hasPermission(currentUser, REGISTRATION_PAY)
                        || permissionGuard.hasPermission(currentUser, MATERIAL_VIEW)
                        || permissionGuard.hasPermission(currentUser, MATERIAL_SUBMIT)
                        || permissionGuard.hasPermission(currentUser, PAYMENT_ORDER_VIEW)
                        || permissionGuard.hasPermission(currentUser, STAGE_MANAGE)
        ) {
            return currentUser;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "褰撳墠璐﹀彿娌℃湁璁块棶鏉冮檺");
    }

    private CurrentUser requireStageReadAccess() {
        CurrentUser currentUser = requireTrustedUser(securityContextFacade.getCurrentUser());
        if (
                permissionGuard.hasPermission(currentUser, STAGE_VIEW)
                        || permissionGuard.hasPermission(currentUser, REGISTRATION_VIEW)
                        || permissionGuard.hasPermission(currentUser, REGISTRATION_CREATE)
                        || permissionGuard.hasPermission(currentUser, REGISTRATION_UPDATE)
                        || permissionGuard.hasPermission(currentUser, REGISTRATION_PAY)
                        || permissionGuard.hasPermission(currentUser, MATERIAL_VIEW)
                        || permissionGuard.hasPermission(currentUser, MATERIAL_SUBMIT)
                        || permissionGuard.hasPermission(currentUser, PAYMENT_ORDER_VIEW)
                        || permissionGuard.hasPermission(currentUser, STAGE_MANAGE)
        ) {
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
