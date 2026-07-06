package com.lumira.saas.modules.competition.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.competition.app.CompetitionRegistrationAppService;
import com.lumira.saas.modules.competition.dto.CompetitionRegistrationDTO;
import com.lumira.saas.modules.competition.vo.CompetitionRegistrationVO;
import jakarta.validation.Valid;
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

    private final CompetitionRegistrationAppService registrationAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public CompetitionRegistrationV2Controller(
            CompetitionRegistrationAppService registrationAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.registrationAppService = registrationAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
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
        permissionGuard.requirePermission(currentUser, permissionKey);
        return requireTrustedUser(currentUser);
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
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser;
    }
}
