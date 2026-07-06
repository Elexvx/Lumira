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
import com.lumira.common.web.repeatsubmit.ClientIpResolver;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
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
    private static final String STATUS_ENABLED = "ENABLED";

    private final CertificateAppService certificateAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final ClientIpResolver clientIpResolver;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;

    public CertificateV2Controller(
            CertificateAppService certificateAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            ClientIpResolver clientIpResolver
    ) {
        this(certificateAppService, securityContextFacade, permissionGuard, clientIpResolver, null, null, null);
    }

    public CertificateV2Controller(
            CertificateAppService certificateAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            ClientIpResolver clientIpResolver,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(
                certificateAppService,
                securityContextFacade,
                permissionGuard,
                clientIpResolver,
                permissionSnapshotService,
                null,
                null
        );
    }

    public CertificateV2Controller(
            CertificateAppService certificateAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            ClientIpResolver clientIpResolver,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                certificateAppService,
                securityContextFacade,
                permissionGuard,
                clientIpResolver,
                permissionSnapshotService,
                null,
                sessionAuthenticationService
        );
    }

    @Autowired
    public CertificateV2Controller(
            CertificateAppService certificateAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            ClientIpResolver clientIpResolver,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this.certificateAppService = certificateAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.clientIpResolver = clientIpResolver;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
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
