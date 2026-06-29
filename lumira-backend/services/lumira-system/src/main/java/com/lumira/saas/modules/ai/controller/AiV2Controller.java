package com.lumira.saas.modules.ai.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.ai.app.AiKnowledgeBaseAppService;
import com.lumira.saas.modules.ai.app.AiManagementAppService;
import com.lumira.saas.modules.ai.app.AiNativeToolRuntimeService;
import com.lumira.saas.modules.ai.app.AiToolOrchestrationService;
import com.lumira.saas.modules.ai.controller.AiController.MapEnabledRequest;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.vo.AiVO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v2/ai")
public class AiV2Controller {

    private final AiManagementAppService aiManagementAppService;
    private final AiKnowledgeBaseAppService aiKnowledgeBaseAppService;
    private final AiNativeToolRuntimeService aiNativeToolRuntimeService;
    private final AiToolOrchestrationService aiToolOrchestrationService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public AiV2Controller(
            AiManagementAppService aiManagementAppService,
            AiKnowledgeBaseAppService aiKnowledgeBaseAppService,
            AiNativeToolRuntimeService aiNativeToolRuntimeService,
            AiToolOrchestrationService aiToolOrchestrationService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.aiManagementAppService = aiManagementAppService;
        this.aiKnowledgeBaseAppService = aiKnowledgeBaseAppService;
        this.aiNativeToolRuntimeService = aiNativeToolRuntimeService;
        this.aiToolOrchestrationService = aiToolOrchestrationService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/employees")
    public ApiResponse<PageResponse<AiVO.EmployeeVO>> employees(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = requireAny("ai:view", "ai:chat:send");
        return ApiResponse.success(aiManagementAppService.listEmployees(currentUser, pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/employees/{id}")
    public ApiResponse<AiVO.EmployeeDetailVO> employee(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("ai:view");
        return ApiResponse.success(aiManagementAppService.getEmployee(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/employees")
    @RepeatSubmit
    public ApiResponse<AiVO.EmployeeDetailVO> createEmployee(@Valid @RequestBody AiDTO.EmployeeUpsertRequest request) {
        CurrentUser currentUser = require("ai:employee:create");
        return ApiResponse.success(aiManagementAppService.createEmployee(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/employees/{id}")
    @RepeatSubmit
    public ApiResponse<AiVO.EmployeeDetailVO> updateEmployee(@PathVariable("id") Long id, @Valid @RequestBody AiDTO.EmployeeUpsertRequest request) {
        CurrentUser currentUser = require("ai:employee:update");
        return ApiResponse.success(aiManagementAppService.updateEmployee(currentUser, id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/employees/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteEmployee(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("ai:employee:delete");
        return ApiResponse.success(aiManagementAppService.deleteEmployee(currentUser, id), TraceContext.getRequestId());
    }

    @PatchMapping("/employees/{id}/enabled")
    @RepeatSubmit
    public ApiResponse<Boolean> updateEmployeeEnabled(@PathVariable("id") Long id, @RequestBody MapEnabledRequest request) {
        CurrentUser currentUser = require("ai:employee:status");
        return ApiResponse.success(aiManagementAppService.updateEmployeeEnabled(currentUser, id, request.getEnabled()), TraceContext.getRequestId());
    }

    @GetMapping("/employees/{id}/capabilities")
    public ApiResponse<List<AiVO.EmployeeCapabilityVO>> employeeCapabilities(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("ai:view");
        return ApiResponse.success(aiManagementAppService.getEmployeeCapabilities(currentUser, id), TraceContext.getRequestId());
    }

    @PutMapping("/employees/{id}/capabilities")
    @RepeatSubmit
    public ApiResponse<Boolean> updateEmployeeCapabilities(@PathVariable("id") Long id, @Valid @RequestBody AiDTO.EmployeeCapabilitiesUpdateRequest request) {
        CurrentUser currentUser = require("ai:employee:skills");
        return ApiResponse.success(aiManagementAppService.updateEmployeeCapabilities(currentUser, id, request), TraceContext.getRequestId());
    }

    @GetMapping("/employees/{id}/knowledge-bases")
    public ApiResponse<List<AiVO.KnowledgeBaseVO>> employeeKnowledgeBases(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("ai:knowledge:view");
        return ApiResponse.success(aiKnowledgeBaseAppService.listEmployeeKnowledgeBases(currentUser, id), TraceContext.getRequestId());
    }

    @PutMapping("/employees/{id}/knowledge-bases")
    @RepeatSubmit
    public ApiResponse<Boolean> updateEmployeeKnowledgeBases(@PathVariable("id") Long id, @RequestBody AiDTO.EmployeeKnowledgeBasesUpdateRequest request) {
        CurrentUser currentUser = require("ai:knowledge:bind");
        return ApiResponse.success(aiKnowledgeBaseAppService.updateEmployeeKnowledgeBases(currentUser, id, request), TraceContext.getRequestId());
    }

    @GetMapping("/llm-services")
    public ApiResponse<PageResponse<AiVO.LlmServiceVO>> llmServices(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("ai:view");
        return ApiResponse.success(aiManagementAppService.listLlmServices(currentUser, pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/llm-services/{id}")
    public ApiResponse<AiVO.LlmServiceVO> llmService(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("ai:view");
        return ApiResponse.success(aiManagementAppService.getLlmService(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/llm-services")
    @RepeatSubmit
    public ApiResponse<AiVO.LlmServiceVO> createLlmService(@Valid @RequestBody AiDTO.LlmServiceUpsertRequest request) {
        CurrentUser currentUser = require("ai:llm:create");
        return ApiResponse.success(aiManagementAppService.createLlmService(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/llm-services/{id}")
    @RepeatSubmit
    public ApiResponse<AiVO.LlmServiceVO> updateLlmService(@PathVariable("id") Long id, @Valid @RequestBody AiDTO.LlmServiceUpsertRequest request) {
        CurrentUser currentUser = require("ai:llm:update");
        return ApiResponse.success(aiManagementAppService.updateLlmService(currentUser, id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/llm-services/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteLlmService(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("ai:llm:delete");
        return ApiResponse.success(aiManagementAppService.deleteLlmService(currentUser, id), TraceContext.getRequestId());
    }

    @PatchMapping("/llm-services/{id}/enabled")
    @RepeatSubmit
    public ApiResponse<Boolean> updateLlmServiceEnabled(@PathVariable("id") Long id, @RequestBody MapEnabledRequest request) {
        CurrentUser currentUser = require("ai:llm:status");
        return ApiResponse.success(aiManagementAppService.updateLlmServiceEnabled(currentUser, id, request.getEnabled()), TraceContext.getRequestId());
    }

    @PostMapping("/llm-services/test")
    @RepeatSubmit
    public ApiResponse<AiVO.LlmServiceTestResultVO> testLlmService(@RequestBody AiDTO.LlmServiceTestRequest request) {
        CurrentUser currentUser = requireAny("ai:llm:create", "ai:llm:update");
        return ApiResponse.success(aiManagementAppService.testLlmService(currentUser, request), TraceContext.getRequestId());
    }

    @GetMapping("/assistant")
    public ApiResponse<AiVO.EmployeeVO> assistant() {
        CurrentUser currentUser = require("ai:chat:send");
        return ApiResponse.success(aiManagementAppService.getAssistantEmployee(currentUser), TraceContext.getRequestId());
    }

    @GetMapping("/conversations")
    public ApiResponse<PageResponse<AiVO.ConversationVO>> conversations(
            @RequestParam(name = "employeeId", required = false) Long employeeId,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("ai:chat:send");
        return ApiResponse.success(aiManagementAppService.listConversations(currentUser, employeeId, pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<AiVO.MessageVO>> conversationMessages(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("ai:chat:send");
        return ApiResponse.success(aiManagementAppService.listConversationMessages(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/chat")
    @RepeatSubmit
    public ApiResponse<AiVO.ChatResponseVO> chat(@Valid @RequestBody AiDTO.ChatRequest request) {
        CurrentUser currentUser = require("ai:chat:send");
        return ApiResponse.success(aiManagementAppService.chat(currentUser, request), TraceContext.getRequestId());
    }

    @GetMapping("/knowledge-bases")
    public ApiResponse<PageResponse<AiVO.KnowledgeBaseVO>> knowledgeBases(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("ai:knowledge:view");
        return ApiResponse.success(aiKnowledgeBaseAppService.listKnowledgeBases(currentUser, keyword, status, scope, pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/knowledge-bases/{id}")
    public ApiResponse<AiVO.KnowledgeBaseVO> knowledgeBase(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("ai:knowledge:view");
        return ApiResponse.success(aiKnowledgeBaseAppService.getKnowledgeBase(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/knowledge-bases/{id}/documents")
    public ApiResponse<PageResponse<AiVO.KnowledgeDocumentVO>> knowledgeDocuments(
            @PathVariable("id") Long id,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("ai:knowledge:view");
        return ApiResponse.success(aiKnowledgeBaseAppService.listDocuments(currentUser, id, pageNo, pageSize), TraceContext.getRequestId());
    }

    @PostMapping(value = "/knowledge-bases/{id}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RepeatSubmit
    public ApiResponse<AiVO.KnowledgeDocumentVO> uploadKnowledgeDocument(
            @PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file
    ) {
        CurrentUser currentUser = require("ai:knowledge:document:upload");
        return ApiResponse.success(aiKnowledgeBaseAppService.uploadDocument(currentUser, id, file), TraceContext.getRequestId());
    }

    @PostMapping("/knowledge-bases/{id}/documents/{documentId}/reindex")
    @RepeatSubmit
    public ApiResponse<AiVO.KnowledgeDocumentVO> reindexKnowledgeDocument(
            @PathVariable("id") Long id,
            @PathVariable("documentId") Long documentId
    ) {
        CurrentUser currentUser = require("ai:knowledge:document:index");
        return ApiResponse.success(aiKnowledgeBaseAppService.reindexDocument(currentUser, id, documentId), TraceContext.getRequestId());
    }

    @PostMapping("/knowledge-bases/search")
    @RepeatSubmit
    public ApiResponse<List<AiVO.KnowledgeReferenceVO>> searchKnowledge(@Valid @RequestBody AiDTO.KnowledgeSearchRequest request) {
        CurrentUser currentUser = require("ai:knowledge:query");
        int limit = request.getLimit() == null ? 8 : request.getLimit();
        return ApiResponse.success(aiKnowledgeBaseAppService.retrieve(currentUser, request.getQuery(), request.getKnowledgeBaseIds(), limit), TraceContext.getRequestId());
    }

    @GetMapping("/tools")
    public ApiResponse<List<AiVO.ToolVO>> tools() {
        CurrentUser currentUser = require("ai:tool:view");
        return ApiResponse.success(aiNativeToolRuntimeService.listTools(currentUser), TraceContext.getRequestId());
    }

    @PostMapping("/tools/execute")
    @RepeatSubmit
    public ApiResponse<AiVO.ToolExecuteResultVO> executeTool(@Valid @RequestBody AiDTO.ToolExecuteRequest request) {
        CurrentUser currentUser = require("ai:tool:execute");
        if (!aiNativeToolRuntimeService.isDirectExecutable(currentUser, request.getToolCode())) {
            throw new BizException(ErrorCode.FORBIDDEN, "Direct AI tool execution is limited to read-only LOW risk tools; use propose/confirm");
        }
        request.setConfirmed(false);
        return ApiResponse.success(aiNativeToolRuntimeService.execute(currentUser, request), TraceContext.getRequestId());
    }

    @PostMapping("/tools/propose")
    @RepeatSubmit
    public ApiResponse<AiVO.ToolPlanVO> proposeTool(@RequestBody AiDTO.ToolProposeRequest request) {
        CurrentUser currentUser = require("ai:tool:execute");
        return ApiResponse.success(aiToolOrchestrationService.propose(currentUser, request), TraceContext.getRequestId());
    }

    @PostMapping("/tools/confirm")
    @RepeatSubmit
    public ApiResponse<AiVO.ToolExecuteResultVO> confirmTool(@Valid @RequestBody AiDTO.ToolConfirmRequest request) {
        CurrentUser currentUser = require("ai:tool:execute");
        return ApiResponse.success(aiToolOrchestrationService.confirm(currentUser, request), TraceContext.getRequestId());
    }

    private CurrentUser currentUser() {
        return securityContextFacade.getCurrentUser();
    }

    private CurrentUser require(String permissionKey) {
        CurrentUser currentUser = currentUser();
        permissionGuard.requirePermission(currentUser, permissionKey);
        return currentUser;
    }

    private CurrentUser requireAny(String... permissionKeys) {
        CurrentUser currentUser = currentUser();
        for (String permissionKey : permissionKeys) {
            try {
                permissionGuard.requirePermission(currentUser, permissionKey);
                return currentUser;
            } catch (BizException ignored) {
                // Try the next acceptable permission.
            }
        }
        permissionGuard.requirePermission(currentUser, permissionKeys.length == 0 ? null : permissionKeys[0]);
        return currentUser;
    }
}
