package com.legendary.invention.saas.modules.ai.controller;

import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.common.annotation.RepeatSubmit;
import com.legendary.invention.saas.common.vo.PageResponse;
import com.legendary.invention.saas.infrastructure.observability.TraceContext;
import com.legendary.invention.saas.infrastructure.security.SecurityContextFacade;
import com.legendary.invention.saas.modules.ai.app.AiManagementAppService;
import com.legendary.invention.saas.modules.ai.dto.AiDTO;
import com.legendary.invention.saas.modules.ai.vo.AiVO;
import com.legendary.invention.saas.modules.iam.service.PermissionGuard;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiManagementAppService aiManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public AiController(
            AiManagementAppService aiManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.aiManagementAppService = aiManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/employees")
    public ApiResponse<PageResponse<AiVO.EmployeeVO>> employees(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require("ai:view");
        return ApiResponse.success(aiManagementAppService.listEmployees(currentUser(), pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/employees/{id}")
    public ApiResponse<AiVO.EmployeeDetailVO> employee(@PathVariable("id") Long id) {
        require("ai:view");
        return ApiResponse.success(aiManagementAppService.getEmployee(currentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/employees")
    @RepeatSubmit
    public ApiResponse<AiVO.EmployeeDetailVO> createEmployee(@Valid @RequestBody AiDTO.EmployeeUpsertRequest request) {
        require("ai:employee:create");
        return ApiResponse.success(aiManagementAppService.createEmployee(currentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/employees/{id}")
    @RepeatSubmit
    public ApiResponse<AiVO.EmployeeDetailVO> updateEmployee(@PathVariable("id") Long id, @Valid @RequestBody AiDTO.EmployeeUpsertRequest request) {
        require("ai:employee:update");
        return ApiResponse.success(aiManagementAppService.updateEmployee(currentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/employees/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteEmployee(@PathVariable("id") Long id) {
        require("ai:employee:delete");
        return ApiResponse.success(aiManagementAppService.deleteEmployee(currentUser(), id), TraceContext.getRequestId());
    }

    @PatchMapping("/employees/{id}/enabled")
    @RepeatSubmit
    public ApiResponse<Boolean> updateEmployeeEnabled(@PathVariable("id") Long id, @RequestBody MapEnabledRequest request) {
        require("ai:employee:status");
        return ApiResponse.success(aiManagementAppService.updateEmployeeEnabled(currentUser(), id, request.getEnabled()), TraceContext.getRequestId());
    }

    @GetMapping("/employees/template")
    public ApiResponse<AiVO.PromptTemplateVO> employeeTemplate() {
        require("ai:view");
        return ApiResponse.success(aiManagementAppService.defaultTemplate(), TraceContext.getRequestId());
    }

    @GetMapping("/llm-services")
    public ApiResponse<PageResponse<AiVO.LlmServiceVO>> llmServices(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require("ai:view");
        return ApiResponse.success(aiManagementAppService.listLlmServices(currentUser(), pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/llm-services/{id}")
    public ApiResponse<AiVO.LlmServiceVO> llmService(@PathVariable("id") Long id) {
        require("ai:view");
        return ApiResponse.success(aiManagementAppService.getLlmService(currentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/llm-services")
    @RepeatSubmit
    public ApiResponse<AiVO.LlmServiceVO> createLlmService(@Valid @RequestBody AiDTO.LlmServiceUpsertRequest request) {
        require("ai:llm:create");
        return ApiResponse.success(aiManagementAppService.createLlmService(currentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/llm-services/{id}")
    @RepeatSubmit
    public ApiResponse<AiVO.LlmServiceVO> updateLlmService(@PathVariable("id") Long id, @Valid @RequestBody AiDTO.LlmServiceUpsertRequest request) {
        require("ai:llm:update");
        return ApiResponse.success(aiManagementAppService.updateLlmService(currentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/llm-services/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteLlmService(@PathVariable("id") Long id) {
        require("ai:llm:delete");
        return ApiResponse.success(aiManagementAppService.deleteLlmService(currentUser(), id), TraceContext.getRequestId());
    }

    @PatchMapping("/llm-services/{id}/enabled")
    @RepeatSubmit
    public ApiResponse<Boolean> updateLlmServiceEnabled(@PathVariable("id") Long id, @RequestBody MapEnabledRequest request) {
        require("ai:llm:status");
        return ApiResponse.success(aiManagementAppService.updateLlmServiceEnabled(currentUser(), id, request.getEnabled()), TraceContext.getRequestId());
    }

    @GetMapping("/skills")
    public ApiResponse<List<AiVO.SkillVO>> skills() {
        require("ai:view");
        return ApiResponse.success(aiManagementAppService.listSkills(currentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/employees/{id}/skills")
    public ApiResponse<List<AiVO.EmployeeSkillVO>> employeeSkills(@PathVariable("id") Long id) {
        require("ai:view");
        return ApiResponse.success(aiManagementAppService.getEmployeeSkills(currentUser(), id), TraceContext.getRequestId());
    }

    @PutMapping("/employees/{id}/skills")
    @RepeatSubmit
    public ApiResponse<Boolean> updateEmployeeSkills(@PathVariable("id") Long id, @Valid @RequestBody AiDTO.EmployeeSkillsUpdateRequest request) {
        require("ai:employee:skills");
        return ApiResponse.success(aiManagementAppService.updateEmployeeSkills(currentUser(), id, request), TraceContext.getRequestId());
    }

    @GetMapping("/assistant")
    public ApiResponse<AiVO.EmployeeVO> assistant() {
        require("ai:chat:send");
        return ApiResponse.success(aiManagementAppService.getAssistantEmployee(currentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/conversations")
    public ApiResponse<PageResponse<AiVO.ConversationVO>> conversations(
            @RequestParam(name = "employeeId") Long employeeId,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require("ai:chat:send");
        return ApiResponse.success(aiManagementAppService.listConversations(currentUser(), employeeId, pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<AiVO.MessageVO>> conversationMessages(@PathVariable("id") Long id) {
        require("ai:chat:send");
        return ApiResponse.success(aiManagementAppService.listConversationMessages(currentUser(), id), TraceContext.getRequestId());
    }

    @PutMapping("/conversations/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> updateConversation(@PathVariable("id") Long id, @RequestBody AiDTO.ConversationUpdateRequest request) {
        require("ai:chat:send");
        return ApiResponse.success(aiManagementAppService.updateConversation(currentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/conversations/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteConversation(@PathVariable("id") Long id) {
        require("ai:chat:send");
        return ApiResponse.success(aiManagementAppService.deleteConversation(currentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/conversations/{id}/share")
    @RepeatSubmit
    public ApiResponse<AiVO.ConversationShareVO> createConversationShare(@PathVariable("id") Long id) {
        require("ai:chat:send");
        return ApiResponse.success(aiManagementAppService.createConversationShare(currentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/conversations/{id}/export")
    public ApiResponse<AiVO.ConversationExportVO> exportConversation(
            @PathVariable("id") Long id,
            @RequestParam(name = "format", defaultValue = "markdown") String format
    ) {
        require("ai:view");
        return ApiResponse.success(aiManagementAppService.exportConversation(currentUser(), id, format), TraceContext.getRequestId());
    }

    @GetMapping("/shares/{token}")
    public ApiResponse<AiVO.ConversationShareDetailVO> conversationShare(@PathVariable("token") String token) {
        require("ai:view");
        return ApiResponse.success(aiManagementAppService.getConversationShare(currentUser(), token), TraceContext.getRequestId());
    }

    @PostMapping("/chat")
    @RepeatSubmit
    public ApiResponse<AiVO.ChatResponseVO> chat(@Valid @RequestBody AiDTO.ChatRequest request) {
        require("ai:chat:send");
        return ApiResponse.success(aiManagementAppService.chat(currentUser(), request), TraceContext.getRequestId());
    }

    private com.legendary.invention.saas.infrastructure.security.CurrentUser currentUser() {
        return securityContextFacade.getCurrentUser();
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(currentUser(), permissionKey);
    }

    public static class MapEnabledRequest {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
