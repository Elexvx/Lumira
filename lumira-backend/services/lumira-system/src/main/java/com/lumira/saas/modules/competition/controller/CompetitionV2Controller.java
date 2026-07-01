package com.lumira.saas.modules.competition.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.competition.app.CompetitionManagementAppService;
import com.lumira.saas.modules.competition.dto.CompetitionDTO;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/aiadc/competitions")
public class CompetitionV2Controller {
    private static final String VIEW = "aiadc:competition:view";
    private static final String CREATE = "aiadc:competition:create";
    private static final String UPDATE = "aiadc:competition:update";
    private static final String DELETE = "aiadc:competition:delete";

    private final CompetitionManagementAppService competitionManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public CompetitionV2Controller(
            CompetitionManagementAppService competitionManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.competitionManagementAppService = competitionManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
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
        require(VIEW);
        return ApiResponse.success(
                competitionManagementAppService.listCompetitions(securityContextFacade.getCurrentUser(), keyword, category, status, locale, featured, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<CompetitionVO.Competition> competition(@PathVariable("id") Long id) {
        require(VIEW);
        return ApiResponse.success(competitionManagementAppService.getCompetition(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/{competitionUuid}/settings")
    public ApiResponse<CompetitionVO.Settings> competitionSettings(@PathVariable("competitionUuid") String competitionUuid) {
        require(VIEW);
        return ApiResponse.success(
                competitionManagementAppService.getCompetitionSettings(securityContextFacade.getCurrentUser(), competitionUuid),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/{competitionUuid}/settings/{module}")
    public ApiResponse<CompetitionVO.Settings> saveCompetitionSettingsModule(
            @PathVariable("competitionUuid") String competitionUuid,
            @PathVariable("module") String module,
            @Valid @RequestBody CompetitionDTO.SettingsModuleRequest request
    ) {
        require(UPDATE);
        return ApiResponse.success(
                competitionManagementAppService.saveSettingsModule(securityContextFacade.getCurrentUser(), competitionUuid, module, request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/{competitionUuid}/settings/publish")
    @RepeatSubmit
    public ApiResponse<CompetitionVO.ConfigSet> publishCompetitionSettings(@PathVariable("competitionUuid") String competitionUuid) {
        require(UPDATE);
        return ApiResponse.success(
                competitionManagementAppService.publishSettings(securityContextFacade.getCurrentUser(), competitionUuid),
                TraceContext.getRequestId()
        );
    }

    @PostMapping
    @RepeatSubmit
    public ApiResponse<CompetitionVO.Competition> createCompetition(@Valid @RequestBody CompetitionDTO.CompetitionUpsertRequest request) {
        require(CREATE);
        return ApiResponse.success(competitionManagementAppService.createCompetition(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PostMapping("/drafts")
    public ApiResponse<CompetitionVO.Competition> createCompetitionDraft(@RequestBody CompetitionDTO.CompetitionUpsertRequest request) {
        require(CREATE);
        return ApiResponse.success(competitionManagementAppService.createCompetitionDraft(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/drafts/{id}")
    public ApiResponse<CompetitionVO.Competition> updateCompetitionDraft(@PathVariable("id") Long id, @RequestBody CompetitionDTO.CompetitionUpsertRequest request) {
        require(CREATE);
        return ApiResponse.success(competitionManagementAppService.updateCompetitionDraft(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @PutMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<CompetitionVO.Competition> updateCompetition(@PathVariable("id") Long id, @RequestBody CompetitionDTO.CompetitionUpsertRequest request) {
        require(UPDATE);
        return ApiResponse.success(competitionManagementAppService.updateCompetition(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteCompetition(@PathVariable("id") Long id) {
        require(DELETE);
        return ApiResponse.success(competitionManagementAppService.deleteCompetition(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
