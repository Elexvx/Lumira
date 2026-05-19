package com.legendary.invention.saas.modules.task.controller;

import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.common.vo.PageResponse;
import com.legendary.invention.common.web.TraceContext;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.infrastructure.security.SecurityContextFacade;
import com.legendary.invention.saas.modules.iam.service.PermissionGuard;
import com.legendary.invention.saas.modules.task.app.TaskCenterAppService;
import com.legendary.invention.saas.modules.task.vo.TaskVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskCenterController {

    private final TaskCenterAppService taskCenterAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public TaskCenterController(TaskCenterAppService taskCenterAppService, SecurityContextFacade securityContextFacade, PermissionGuard permissionGuard) {
        this.taskCenterAppService = taskCenterAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/my-pending")
    public ApiResponse<PageResponse<TaskVO.TaskItemVO>> myPending(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require();
        return ApiResponse.success(taskCenterAppService.myPending(currentUser(), pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/my-handled")
    public ApiResponse<PageResponse<TaskVO.TaskItemVO>> myHandled(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require();
        return ApiResponse.success(taskCenterAppService.myHandled(currentUser(), pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/summary")
    public ApiResponse<TaskVO.TaskSummaryVO> summary() {
        require();
        return ApiResponse.success(taskCenterAppService.summary(currentUser()), TraceContext.getRequestId());
    }

    private CurrentUser currentUser() {
        return securityContextFacade.getCurrentUser();
    }

    private void require() {
        permissionGuard.requirePermission(currentUser(), "task:view");
    }
}
