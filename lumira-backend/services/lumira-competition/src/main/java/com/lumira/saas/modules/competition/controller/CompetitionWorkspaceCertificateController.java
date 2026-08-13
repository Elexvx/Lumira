package com.lumira.saas.modules.competition.controller;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.vo.PageResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.saas.modules.competition.app.CompetitionAccessDecision;
import com.lumira.saas.modules.competition.app.CompetitionAuthenticationTrust;
import com.lumira.saas.modules.competition.app.CompetitionCapability;
import com.lumira.saas.modules.competition.app.CompetitionRef;
import com.lumira.saas.modules.competition.app.CompetitionWorkspaceAccessPolicy;
import com.lumira.saas.modules.competition.app.CertificateAppService;
import com.lumira.saas.modules.competition.dto.CertificateDTO;
import com.lumira.saas.modules.competition.repository.CertificateRecordRepository;
import com.lumira.saas.modules.competition.vo.CertificateVO;
import jakarta.validation.Valid;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UUID-scoped certificate APIs for a competition workspace. */
@RestController
@RequestMapping("/api/v2/aiadc/competitions/{competitionUuid}")
public class CompetitionWorkspaceCertificateController {
    private final CertificateAppService certificateAppService;
    private final CertificateRecordRepository certificateRecordRepository;
    private final CompetitionWorkspaceAccessPolicy accessPolicy;
    private final SecurityContextFacade securityContextFacade;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;

    @Autowired
    public CompetitionWorkspaceCertificateController(
            CertificateAppService certificateAppService,
            CertificateRecordRepository certificateRecordRepository,
            CompetitionWorkspaceAccessPolicy accessPolicy,
            SecurityContextFacade securityContextFacade,
            TrustedCurrentUserResolver trustedCurrentUserResolver
    ) {
        this.certificateAppService = certificateAppService;
        this.certificateRecordRepository = certificateRecordRepository;
        this.accessPolicy = accessPolicy;
        this.securityContextFacade = securityContextFacade;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
    }

    @GetMapping("/certificate-award-sources")
    public ApiResponse<List<CertificateVO.AwardSource>> awardSources(@PathVariable String competitionUuid) {
        CurrentUser currentUser = requireTrustedUser();
        CompetitionAccessDecision decision = accessPolicy.requireAccessibleCompetition(currentUser, competitionUuid, CompetitionCapability.CERTIFICATE_MANAGE);
        Long competitionId = decision.competition().id();
        return ApiResponse.success(
                certificateAppService.listPublishedAwardSources(currentUser).stream()
                        .filter(source -> Objects.equals(competitionId, source.getCompetitionId()))
                        .toList(),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/certificate-awards/grant")
    @RepeatSubmit
    public ApiResponse<List<CertificateVO.AwardGrant>> grantAwards(
            @PathVariable String competitionUuid,
            @Valid @RequestBody CertificateDTO.AwardGrantRequest request
    ) {
        CurrentUser currentUser = requireTrustedUser();
        CompetitionAccessDecision decision = accessPolicy.requireAccessibleCompetition(currentUser, competitionUuid, CompetitionCapability.CERTIFICATE_MANAGE);
        Long competitionId = decision.competition().id();
        requirePublishedBatch(currentUser, request.getReviewBatchId(), competitionId);
        return ApiResponse.success(
                certificateAppService.grantPublishedAwards(currentUser, request).stream()
                        .filter(grant -> Objects.equals(competitionId, grant.getCompetitionId()))
                        .toList(),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/certificate-awards")
    public ApiResponse<List<CertificateVO.AwardGrant>> awardGrants(
            @PathVariable String competitionUuid,
            @RequestParam("reviewBatchId") Long reviewBatchId
    ) {
        CurrentUser currentUser = requireTrustedUser();
        CompetitionAccessDecision decision = accessPolicy.requireAccessibleCompetition(currentUser, competitionUuid, CompetitionCapability.CERTIFICATE_READ);
        Long competitionId = decision.competition().id();
        requirePublishedBatch(currentUser, reviewBatchId, competitionId);
        return ApiResponse.success(
                certificateAppService.listAwardGrants(currentUser, reviewBatchId).stream()
                        .filter(grant -> Objects.equals(competitionId, grant.getCompetitionId()))
                        .toList(),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/certificate-batches")
    @RepeatSubmit
    public ApiResponse<CertificateVO.GenerateResult> generateBatch(
            @PathVariable String competitionUuid,
            @Valid @RequestBody CertificateDTO.BatchGenerateRequest request
    ) {
        CompetitionAccessDecision decision = requireWorkspace(competitionUuid, CompetitionCapability.CERTIFICATE_MANAGE);
        request.setCompetitionId(decision.competition().id());
        return ApiResponse.success(
                certificateAppService.generateBatch(requireTrustedUser(), request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/certificate-batches/from-awards")
    @RepeatSubmit
    public ApiResponse<CertificateVO.GenerateResult> generateAwardCertificates(
            @PathVariable String competitionUuid,
            @Valid @RequestBody CertificateDTO.AwardCertificateGenerateRequest request
    ) {
        CompetitionAccessDecision decision = requireWorkspace(competitionUuid, CompetitionCapability.CERTIFICATE_MANAGE);
        requireAwardGrants(requireTrustedUser(), request.getGrantIds(), decision.competition().id());
        return ApiResponse.success(
                certificateAppService.generateAwardCertificates(requireTrustedUser(), request),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/certificate-batches")
    public ApiResponse<PageResponse<CertificateVO.Batch>> batches(
            @PathVariable String competitionUuid,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CompetitionAccessDecision decision = requireWorkspace(competitionUuid, CompetitionCapability.CERTIFICATE_READ);
        PageResponse<CertificateVO.Batch> page = certificateAppService.listBatches(
                requireTrustedUser(), pageNo, pageSize, decision.competition().id());
        return ApiResponse.success(page, TraceContext.getRequestId());
    }

    @GetMapping("/certificate-batches/{id}")
    public ApiResponse<CertificateVO.Batch> batch(
            @PathVariable String competitionUuid,
            @PathVariable Long id
    ) {
        CompetitionAccessDecision decision = requireWorkspace(competitionUuid, CompetitionCapability.CERTIFICATE_READ);
        CertificateVO.Batch batch = certificateAppService.getBatch(requireTrustedUser(), id);
        requireSameCompetition(batch == null ? null : batch.getCompetitionId(), decision.competition().id(), "Certificate batch not found");
        return ApiResponse.success(batch, TraceContext.getRequestId());
    }

    @GetMapping("/certificates")
    public ApiResponse<PageResponse<CertificateVO.Record>> certificates(
            @PathVariable String competitionUuid,
            @RequestParam(name = "certificateNo", required = false) String certificateNo,
            @RequestParam(name = "recipientName", required = false) String recipientName,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CompetitionAccessDecision decision = requireWorkspace(competitionUuid, CompetitionCapability.CERTIFICATE_READ);
        PageResponse<CertificateVO.Record> page = certificateAppService.listRecords(
                requireTrustedUser(), certificateNo, recipientName, status, pageNo, pageSize,
                decision.competition().id());
        return ApiResponse.success(page, TraceContext.getRequestId());
    }

    @GetMapping("/certificates/{id}")
    public ApiResponse<CertificateVO.Record> certificate(
            @PathVariable String competitionUuid,
            @PathVariable Long id
    ) {
        CompetitionAccessDecision decision = requireWorkspace(competitionUuid, CompetitionCapability.CERTIFICATE_READ);
        CertificateVO.Record record = certificateAppService.getRecord(requireTrustedUser(), id);
        requireSameCompetition(record == null ? null : record.getCompetitionId(), decision.competition().id(), "Certificate not found");
        return ApiResponse.success(record, TraceContext.getRequestId());
    }

    @GetMapping("/certificates/{id}/download")
    public ResponseEntity<FileSystemResource> downloadCertificate(
            @PathVariable String competitionUuid,
            @PathVariable Long id
    ) {
        CompetitionAccessDecision decision = requireWorkspace(competitionUuid, CompetitionCapability.CERTIFICATE_READ);
        CertificateVO.Record record = certificateAppService.getRecordForDownload(requireTrustedUser(), id);
        requireSameCompetition(record == null ? null : record.getCompetitionId(), decision.competition().id(), "Certificate not found");
        Path path = resolveCertificateFilePath(record.getCertificateFileUrl());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + record.getCertificateNo() + ".png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(new FileSystemResource(path));
    }

    @PostMapping("/certificates/{id}/regenerate")
    @RepeatSubmit
    public ApiResponse<CertificateVO.Record> regenerateCertificate(
            @PathVariable String competitionUuid,
            @PathVariable Long id
    ) {
        CompetitionAccessDecision decision = requireWorkspace(competitionUuid, CompetitionCapability.CERTIFICATE_MANAGE);
        CurrentUser currentUser = requireTrustedUser();
        requireRecord(currentUser, id, decision.competition().id());
        return ApiResponse.success(certificateAppService.regenerateRecord(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/certificates/{id}/revoke")
    @RepeatSubmit
    public ApiResponse<CertificateVO.Record> revokeCertificate(
            @PathVariable String competitionUuid,
            @PathVariable Long id,
            @RequestBody(required = false) CertificateDTO.RevokeRequest request
    ) {
        CompetitionAccessDecision decision = requireWorkspace(competitionUuid, CompetitionCapability.CERTIFICATE_MANAGE);
        CurrentUser currentUser = requireTrustedUser();
        requireRecord(currentUser, id, decision.competition().id());
        return ApiResponse.success(
                certificateAppService.revokeRecord(currentUser, id, request == null ? null : request.getReason()),
                TraceContext.getRequestId()
        );
    }

    private CompetitionAccessDecision requireWorkspace(String competitionUuid, CompetitionCapability capability) {
        CurrentUser currentUser = requireTrustedUser();
        return accessPolicy.requireAccessibleCompetition(currentUser, competitionUuid, capability);
    }

    private void requirePublishedBatch(CurrentUser currentUser, Long reviewBatchId, Long competitionId) {
        if (reviewBatchId == null || certificateRecordRepository.findPublishedAwardSources().stream()
                .noneMatch(source -> Objects.equals(reviewBatchId, source.getReviewBatchId())
                        && Objects.equals(competitionId, source.getCompetitionId()))) {
            throw new BizException(ErrorCode.NOT_FOUND, "Review batch not found");
        }
    }

    private void requireAwardGrants(CurrentUser currentUser, List<Long> grantIds, Long competitionId) {
        if (grantIds == null || grantIds.isEmpty()) {
            return;
        }
        List<CertificateVO.AwardGrant> grants = certificateRecordRepository.findAwardGrantsByAnyIds(grantIds);
        if (grants.stream().anyMatch(grant -> !Objects.equals(competitionId, grant.getCompetitionId()))) {
            throw new BizException(ErrorCode.NOT_FOUND, "Award grants not found");
        }
    }

    private void requireRecord(CurrentUser currentUser, Long id, Long competitionId) {
        CertificateVO.Record record = certificateAppService.getRecord(currentUser, id);
        requireSameCompetition(record == null ? null : record.getCompetitionId(), competitionId, "Certificate not found");
    }

    private void requireSameCompetition(Long resourceCompetitionId, Long competitionId, String message) {
        if (resourceCompetitionId == null || !Objects.equals(resourceCompetitionId, competitionId)) {
            throw new BizException(ErrorCode.NOT_FOUND, message);
        }
    }

    private CurrentUser requireTrustedUser() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        CompetitionAuthenticationTrust.refresh(currentUser, trustedCurrentUserResolver, true);
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser;
    }

    private Path resolveCertificateFilePath(String certificateFileUrl) {
        if (certificateFileUrl == null || certificateFileUrl.isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND, "Certificate file not found");
        }
        Path base = Path.of("storage", "certificates").toAbsolutePath().normalize();
        Path path = Path.of(certificateFileUrl.replaceFirst("^/", "")).toAbsolutePath().normalize();
        if (!path.startsWith(base)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Invalid certificate file path");
        }
        return path;
    }
}
