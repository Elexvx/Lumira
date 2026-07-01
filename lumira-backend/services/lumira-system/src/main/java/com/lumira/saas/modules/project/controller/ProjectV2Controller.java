package com.lumira.saas.modules.project.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.project.app.ProjectManagementAppService;
import com.lumira.saas.modules.project.dto.ProjectDTO;
import com.lumira.saas.modules.project.vo.ProjectVO;
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

    public ProjectV2Controller(
            ProjectManagementAppService projectManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.projectManagementAppService = projectManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ApiResponse<PageResponse<ProjectVO.Project>> projects(
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
        requireAny(VIEW, REGISTRATION_VIEW, REGISTRATION_CREATE);
        return ApiResponse.success(
                projectManagementAppService.listProjects(
                        securityContextFacade.getCurrentUser(),
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
        requireAny(VIEW, REGISTRATION_VIEW, REGISTRATION_CREATE);
        return ApiResponse.success(projectManagementAppService.getProject(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping
    @RepeatSubmit
    public ApiResponse<ProjectVO.Project> createProject(@Valid @RequestBody ProjectDTO.ProjectUpsertRequest request) {
        requireAny(CREATE, REGISTRATION_CREATE);
        return ApiResponse.success(projectManagementAppService.createProject(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<ProjectVO.Project> updateProject(@PathVariable("id") Long id, @Valid @RequestBody ProjectDTO.ProjectUpsertRequest request) {
        require(UPDATE);
        return ApiResponse.success(projectManagementAppService.updateProject(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteProject(@PathVariable("id") Long id) {
        require(DELETE);
        return ApiResponse.success(projectManagementAppService.deleteProject(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }

    private void requireAny(String... permissionKeys) {
        RuntimeException lastError = null;
        for (String permissionKey : permissionKeys) {
            try {
                require(permissionKey);
                return;
            } catch (RuntimeException error) {
                lastError = error;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        require(permissionKeys.length == 0 ? null : permissionKeys[0]);
    }
}
