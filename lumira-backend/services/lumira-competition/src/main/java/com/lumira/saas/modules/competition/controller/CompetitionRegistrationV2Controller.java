package com.lumira.saas.modules.competition.controller;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.file.FileContentDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.competition.app.CompetitionAuthenticationTrust;
import com.lumira.saas.modules.competition.app.CompetitionRegistrationAppService;
import com.lumira.saas.modules.competition.dto.CompetitionRegistrationDTO;
import com.lumira.saas.modules.competition.vo.CompetitionRegistrationVO;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
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
    private static final String MATERIAL_DOWNLOAD = "registration:material:download";

    private final CompetitionRegistrationAppService registrationAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final FileInternalApi fileInternalApi;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;
    private final boolean enforceTrustedUserResolution;
    @Value("${saas.workflow.legacy-stage-review-enabled:false}")
    private boolean legacyStageReviewEnabled;

    public CompetitionRegistrationV2Controller(
            CompetitionRegistrationAppService registrationAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(registrationAppService, securityContextFacade, permissionGuard, null, null, false);
    }

    @Autowired
    public CompetitionRegistrationV2Controller(
            CompetitionRegistrationAppService registrationAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            FileInternalApi fileInternalApi,
            TrustedCurrentUserResolver trustedCurrentUserResolver
    ) {
        this(
                registrationAppService,
                securityContextFacade,
                permissionGuard,
                fileInternalApi,
                trustedCurrentUserResolver,
                true
        );
    }

    private CompetitionRegistrationV2Controller(
            CompetitionRegistrationAppService registrationAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            FileInternalApi fileInternalApi,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            boolean enforceTrustedUserResolution
    ) {
        this.registrationAppService = registrationAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.fileInternalApi = fileInternalApi;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @GetMapping("/registrations")
    public ApiResponse<PageResponse<CompetitionRegistrationVO.Registration>> registrations(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize,
            @RequestParam(name = "competitionId", required = false) Long competitionId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "includeSnapshots", defaultValue = "false") boolean includeSnapshots
    ) {
        CurrentUser currentUser = requireRegistrationReadAccess();
        return ApiResponse.success(
                registrationAppService.listRegistrations(
                        currentUser,
                        pageNo,
                        pageSize,
                        competitionId,
                        status,
                        keyword,
                        includeSnapshots
                ),
                TraceContext.getRequestId()
        );
    }

    public ApiResponse<PageResponse<CompetitionRegistrationVO.Registration>> registrations(
            long pageNo,
            long pageSize
    ) {
        return registrations(pageNo, pageSize, null, null, null, false);
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

    @PostMapping("/registrations/confirm")
    @RepeatSubmit
    public ApiResponse<CompetitionRegistrationVO.Registration> confirmRegistration(
            @Valid @RequestBody CompetitionRegistrationDTO.RegistrationConfirmRequest request
    ) {
        CurrentUser currentUser = require(REGISTRATION_CREATE);
        return ApiResponse.success(registrationAppService.confirmRegistration(currentUser, null, request), TraceContext.getRequestId());
    }

    @PutMapping("/registrations/{id}/confirm")
    @RepeatSubmit
    public ApiResponse<CompetitionRegistrationVO.Registration> reconfirmRegistration(
            @PathVariable("id") Long id,
            @Valid @RequestBody CompetitionRegistrationDTO.RegistrationConfirmRequest request
    ) {
        CurrentUser currentUser = require(REGISTRATION_UPDATE);
        return ApiResponse.success(registrationAppService.confirmRegistration(currentUser, id, request), TraceContext.getRequestId());
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

    @DeleteMapping("/registrations/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteRegistration(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(REGISTRATION_UPDATE);
        return ApiResponse.success(registrationAppService.deletePendingRegistration(currentUser, id), TraceContext.getRequestId());
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

    @GetMapping("/registrations/{registrationId}/materials/files/{fileId}/download")
    public ResponseEntity<byte[]> downloadMaterialFile(
            @PathVariable("registrationId") Long registrationId,
            @PathVariable("fileId") Long fileId
    ) {
        CurrentUser currentUser = require(MATERIAL_DOWNLOAD);
        registrationAppService.requireMaterialFileAccess(currentUser, registrationId, fileId);
        if (fileInternalApi == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "File service is unavailable");
        }
        FileContentDTO file = fileInternalApi.readFileContentForAuthorizedBusinessReference(
                fileId,
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                currentUser.getUsername(),
                "competition.registration.material",
                registrationId,
                currentUser.getSimulatedRoleId()
        );
        if (file == null || file.content() == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Registration material file not found");
        }
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (StringUtils.hasText(file.mimeType())) {
            try {
                mediaType = MediaType.parseMediaType(file.mimeType());
            } catch (RuntimeException ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }
        String filename = StringUtils.hasText(file.originalFileName())
                ? file.originalFileName()
                : "material-" + fileId;
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString()
                )
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(file.content().length)
                .contentType(mediaType)
                .body(file.content());
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

    @GetMapping("/registrations/{id}/payment-options")
    public ApiResponse<List<CompetitionRegistrationVO.PaymentOption>> paymentOptions(
            @PathVariable("id") Long id,
            @RequestParam(name = "clientType", required = false) String clientType
    ) {
        CurrentUser currentUser = require(REGISTRATION_PAY);
        return ApiResponse.success(registrationAppService.listPaymentOptions(currentUser, id, clientType), TraceContext.getRequestId());
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

    @PutMapping("/stages/{stageId}")
    @RepeatSubmit
    public ApiResponse<CompetitionRegistrationVO.Stage> updateStage(
            @PathVariable("stageId") Long stageId,
            @Valid @RequestBody CompetitionRegistrationDTO.StageUpsertRequest request
    ) {
        CurrentUser currentUser = require(STAGE_MANAGE);
        return ApiResponse.success(registrationAppService.updateStage(currentUser, stageId, request), TraceContext.getRequestId());
    }

    @GetMapping("/stages/{stageId}/review-candidates")
    public ApiResponse<List<CompetitionRegistrationVO.StageReviewCandidate>> reviewCandidates(@PathVariable("stageId") Long stageId) {
        requireLegacyStageReviewEnabled();
        CurrentUser currentUser = require(STAGE_MANAGE);
        return ApiResponse.success(registrationAppService.listStageReviewCandidates(currentUser, stageId), TraceContext.getRequestId());
    }

    @PutMapping("/stages/{stageId}/review-candidates/{registrationId}")
    @RepeatSubmit
    public ApiResponse<CompetitionRegistrationVO.StageReviewCandidate> saveReviewDecision(
            @PathVariable("stageId") Long stageId,
            @PathVariable("registrationId") Long registrationId,
            @Valid @RequestBody CompetitionRegistrationDTO.StageReviewDecisionRequest request
    ) {
        requireLegacyStageReviewEnabled();
        CurrentUser currentUser = require(STAGE_MANAGE);
        return ApiResponse.success(
                registrationAppService.saveStageReviewDecision(currentUser, stageId, registrationId, request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/stages/{stageId}/apply-promotion-rule")
    @RepeatSubmit
    public ApiResponse<List<CompetitionRegistrationVO.StageReviewCandidate>> applyPromotionRule(@PathVariable("stageId") Long stageId) {
        requireLegacyStageReviewEnabled();
        CurrentUser currentUser = require(STAGE_MANAGE);
        return ApiResponse.success(registrationAppService.applyStagePromotionRule(currentUser, stageId), TraceContext.getRequestId());
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

    private void requireLegacyStageReviewEnabled() {
        if (!legacyStageReviewEnabled) {
            throw new BizException(
                    ErrorCode.BIZ_ERROR,
                    "Legacy stage review API is disabled; use the review workbench"
            );
        }
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
        throw new BizException(ErrorCode.FORBIDDEN, "Current user does not have permission to view competition registrations");
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
        throw new BizException(ErrorCode.FORBIDDEN, "Current account does not have access permission");
    }

    private CurrentUser requireTrustedUser(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser;
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        CompetitionAuthenticationTrust.refresh(
                currentUser,
                trustedCurrentUserResolver,
                enforceTrustedUserResolution
        );
    }
}
