package com.lumira.saas.modules.competition.controller;

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
import com.lumira.saas.modules.competition.app.CompetitionManagementAppService;
import com.lumira.saas.modules.competition.dto.CompetitionDTO;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

@RestController
@RequestMapping("/api/v2/aiadc/competitions")
public class CompetitionV2Controller {
    private static final String VIEW = "aiadc:competition:view";
    private static final String REGISTRATION_VIEW = "aiadc:registration:view";
    private static final String REGISTRATION_CREATE = "aiadc:registration:create";
    private static final String EXPERT_VIEW = "expert:view";
    private static final String CREATE = "aiadc:competition:create";
    private static final String UPDATE = "aiadc:competition:update";
    private static final String DELETE = "aiadc:competition:delete";

    private final CompetitionManagementAppService competitionManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;
    private final boolean enforceTrustedUserResolution;

    public CompetitionV2Controller(
            CompetitionManagementAppService competitionManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(competitionManagementAppService, securityContextFacade, permissionGuard, null, false);
    }

    @Autowired
    public CompetitionV2Controller(
            CompetitionManagementAppService competitionManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            TrustedCurrentUserResolver trustedCurrentUserResolver
    ) {
        this(competitionManagementAppService, securityContextFacade, permissionGuard, trustedCurrentUserResolver, true);
    }

    private CompetitionV2Controller(
            CompetitionManagementAppService competitionManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            boolean enforceTrustedUserResolution
    ) {
        this.competitionManagementAppService = competitionManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @GetMapping
    public ApiResponse<PageResponse<CompetitionVO.Competition>> competitions(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "locale", required = false) String locale,
            @RequestParam(name = "featured", required = false) Boolean featured,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = requireCompetitionListAccess(status);
        return ApiResponse.success(
                competitionManagementAppService.listCompetitions(currentUser, keyword, category, status, locale, featured, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<CompetitionVO.Competition> competition(@PathVariable("id") Long id) {
        CurrentUser currentUser = requireTrustedUser(securityContextFacade.getCurrentUser());
        return ApiResponse.success(competitionManagementAppService.getCompetition(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/{competitionUuid}/settings")
    public ApiResponse<CompetitionVO.Settings> competitionSettings(@PathVariable("competitionUuid") String competitionUuid) {
        CurrentUser currentUser = requireTrustedUser(securityContextFacade.getCurrentUser());
        return ApiResponse.success(
                competitionManagementAppService.getCompetitionSettings(currentUser, competitionUuid),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/{competitionUuid}/settings/{module}")
    public ApiResponse<CompetitionVO.Settings> saveCompetitionSettingsModule(
            @PathVariable("competitionUuid") String competitionUuid,
            @PathVariable("module") String module,
            @Valid @RequestBody CompetitionDTO.SettingsModuleRequest request
    ) {
        CurrentUser currentUser = require(UPDATE);
        return ApiResponse.success(
                competitionManagementAppService.saveSettingsModule(currentUser, competitionUuid, module, request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/{competitionUuid}/settings/publish")
    @RepeatSubmit
    public ApiResponse<CompetitionVO.ConfigSet> publishCompetitionSettings(@PathVariable("competitionUuid") String competitionUuid) {
        CurrentUser currentUser = require(UPDATE);
        return ApiResponse.success(
                competitionManagementAppService.publishSettings(currentUser, competitionUuid),
                TraceContext.getRequestId()
        );
    }

    @PostMapping
    @RepeatSubmit
    public ApiResponse<CompetitionVO.Competition> createCompetition(
            @Validated(CompetitionDTO.CompetitionUpsertRequest.Create.class)
            @RequestBody CompetitionDTO.CompetitionUpsertRequest request
    ) {
        CurrentUser currentUser = require(CREATE);
        return ApiResponse.success(competitionManagementAppService.createCompetition(currentUser, request), TraceContext.getRequestId());
    }

    @PostMapping("/drafts")
    public ApiResponse<CompetitionVO.Competition> createCompetitionDraft(
            @Validated(CompetitionDTO.CompetitionUpsertRequest.Draft.class)
            @RequestBody CompetitionDTO.CompetitionUpsertRequest request
    ) {
        CurrentUser currentUser = require(CREATE);
        return ApiResponse.success(competitionManagementAppService.createCompetitionDraft(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/drafts/{id}")
    public ApiResponse<CompetitionVO.Competition> updateCompetitionDraft(
            @PathVariable("id") Long id,
            @Validated(CompetitionDTO.CompetitionUpsertRequest.Draft.class)
            @RequestBody CompetitionDTO.CompetitionUpsertRequest request
    ) {
        CurrentUser currentUser = require(CREATE);
        return ApiResponse.success(competitionManagementAppService.updateCompetitionDraft(currentUser, id, request), TraceContext.getRequestId());
    }

    @PutMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<CompetitionVO.Competition> updateCompetition(
            @PathVariable("id") Long id,
            @Validated(CompetitionDTO.CompetitionUpsertRequest.Update.class)
            @RequestBody CompetitionDTO.CompetitionUpsertRequest request
    ) {
        CurrentUser currentUser = require(UPDATE);
        return ApiResponse.success(competitionManagementAppService.updateCompetition(currentUser, id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteCompetition(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(DELETE);
        return ApiResponse.success(competitionManagementAppService.deleteCompetition(currentUser, id), TraceContext.getRequestId());
    }

    private CurrentUser require(String permissionKey) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        currentUser = requireTrustedUser(currentUser);
        permissionGuard.requirePermission(currentUser, permissionKey);
        return currentUser;
    }

    private CurrentUser requireCompetitionListAccess(String status) {
        CurrentUser currentUser = requireTrustedUser(securityContextFacade.getCurrentUser());
        if (permissionGuard.hasPermission(currentUser, VIEW)) {
            return currentUser;
        }
        boolean publishedOnly = "published".equalsIgnoreCase(status);
        if (publishedOnly && (
                permissionGuard.hasPermission(currentUser, REGISTRATION_VIEW)
                        || permissionGuard.hasPermission(currentUser, REGISTRATION_CREATE)
                        || permissionGuard.hasPermission(currentUser, EXPERT_VIEW)
        )) {
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
        CompetitionAuthenticationTrust.refresh(
                currentUser,
                trustedCurrentUserResolver,
                enforceTrustedUserResolution
        );
    }
}
