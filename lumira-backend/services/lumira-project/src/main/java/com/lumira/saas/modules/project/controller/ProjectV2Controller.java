package com.lumira.saas.modules.project.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.saas.modules.project.app.ProjectManagementAppService;
import com.lumira.saas.modules.project.dto.ProjectDTO;
import com.lumira.saas.modules.project.vo.ProjectPageResponse;
import com.lumira.saas.modules.project.vo.ProjectVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/api/v2/aiadc/projects")
public class ProjectV2Controller {
    private static final String VIEW = "aiadc:project:view";
    private static final String CREATE = "aiadc:project:create";
    private static final String UPDATE = "aiadc:project:update";
    private static final String DELETE = "aiadc:project:delete";
    private static final String REGISTRATION_VIEW = "aiadc:registration:view";
    private static final String REGISTRATION_CREATE = "aiadc:registration:create";

    private final ProjectManagementAppService projectManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;
    private final boolean enforceTrustedUserResolution;

    public ProjectV2Controller(
            ProjectManagementAppService projectManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(projectManagementAppService, securityContextFacade, permissionGuard, null, false);
    }

    @Autowired
    public ProjectV2Controller(
            ProjectManagementAppService projectManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            TrustedCurrentUserResolver trustedCurrentUserResolver
    ) {
        this(projectManagementAppService, securityContextFacade, permissionGuard, trustedCurrentUserResolver, true);
    }

    public ProjectV2Controller(
            ProjectManagementAppService projectManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            boolean enforceTrustedUserResolution
    ) {
        this.projectManagementAppService = projectManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @GetMapping
    public ApiResponse<ProjectPageResponse<ProjectVO.Project>> projects(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "ownerName", required = false) String ownerName,
            @RequestParam(name = "rating", required = false) String rating,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "locale", required = false) String locale,
            @RequestParam(name = "featured", required = false) Boolean featured,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "12") long pageSize
    ) {
        CurrentUser currentUser = requireAny(VIEW, REGISTRATION_VIEW, REGISTRATION_CREATE);
        return ApiResponse.success(
                projectManagementAppService.listProjects(
                        currentUser,
                        keyword,
                        category,
                        ownerName,
                        rating,
                        status,
                        locale,
                        featured,
                        pageNo,
                        pageSize
                ),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectVO.Project> project(@PathVariable("id") Long id) {
        CurrentUser currentUser = requireAny(VIEW, REGISTRATION_VIEW, REGISTRATION_CREATE);
        return ApiResponse.success(projectManagementAppService.getProject(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping
    @RepeatSubmit
    public ApiResponse<ProjectVO.Project> createProject(@Valid @RequestBody ProjectDTO.ProjectUpsertRequest request) {
        CurrentUser currentUser = requireAny(CREATE, REGISTRATION_CREATE);
        return ApiResponse.success(projectManagementAppService.createProject(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<ProjectVO.Project> updateProject(@PathVariable("id") Long id, @Valid @RequestBody ProjectDTO.ProjectUpsertRequest request) {
        CurrentUser currentUser = require(UPDATE);
        return ApiResponse.success(projectManagementAppService.updateProject(currentUser, id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteProject(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(DELETE);
        return ApiResponse.success(projectManagementAppService.deleteProject(currentUser, id), TraceContext.getRequestId());
    }

    private CurrentUser require(String permissionKey) {
        CurrentUser currentUser = requireTrustedUser(securityContextFacade.getCurrentUser());
        permissionGuard.requirePermission(currentUser, permissionKey);
        return currentUser;
    }

    private CurrentUser requireAny(String... permissionKeys) {
        CurrentUser currentUser = requireTrustedUser(securityContextFacade.getCurrentUser());
        for (String permissionKey : permissionKeys) {
            if (permissionGuard.hasPermission(currentUser, permissionKey)) {
                return currentUser;
            }
        }
        permissionGuard.requirePermission(currentUser, permissionKeys.length == 0 ? null : permissionKeys[0]);
        return currentUser;
    }

    private CurrentUser requireTrustedUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        if (trustedCurrentUserResolver == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return currentUser;
        }
        CurrentUser resolvedCurrentUser = trustedCurrentUserResolver.resolve(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(resolvedCurrentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return resolvedCurrentUser;
    }
}
