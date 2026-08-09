package com.lumira.saas.modules.competition.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.ClientIpResolver;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.competition.app.CompetitionAuthenticationTrust;
import com.lumira.saas.modules.competition.app.CertificateAppService;
import com.lumira.saas.modules.competition.dto.CertificateDTO;
import com.lumira.saas.modules.competition.vo.CertificateVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

@RestController
@RequestMapping
public class CertificateV2Controller {
    private static final String TEMPLATE_VIEW = "aiadc:certificate-template:view";
    private static final String TEMPLATE_CREATE = "aiadc:certificate-template:create";
    private static final String TEMPLATE_UPDATE = "aiadc:certificate-template:update";
    private static final String TEMPLATE_PUBLISH = "aiadc:certificate-template:publish";
    private static final String TEMPLATE_DELETE = "aiadc:certificate-template:delete";
    private static final String BATCH_VIEW = "aiadc:certificate-batch:view";
    private static final String BATCH_CREATE = "aiadc:certificate-batch:create";
    private static final String CERTIFICATE_VIEW = "aiadc:certificate:view";
    private static final String CERTIFICATE_DOWNLOAD = "aiadc:certificate:download";
    private static final String CERTIFICATE_REGENERATE = "aiadc:certificate:regenerate";
    private static final String CERTIFICATE_REVOKE = "aiadc:certificate:revoke";

    private final CertificateAppService certificateAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final ClientIpResolver clientIpResolver;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;
    private final boolean enforceTrustedUserResolution;

    public CertificateV2Controller(
            CertificateAppService certificateAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            ClientIpResolver clientIpResolver
    ) {
        this(certificateAppService, securityContextFacade, permissionGuard, clientIpResolver, null, false);
    }

    @Autowired
    public CertificateV2Controller(
            CertificateAppService certificateAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            ClientIpResolver clientIpResolver,
            TrustedCurrentUserResolver trustedCurrentUserResolver
    ) {
        this(certificateAppService, securityContextFacade, permissionGuard, clientIpResolver, trustedCurrentUserResolver, true);
    }

    private CertificateV2Controller(
            CertificateAppService certificateAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            ClientIpResolver clientIpResolver,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            boolean enforceTrustedUserResolution
    ) {
        this.certificateAppService = certificateAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.clientIpResolver = clientIpResolver;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @GetMapping("/api/v2/aiadc/certificate-templates")
    public ApiResponse<PageResponse<CertificateVO.Template>> templates(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require(TEMPLATE_VIEW);
        return ApiResponse.success(certificateAppService.listTemplates(currentUser, keyword, status, pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/api/v2/aiadc/certificate-templates/{id}")
    public ApiResponse<CertificateVO.Template> template(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(TEMPLATE_VIEW);
        return ApiResponse.success(certificateAppService.getTemplate(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/api/v2/aiadc/certificate-templates")
    @RepeatSubmit
    public ApiResponse<CertificateVO.Template> createTemplate(@Valid @RequestBody CertificateDTO.TemplateUpsertRequest request) {
        CurrentUser currentUser = require(TEMPLATE_CREATE);
        return ApiResponse.success(certificateAppService.createTemplate(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/api/v2/aiadc/certificate-templates/{id}")
    @RepeatSubmit
    public ApiResponse<CertificateVO.Template> updateTemplate(@PathVariable("id") Long id, @Valid @RequestBody CertificateDTO.TemplateUpsertRequest request) {
        CurrentUser currentUser = require(TEMPLATE_UPDATE);
        return ApiResponse.success(certificateAppService.updateTemplate(currentUser, id, request), TraceContext.getRequestId());
    }

    @PostMapping("/api/v2/aiadc/certificate-templates/{id}/publish")
    @RepeatSubmit
    public ApiResponse<CertificateVO.TemplateVersion> publishTemplate(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(TEMPLATE_PUBLISH);
        return ApiResponse.success(certificateAppService.publishTemplate(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/api/v2/aiadc/certificate-templates/{id}/duplicate")
    @RepeatSubmit
    public ApiResponse<CertificateVO.Template> duplicateTemplate(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(TEMPLATE_CREATE);
        return ApiResponse.success(certificateAppService.duplicateTemplate(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/api/v2/aiadc/certificate-templates/{id}/archive")
    @RepeatSubmit
    public ApiResponse<CertificateVO.Template> archiveTemplate(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(TEMPLATE_DELETE);
        return ApiResponse.success(certificateAppService.archiveTemplate(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/api/v2/aiadc/certificate-templates/{id}/versions")
    public ApiResponse<List<CertificateVO.TemplateVersion>> versions(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(TEMPLATE_VIEW);
        return ApiResponse.success(certificateAppService.listVersions(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/api/v2/aiadc/certificate-template-versions/{versionId}")
    public ApiResponse<CertificateVO.TemplateVersion> version(@PathVariable("versionId") Long versionId) {
        CurrentUser currentUser = require(TEMPLATE_VIEW);
        return ApiResponse.success(certificateAppService.getVersion(currentUser, versionId), TraceContext.getRequestId());
    }

    @PutMapping("/api/v2/aiadc/certificate-template-versions/{versionId}/canvas")
    @RepeatSubmit
    public ApiResponse<CertificateVO.TemplateVersion> saveCanvas(
            @PathVariable("versionId") Long versionId,
            @Valid @RequestBody CertificateDTO.CanvasSaveRequest request
    ) {
        CurrentUser currentUser = require(TEMPLATE_UPDATE);
        return ApiResponse.success(certificateAppService.saveCanvas(currentUser, versionId, request), TraceContext.getRequestId());
    }

    @PostMapping(value = "/api/v2/aiadc/certificate-template-versions/{versionId}/background", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RepeatSubmit
    public ApiResponse<CertificateVO.TemplateVersion> uploadBackground(
            @PathVariable("versionId") Long versionId,
            @RequestPart("file") MultipartFile file
    ) {
        CurrentUser currentUser = require(TEMPLATE_UPDATE);
        return ApiResponse.success(certificateAppService.uploadBackground(currentUser, versionId, file), TraceContext.getRequestId());
    }

    @PostMapping("/api/v2/aiadc/certificate-batches/preview")
    public ApiResponse<CertificateVO.GenerateResult> previewBatch(@Valid @RequestBody CertificateDTO.BatchGenerateRequest request) {
        CurrentUser currentUser = require(BATCH_CREATE);
        return ApiResponse.success(certificateAppService.previewBatch(currentUser, request), TraceContext.getRequestId());
    }

    @PostMapping("/api/v2/aiadc/certificate-batches")
    @RepeatSubmit
    public ApiResponse<CertificateVO.GenerateResult> generateBatch(@Valid @RequestBody CertificateDTO.BatchGenerateRequest request) {
        CurrentUser currentUser = require(BATCH_CREATE);
        return ApiResponse.success(certificateAppService.generateBatch(currentUser, request), TraceContext.getRequestId());
    }

    @PostMapping("/api/v2/aiadc/certificate-awards/grant")
    public ApiResponse<List<CertificateVO.AwardGrant>> grantPublishedAwards(
            @Valid @RequestBody CertificateDTO.AwardGrantRequest request
    ) {
        CurrentUser currentUser = require(BATCH_CREATE);
        return ApiResponse.success(
                certificateAppService.grantPublishedAwards(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/api/v2/aiadc/certificate-award-sources")
    public ApiResponse<List<CertificateVO.AwardSource>> awardSources() {
        CurrentUser currentUser = require(BATCH_CREATE);
        return ApiResponse.success(
                certificateAppService.listPublishedAwardSources(currentUser),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/api/v2/aiadc/certificate-awards")
    public ApiResponse<List<CertificateVO.AwardGrant>> awardGrants(
            @RequestParam("reviewBatchId") Long reviewBatchId
    ) {
        CurrentUser currentUser = require(BATCH_CREATE);
        return ApiResponse.success(
                certificateAppService.listAwardGrants(currentUser, reviewBatchId),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/api/v2/aiadc/certificate-batches/from-awards")
    @RepeatSubmit
    public ApiResponse<CertificateVO.GenerateResult> generateAwardCertificates(
            @Valid @RequestBody CertificateDTO.AwardCertificateGenerateRequest request
    ) {
        CurrentUser currentUser = require(BATCH_CREATE);
        return ApiResponse.success(
                certificateAppService.generateAwardCertificates(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/api/v2/aiadc/certificate-batches")
    public ApiResponse<PageResponse<CertificateVO.Batch>> batches(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require(BATCH_VIEW);
        return ApiResponse.success(certificateAppService.listBatches(currentUser, pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/api/v2/aiadc/certificate-batches/{id}")
    public ApiResponse<CertificateVO.Batch> batch(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(BATCH_VIEW);
        return ApiResponse.success(certificateAppService.getBatch(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/api/v2/aiadc/certificates")
    public ApiResponse<PageResponse<CertificateVO.Record>> certificates(
            @RequestParam(name = "certificateNo", required = false) String certificateNo,
            @RequestParam(name = "recipientName", required = false) String recipientName,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require(CERTIFICATE_VIEW);
        return ApiResponse.success(certificateAppService.listRecords(currentUser, certificateNo, recipientName, status, pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/api/v2/aiadc/certificates/mine")
    public ApiResponse<List<CertificateVO.Record>> myCertificates() {
        CurrentUser currentUser = requireTrustedUser(securityContextFacade.getCurrentUser());
        return ApiResponse.success(
                certificateAppService.listMyCertificates(currentUser),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/api/v2/aiadc/certificates/mine/{id}/download")
    public ResponseEntity<FileSystemResource> downloadMyCertificate(@PathVariable("id") Long id) {
        CurrentUser currentUser = requireTrustedUser(securityContextFacade.getCurrentUser());
        CertificateVO.Record record = certificateAppService.getMyCertificateForDownload(currentUser, id);
        Path path = resolveCertificateFilePath(record.getCertificateFileUrl());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + record.getCertificateNo() + ".png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(new FileSystemResource(path));
    }

    @GetMapping("/api/v2/aiadc/certificates/{id}")
    public ApiResponse<CertificateVO.Record> certificate(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(CERTIFICATE_VIEW);
        return ApiResponse.success(certificateAppService.getRecord(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/api/v2/aiadc/certificates/{id}/download")
    public ResponseEntity<FileSystemResource> downloadCertificate(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(CERTIFICATE_DOWNLOAD);
        CertificateVO.Record record = certificateAppService.getRecordForDownload(currentUser, id);
        Path path = resolveCertificateFilePath(record.getCertificateFileUrl());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + record.getCertificateNo() + ".png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(new FileSystemResource(path));
    }

    @PostMapping("/api/v2/aiadc/certificates/{id}/regenerate")
    @RepeatSubmit
    public ApiResponse<CertificateVO.Record> regenerateCertificate(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(CERTIFICATE_REGENERATE);
        return ApiResponse.success(certificateAppService.regenerateRecord(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/api/v2/aiadc/certificates/{id}/revoke")
    @RepeatSubmit
    public ApiResponse<CertificateVO.Record> revokeCertificate(@PathVariable("id") Long id, @RequestBody(required = false) CertificateDTO.RevokeRequest request) {
        CurrentUser currentUser = require(CERTIFICATE_REVOKE);
        return ApiResponse.success(
                certificateAppService.revokeRecord(currentUser, id, request == null ? null : request.getReason()),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/api/public/certificates/verify")
    public ApiResponse<CertificateVO.PublicVerifyResult> verifyByCertificateNo(
            @RequestParam("certificateNo") String certificateNo,
            @RequestParam("verificationCode") String verificationCode,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                certificateAppService.verifyByCertificateNo(certificateNo, verificationCode, clientIp(request), request.getHeader(HttpHeaders.USER_AGENT)),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/api/public/certificates/verify/{publicToken}")
    public ApiResponse<CertificateVO.PublicVerifyResult> verifyByToken(@PathVariable("publicToken") String publicToken, HttpServletRequest request) {
        return ApiResponse.success(
                certificateAppService.verifyByToken(publicToken, clientIp(request), request.getHeader(HttpHeaders.USER_AGENT)),
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
        CompetitionAuthenticationTrust.refresh(
                currentUser,
                trustedCurrentUserResolver,
                enforceTrustedUserResolution
        );
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

    private String clientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }
}
