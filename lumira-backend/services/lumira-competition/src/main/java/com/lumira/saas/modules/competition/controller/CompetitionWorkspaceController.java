package com.lumira.saas.modules.competition.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.web.TraceContext;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.competition.app.CompetitionAuthenticationTrust;
import com.lumira.saas.modules.competition.app.CompetitionWorkspaceAppService;
import com.lumira.saas.modules.competition.vo.CompetitionWorkspaceVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for the competition workspace shell. */
@RestController
@RequestMapping("/api/v2/aiadc")
public class CompetitionWorkspaceController {
    private final CompetitionWorkspaceAppService workspaceAppService;
    private final SecurityContextFacade securityContextFacade;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;
    private final boolean enforceTrustedUserResolution;

    public CompetitionWorkspaceController(
            CompetitionWorkspaceAppService workspaceAppService,
            SecurityContextFacade securityContextFacade
    ) {
        this(workspaceAppService, securityContextFacade, null, false);
    }

    @Autowired
    public CompetitionWorkspaceController(
            CompetitionWorkspaceAppService workspaceAppService,
            SecurityContextFacade securityContextFacade,
            TrustedCurrentUserResolver trustedCurrentUserResolver
    ) {
        this(workspaceAppService, securityContextFacade, trustedCurrentUserResolver, true);
    }

    private CompetitionWorkspaceController(
            CompetitionWorkspaceAppService workspaceAppService,
            SecurityContextFacade securityContextFacade,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            boolean enforceTrustedUserResolution
    ) {
        this.workspaceAppService = workspaceAppService;
        this.securityContextFacade = securityContextFacade;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @GetMapping("/competition-workspaces")
    public ApiResponse<PageResponse<CompetitionWorkspaceVO.Workspace>> workspaces(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "module", required = false) String module,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "20") long pageSize
    ) {
        CurrentUser currentUser = requireTrustedUser();
        return ApiResponse.success(
                workspaceAppService.listWorkspaces(currentUser, keyword, status, module, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/competitions/{competitionUuid}/workspace")
    public ApiResponse<CompetitionWorkspaceVO.Workspace> workspace(
            @PathVariable("competitionUuid") String competitionUuid
    ) {
        CurrentUser currentUser = requireTrustedUser();
        return ApiResponse.success(
                workspaceAppService.getWorkspace(currentUser, competitionUuid),
                TraceContext.getRequestId()
        );
    }

    private CurrentUser requireTrustedUser() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        CompetitionAuthenticationTrust.refresh(
                currentUser,
                trustedCurrentUserResolver,
                enforceTrustedUserResolution
        );
        if (currentUser == null || !currentUser.isAuthenticated()
                || currentUser.getUserId() == null
                || currentUser.getSessionVersion() == null
                || currentUser.getUsername() == null
                || currentUser.getUsername().isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser;
    }
}
