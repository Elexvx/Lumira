package com.lumira.saas.modules.competition.controller;

import com.lumira.common.api.ApiResponse;
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
        require(REGISTRATION_VIEW);
        return ApiResponse.success(
                registrationAppService.listRegistrations(securityContextFacade.getCurrentUser(), pageNo, pageSize),
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
        require(PAYMENT_ORDER_VIEW);
        return ApiResponse.success(
                registrationAppService.listPaymentRecords(
                        securityContextFacade.getCurrentUser(),
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
        require(REGISTRATION_VIEW);
        return ApiResponse.success(registrationAppService.getRegistration(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/registrations")
    @RepeatSubmit
    public ApiResponse<CompetitionRegistrationVO.Registration> createRegistration(@Valid @RequestBody CompetitionRegistrationDTO.RegistrationCreateRequest request) {
        require(REGISTRATION_CREATE);
        return ApiResponse.success(registrationAppService.createRegistration(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/registrations/{id}")
    @RepeatSubmit
    public ApiResponse<CompetitionRegistrationVO.Registration> updateRegistration(
            @PathVariable("id") Long id,
            @Valid @RequestBody CompetitionRegistrationDTO.RegistrationCreateRequest request
    ) {
        require(REGISTRATION_UPDATE);
        return ApiResponse.success(registrationAppService.updateRegistration(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @PostMapping("/registrations/{id}/materials")
    @RepeatSubmit
    public ApiResponse<CompetitionRegistrationVO.Registration> submitMaterials(
            @PathVariable("id") Long id,
            @Valid @RequestBody CompetitionRegistrationDTO.MaterialSubmitRequest request
    ) {
        require(MATERIAL_SUBMIT);
        return ApiResponse.success(registrationAppService.submitMaterials(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @GetMapping("/registrations/{id}/materials")
    public ApiResponse<List<CompetitionRegistrationVO.MaterialSubmission>> materials(@PathVariable("id") Long id) {
        require(MATERIAL_VIEW);
        return ApiResponse.success(registrationAppService.listMaterials(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/registrations/{id}/payment-order")
    @RepeatSubmit
    public ApiResponse<CompetitionRegistrationVO.PaymentOrder> createPaymentOrder(
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) CompetitionRegistrationDTO.PaymentOrderRequest request
    ) {
        require(REGISTRATION_PAY);
        return ApiResponse.success(
                registrationAppService.createPaymentOrder(
                        securityContextFacade.getCurrentUser(),
                        id,
                        Optional.ofNullable(request).orElseGet(CompetitionRegistrationDTO.PaymentOrderRequest::new)
                ),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/registrations/{id}/payment-status")
    public ApiResponse<CompetitionRegistrationVO.PaymentOrder> paymentStatus(@PathVariable("id") Long id) {
        require(REGISTRATION_VIEW);
        return ApiResponse.success(registrationAppService.getPaymentStatus(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/competitions/{competitionId}/stages")
    public ApiResponse<List<CompetitionRegistrationVO.Stage>> stages(@PathVariable("competitionId") Long competitionId) {
        require(STAGE_VIEW);
        return ApiResponse.success(registrationAppService.listStages(securityContextFacade.getCurrentUser(), competitionId), TraceContext.getRequestId());
    }

    @PostMapping("/competitions/{competitionId}/stages")
    @RepeatSubmit
    public ApiResponse<CompetitionRegistrationVO.Stage> createStage(
            @PathVariable("competitionId") Long competitionId,
            @Valid @RequestBody CompetitionRegistrationDTO.StageUpsertRequest request
    ) {
        require(STAGE_MANAGE);
        return ApiResponse.success(registrationAppService.createStage(securityContextFacade.getCurrentUser(), competitionId, request), TraceContext.getRequestId());
    }

    @GetMapping("/stages/{stageId}/form")
    public ApiResponse<CompetitionRegistrationVO.StageForm> stageForm(@PathVariable("stageId") Long stageId) {
        require(STAGE_VIEW);
        return ApiResponse.success(registrationAppService.getStageForm(securityContextFacade.getCurrentUser(), stageId), TraceContext.getRequestId());
    }

    @PutMapping("/stages/{stageId}/form")
    @RepeatSubmit
    public ApiResponse<CompetitionRegistrationVO.StageForm> upsertStageForm(
            @PathVariable("stageId") Long stageId,
            @Valid @RequestBody CompetitionRegistrationDTO.StageFormUpsertRequest request
    ) {
        require(STAGE_MANAGE);
        return ApiResponse.success(registrationAppService.upsertStageForm(securityContextFacade.getCurrentUser(), stageId, request), TraceContext.getRequestId());
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
