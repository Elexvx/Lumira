package com.legendary.invention.saas.modules.evaluation.controller;

import com.legendary.invention.saas.common.annotation.RepeatSubmit;
import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.common.vo.PageResponse;
import com.legendary.invention.common.web.TraceContext;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.infrastructure.security.SecurityContextFacade;
import com.legendary.invention.saas.modules.evaluation.app.EvaluationAppService;
import com.legendary.invention.saas.modules.evaluation.dto.EvaluationDTO;
import com.legendary.invention.saas.modules.evaluation.vo.EvaluationVO;
import com.legendary.invention.saas.modules.iam.service.PermissionGuard;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluations")
public class EvaluationController {

    private final EvaluationAppService evaluationAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public EvaluationController(EvaluationAppService evaluationAppService, SecurityContextFacade securityContextFacade, PermissionGuard permissionGuard) {
        this.evaluationAppService = evaluationAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/templates")
    public ApiResponse<PageResponse<EvaluationVO.TemplateVO>> templates(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "10") long pageSize) {
        require("evaluation:view");
        return ApiResponse.success(evaluationAppService.listTemplates(currentUser(), pageNo, pageSize), TraceContext.getRequestId());
    }

    @PostMapping("/templates")
    @RepeatSubmit
    public ApiResponse<EvaluationVO.TemplateVO> createTemplate(@Valid @RequestBody EvaluationDTO.TemplateRequest request) {
        require("evaluation:template:manage");
        return ApiResponse.success(evaluationAppService.createTemplate(currentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/templates/{id}")
    @RepeatSubmit
    public ApiResponse<EvaluationVO.TemplateVO> updateTemplate(@PathVariable Long id, @Valid @RequestBody EvaluationDTO.TemplateRequest request) {
        require("evaluation:template:manage");
        return ApiResponse.success(evaluationAppService.updateTemplate(currentUser(), id, request), TraceContext.getRequestId());
    }

    @PatchMapping("/templates/{id}/enabled")
    @RepeatSubmit
    public ApiResponse<Boolean> updateTemplateEnabled(@PathVariable Long id, @RequestBody EvaluationDTO.EnabledRequest request) {
        require("evaluation:template:manage");
        return ApiResponse.success(evaluationAppService.updateTemplateEnabled(currentUser(), id, request.isEnabled()), TraceContext.getRequestId());
    }

    @PostMapping("/instances")
    @RepeatSubmit
    public ApiResponse<EvaluationVO.InstanceVO> createInstance(@Valid @RequestBody EvaluationDTO.InstanceCreateRequest request) {
        require("evaluation:create");
        return ApiResponse.success(evaluationAppService.createInstance(currentUser(), request), TraceContext.getRequestId());
    }

    @GetMapping("/instances")
    public ApiResponse<PageResponse<EvaluationVO.InstanceVO>> instances(@RequestParam(required = false) String objectType, @RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "10") long pageSize) {
        require("evaluation:view");
        return ApiResponse.success(evaluationAppService.listInstances(currentUser(), objectType, pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/instances/{id}")
    public ApiResponse<EvaluationVO.InstanceVO> instance(@PathVariable Long id) {
        require("evaluation:view");
        return ApiResponse.success(evaluationAppService.getInstance(currentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/tasks/my-pending")
    public ApiResponse<PageResponse<EvaluationVO.ScoreTaskVO>> myPending(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "10") long pageSize) {
        require("evaluation:score");
        return ApiResponse.success(evaluationAppService.myPendingTasks(currentUser(), pageNo, pageSize), TraceContext.getRequestId());
    }

    @PostMapping("/tasks/{taskId}/submit-score")
    @RepeatSubmit
    public ApiResponse<EvaluationVO.InstanceVO> submitScore(@PathVariable Long taskId, @Valid @RequestBody EvaluationDTO.ScoreSubmitRequest request) {
        require("evaluation:score");
        return ApiResponse.success(evaluationAppService.submitScore(currentUser(), taskId, request), TraceContext.getRequestId());
    }

    @PostMapping("/instances/{id}/review")
    @RepeatSubmit
    public ApiResponse<EvaluationVO.InstanceVO> review(@PathVariable Long id, @Valid @RequestBody EvaluationDTO.ReviewRequest request) {
        require("evaluation:review");
        return ApiResponse.success(evaluationAppService.review(currentUser(), id, request), TraceContext.getRequestId());
    }

    @PostMapping("/instances/{id}/archive")
    @RepeatSubmit
    public ApiResponse<EvaluationVO.InstanceVO> archive(@PathVariable Long id, @RequestBody EvaluationDTO.ArchiveRequest request) {
        require("evaluation:archive");
        return ApiResponse.success(evaluationAppService.archive(currentUser(), id, request), TraceContext.getRequestId());
    }

    private CurrentUser currentUser() {
        return securityContextFacade.getCurrentUser();
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(currentUser(), permissionKey);
    }
}
