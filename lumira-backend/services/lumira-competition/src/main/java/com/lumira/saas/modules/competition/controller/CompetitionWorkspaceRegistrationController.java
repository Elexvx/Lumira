package com.lumira.saas.modules.competition.controller;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.file.FileContentDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.web.TraceContext;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.competition.app.CompetitionAccessDecision;
import com.lumira.saas.modules.competition.app.CompetitionCapability;
import com.lumira.saas.modules.competition.app.CompetitionAuthenticationTrust;
import com.lumira.saas.modules.competition.app.CompetitionRef;
import com.lumira.saas.modules.competition.app.CompetitionWorkspaceAccessPolicy;
import com.lumira.saas.modules.competition.app.CompetitionRegistrationAppService;
import com.lumira.saas.modules.competition.vo.CompetitionRegistrationVO;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UUID-scoped read APIs for the competition workspace registration modules. */
@RestController
@RequestMapping("/api/v2/aiadc/competitions/{competitionUuid}")
public class CompetitionWorkspaceRegistrationController {
    private static final String MODULE_MATERIAL_REFERENCE = "competition.registration.material";

    private final CompetitionRegistrationAppService registrationAppService;
    private final CompetitionWorkspaceAccessPolicy accessPolicy;
    private final SecurityContextFacade securityContextFacade;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;
    private final FileInternalApi fileInternalApi;

    @Autowired
    public CompetitionWorkspaceRegistrationController(
            CompetitionRegistrationAppService registrationAppService,
            CompetitionWorkspaceAccessPolicy accessPolicy,
            SecurityContextFacade securityContextFacade,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            FileInternalApi fileInternalApi
    ) {
        this.registrationAppService = registrationAppService;
        this.accessPolicy = accessPolicy;
        this.securityContextFacade = securityContextFacade;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
        this.fileInternalApi = fileInternalApi;
    }

    @GetMapping("/registrations")
    public ApiResponse<PageResponse<CompetitionRegistrationVO.Registration>> registrations(
            @PathVariable String competitionUuid,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "20") long pageSize,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "includeSnapshots", defaultValue = "true") boolean includeSnapshots
    ) {
        CurrentUser currentUser = requireTrustedUser();
        CompetitionRef competition = requireReadWorkspace(currentUser, competitionUuid, CompetitionCapability.REGISTRATION_READ);
        return ApiResponse.success(
                registrationAppService.listRegistrations(
                        currentUser,
                        pageNo,
                        pageSize,
                        competition.id(),
                        status,
                        keyword,
                        includeSnapshots
                ),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/registrations/{registrationId}")
    public ApiResponse<CompetitionRegistrationVO.Registration> registration(
            @PathVariable String competitionUuid,
            @PathVariable Long registrationId
    ) {
        CurrentUser currentUser = requireTrustedUser();
        CompetitionRef competition = requireReadWorkspace(currentUser, competitionUuid, CompetitionCapability.REGISTRATION_READ);
        CompetitionRegistrationVO.Registration registration = requireRegistration(currentUser, registrationId);
        requireSameCompetition(registration, competition);
        return ApiResponse.success(registration, TraceContext.getRequestId());
    }

    @GetMapping("/registrations/{registrationId}/materials")
    public ApiResponse<List<CompetitionRegistrationVO.MaterialSubmission>> materials(
            @PathVariable String competitionUuid,
            @PathVariable Long registrationId
    ) {
        CurrentUser currentUser = requireTrustedUser();
        CompetitionRef competition = requireReadWorkspace(currentUser, competitionUuid, CompetitionCapability.REGISTRATION_READ);
        CompetitionRegistrationVO.Registration registration = requireRegistration(currentUser, registrationId);
        requireSameCompetition(registration, competition);
        return ApiResponse.success(
                registrationAppService.listMaterials(currentUser, registrationId),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/workspace/stages")
    public ApiResponse<List<CompetitionRegistrationVO.Stage>> stages(
            @PathVariable String competitionUuid
    ) {
        CurrentUser currentUser = requireTrustedUser();
        CompetitionRef competition = requireReadWorkspace(currentUser, competitionUuid, CompetitionCapability.REGISTRATION_READ);
        return ApiResponse.success(
                registrationAppService.listStages(currentUser, competition.id()),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/payments")
    public ApiResponse<PageResponse<CompetitionRegistrationVO.PaymentRecord>> payments(
            @PathVariable String competitionUuid,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "20") long pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "paymentStatus", required = false) String paymentStatus,
            @RequestParam(name = "registrationStatus", required = false) String registrationStatus,
            @RequestParam(name = "providerCode", required = false) String providerCode
    ) {
        CurrentUser currentUser = requireTrustedUser();
        CompetitionRef competition = requireReadWorkspace(currentUser, competitionUuid, CompetitionCapability.PAYMENT_READ);
        return ApiResponse.success(
                registrationAppService.listPaymentRecords(
                        currentUser,
                        pageNo,
                        pageSize,
                        competition.id(),
                        keyword,
                        paymentStatus,
                        registrationStatus,
                        providerCode
                ),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/registrations/{registrationId}/materials/files/{fileId}/download")
    public ResponseEntity<byte[]> downloadMaterialFile(
            @PathVariable String competitionUuid,
            @PathVariable Long registrationId,
            @PathVariable Long fileId
    ) {
        CurrentUser currentUser = requireTrustedUser();
        CompetitionRef competition = requireReadWorkspace(currentUser, competitionUuid, CompetitionCapability.REGISTRATION_READ);
        CompetitionRegistrationVO.Registration registration = requireRegistration(currentUser, registrationId);
        requireSameCompetition(registration, competition);
        registrationAppService.requireMaterialFileAccess(currentUser, registrationId, fileId);
        if (fileInternalApi == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "File service is unavailable");
        }
        FileContentDTO file = fileInternalApi.readFileContentForAuthorizedBusinessReference(
                fileId,
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                currentUser.getUsername(),
                MODULE_MATERIAL_REFERENCE,
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

    private CompetitionRef requireReadWorkspace(
            CurrentUser currentUser,
            String competitionUuid,
            CompetitionCapability capability
    ) {
        CompetitionAccessDecision decision = accessPolicy.requireAccessibleCompetition(currentUser, competitionUuid, capability);
        return decision.competition();
    }

    private CompetitionRegistrationVO.Registration requireRegistration(CurrentUser currentUser, Long registrationId) {
        return registrationAppService.getRegistration(currentUser, registrationId);
    }

    private void requireSameCompetition(
            CompetitionRegistrationVO.Registration registration,
            CompetitionRef competition
    ) {
        if (registration == null || registration.getCompetitionId() == null
                || !competition.id().equals(registration.getCompetitionId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "Registration not found");
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
}
