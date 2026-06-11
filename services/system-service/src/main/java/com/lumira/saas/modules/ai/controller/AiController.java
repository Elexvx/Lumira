package com.lumira.saas.modules.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.api.ApiResponse;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.common.exception.BizException;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.ai.app.AiManagementAppService;
import com.lumira.saas.modules.ai.app.AiKnowledgeBaseAppService;
import com.lumira.saas.modules.ai.app.AiNativeToolRuntimeService;
import com.lumira.saas.modules.ai.app.AiToolOrchestrationService;
import com.lumira.saas.modules.ai.app.AiToolPolicyService;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.vo.AiVO;
import com.lumira.common.security.PermissionGuard;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiManagementAppService aiManagementAppService;
    private final AiKnowledgeBaseAppService aiKnowledgeBaseAppService;
    private final AiNativeToolRuntimeService aiNativeToolRuntimeService;
    private final AiToolOrchestrationService aiToolOrchestrationService;
    private final AiToolPolicyService aiToolPolicyService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final ObjectMapper objectMapper;

    public AiController(
            AiManagementAppService aiManagementAppService,
            AiKnowledgeBaseAppService aiKnowledgeBaseAppService,
            AiNativeToolRuntimeService aiNativeToolRuntimeService,
            AiToolOrchestrationService aiToolOrchestrationService,
            AiToolPolicyService aiToolPolicyService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            ObjectMapper objectMapper
    ) {
        this.aiManagementAppService = aiManagementAppService;
        this.aiKnowledgeBaseAppService = aiKnowledgeBaseAppService;
        this.aiNativeToolRuntimeService = aiNativeToolRuntimeService;
        this.aiToolOrchestrationService = aiToolOrchestrationService;
        this.aiToolPolicyService = aiToolPolicyService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/employees")
    public ApiResponse<PageResponse<AiVO.EmployeeVO>> employees(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        requireAny("ai:view", "ai:chat:send");
        return ApiResponse.success(aiManagementAppService.listEmployees(currentUser(), pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/governance/overview")
    public ApiResponse<AiVO.GovernanceOverviewVO> governanceOverview() {
        require("ai:view");
        return ApiResponse.success(aiManagementAppService.governanceOverview(currentUser()), TraceContext.getRequestId());
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

    @PostMapping("/llm-services/test")
    @RepeatSubmit
    public ApiResponse<AiVO.LlmServiceTestResultVO> testLlmService(@RequestBody AiDTO.LlmServiceTestRequest request) {
        requireAny("ai:llm:create", "ai:llm:update");
        return ApiResponse.success(aiManagementAppService.testLlmService(currentUser(), request), TraceContext.getRequestId());
    }

    @GetMapping("/tools")
    public ApiResponse<List<AiVO.ToolVO>> tools() {
        require("ai:tool:view");
        return ApiResponse.success(aiNativeToolRuntimeService.listTools(currentUser()), TraceContext.getRequestId());
    }

    @PostMapping("/tools/execute")
    @RepeatSubmit
    public ApiResponse<AiVO.ToolExecuteResultVO> executeTool(@Valid @RequestBody AiDTO.ToolExecuteRequest request) {
        require("ai:tool:execute");
        return ApiResponse.success(aiNativeToolRuntimeService.execute(currentUser(), request), TraceContext.getRequestId());
    }

    @PostMapping("/tools/propose")
    @RepeatSubmit
    public ApiResponse<AiVO.ToolPlanVO> proposeTool(@RequestBody AiDTO.ToolProposeRequest request) {
        require("ai:tool:execute");
        return ApiResponse.success(aiToolOrchestrationService.propose(currentUser(), request), TraceContext.getRequestId());
    }

    @PostMapping("/tools/confirm")
    @RepeatSubmit
    public ApiResponse<AiVO.ToolExecuteResultVO> confirmTool(@Valid @RequestBody AiDTO.ToolConfirmRequest request) {
        require("ai:tool:execute");
        return ApiResponse.success(aiToolOrchestrationService.confirm(currentUser(), request), TraceContext.getRequestId());
    }

    @GetMapping("/tool-policies")
    public ApiResponse<PageResponse<AiVO.ToolPolicyVO>> toolPolicies(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require("ai:tool:view");
        return ApiResponse.success(aiToolPolicyService.listPolicies(currentUser(), pageNo, pageSize), TraceContext.getRequestId());
    }

    @PostMapping("/tool-policies")
    @RepeatSubmit
    public ApiResponse<AiVO.ToolPolicyVO> createToolPolicy(@Valid @RequestBody AiDTO.ToolPolicyUpsertRequest request) {
        require("ai:tool:execute");
        return ApiResponse.success(aiToolPolicyService.createPolicy(currentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/tool-policies/{id}")
    @RepeatSubmit
    public ApiResponse<AiVO.ToolPolicyVO> updateToolPolicy(@PathVariable("id") Long id, @Valid @RequestBody AiDTO.ToolPolicyUpsertRequest request) {
        require("ai:tool:execute");
        return ApiResponse.success(aiToolPolicyService.updatePolicy(currentUser(), id, request), TraceContext.getRequestId());
    }

    @PatchMapping("/tool-policies/{id}/enabled")
    @RepeatSubmit
    public ApiResponse<Boolean> updateToolPolicyEnabled(@PathVariable("id") Long id, @RequestBody MapEnabledRequest request) {
        require("ai:tool:execute");
        return ApiResponse.success(aiToolPolicyService.updatePolicyEnabled(currentUser(), id, request.getEnabled()), TraceContext.getRequestId());
    }

    @DeleteMapping("/tool-policies/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteToolPolicy(@PathVariable("id") Long id) {
        require("ai:tool:execute");
        return ApiResponse.success(aiToolPolicyService.deletePolicy(currentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/knowledge-bases")
    public ApiResponse<PageResponse<AiVO.KnowledgeBaseVO>> knowledgeBases(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require("ai:knowledge:view");
        return ApiResponse.success(aiKnowledgeBaseAppService.listKnowledgeBases(currentUser(), keyword, status, scope, pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/knowledge-bases/{id}")
    public ApiResponse<AiVO.KnowledgeBaseVO> knowledgeBase(@PathVariable("id") Long id) {
        require("ai:knowledge:view");
        return ApiResponse.success(aiKnowledgeBaseAppService.getKnowledgeBase(currentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/knowledge-bases")
    @RepeatSubmit
    public ApiResponse<AiVO.KnowledgeBaseVO> createKnowledgeBase(@Valid @RequestBody AiDTO.KnowledgeBaseUpsertRequest request) {
        require("ai:knowledge:create");
        return ApiResponse.success(aiKnowledgeBaseAppService.createKnowledgeBase(currentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/knowledge-bases/{id}")
    @RepeatSubmit
    public ApiResponse<AiVO.KnowledgeBaseVO> updateKnowledgeBase(@PathVariable("id") Long id, @Valid @RequestBody AiDTO.KnowledgeBaseUpsertRequest request) {
        require("ai:knowledge:update");
        return ApiResponse.success(aiKnowledgeBaseAppService.updateKnowledgeBase(currentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/knowledge-bases/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteKnowledgeBase(@PathVariable("id") Long id) {
        require("ai:knowledge:delete");
        return ApiResponse.success(aiKnowledgeBaseAppService.deleteKnowledgeBase(currentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/knowledge-bases/{id}/documents")
    public ApiResponse<PageResponse<AiVO.KnowledgeDocumentVO>> knowledgeDocuments(
            @PathVariable("id") Long id,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require("ai:knowledge:view");
        return ApiResponse.success(aiKnowledgeBaseAppService.listDocuments(currentUser(), id, pageNo, pageSize), TraceContext.getRequestId());
    }

    @PostMapping(value = "/knowledge-bases/{id}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RepeatSubmit
    public ApiResponse<AiVO.KnowledgeDocumentVO> uploadKnowledgeDocument(
            @PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file
    ) {
        require("ai:knowledge:document:upload");
        return ApiResponse.success(aiKnowledgeBaseAppService.uploadDocument(currentUser(), id, file), TraceContext.getRequestId());
    }

    @PostMapping("/knowledge-bases/{id}/documents/{documentId}/reindex")
    @RepeatSubmit
    public ApiResponse<AiVO.KnowledgeDocumentVO> reindexKnowledgeDocument(@PathVariable("id") Long id, @PathVariable("documentId") Long documentId) {
        require("ai:knowledge:document:index");
        return ApiResponse.success(aiKnowledgeBaseAppService.reindexDocument(currentUser(), id, documentId), TraceContext.getRequestId());
    }

    @DeleteMapping("/knowledge-bases/{id}/documents/{documentId}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteKnowledgeDocument(@PathVariable("id") Long id, @PathVariable("documentId") Long documentId) {
        require("ai:knowledge:document:delete");
        return ApiResponse.success(aiKnowledgeBaseAppService.deleteDocument(currentUser(), id, documentId), TraceContext.getRequestId());
    }

    @PostMapping("/knowledge-bases/search")
    @RepeatSubmit
    public ApiResponse<List<AiVO.KnowledgeReferenceVO>> searchKnowledge(@Valid @RequestBody AiDTO.KnowledgeSearchRequest request) {
        require("ai:knowledge:query");
        int limit = request.getLimit() == null ? 8 : request.getLimit();
        return ApiResponse.success(aiKnowledgeBaseAppService.retrieve(currentUser(), request.getQuery(), request.getKnowledgeBaseIds(), limit), TraceContext.getRequestId());
    }

    @GetMapping("/employees/{id}/capabilities")
    public ApiResponse<List<AiVO.EmployeeCapabilityVO>> employeeCapabilities(@PathVariable("id") Long id) {
        require("ai:view");
        return ApiResponse.success(aiManagementAppService.getEmployeeCapabilities(currentUser(), id), TraceContext.getRequestId());
    }

    @PutMapping("/employees/{id}/capabilities")
    @RepeatSubmit
    public ApiResponse<Boolean> updateEmployeeCapabilities(@PathVariable("id") Long id, @Valid @RequestBody AiDTO.EmployeeCapabilitiesUpdateRequest request) {
        require("ai:employee:skills");
        return ApiResponse.success(aiManagementAppService.updateEmployeeCapabilities(currentUser(), id, request), TraceContext.getRequestId());
    }

    @GetMapping("/employees/{id}/knowledge-bases")
    public ApiResponse<List<AiVO.KnowledgeBaseVO>> employeeKnowledgeBases(@PathVariable("id") Long id) {
        require("ai:knowledge:view");
        return ApiResponse.success(aiKnowledgeBaseAppService.listEmployeeKnowledgeBases(currentUser(), id), TraceContext.getRequestId());
    }

    @PutMapping("/employees/{id}/knowledge-bases")
    @RepeatSubmit
    public ApiResponse<Boolean> updateEmployeeKnowledgeBases(@PathVariable("id") Long id, @RequestBody AiDTO.EmployeeKnowledgeBasesUpdateRequest request) {
        require("ai:knowledge:bind");
        return ApiResponse.success(aiKnowledgeBaseAppService.updateEmployeeKnowledgeBases(currentUser(), id, request), TraceContext.getRequestId());
    }

    @GetMapping("/assistant")
    public ApiResponse<AiVO.EmployeeVO> assistant() {
        require("ai:chat:send");
        return ApiResponse.success(aiManagementAppService.getAssistantEmployee(currentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/conversations")
    public ApiResponse<PageResponse<AiVO.ConversationVO>> conversations(
            @RequestParam(name = "employeeId", required = false) Long employeeId,
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

    @PostMapping(value = "/chat/stream", produces = "text/event-stream")
    @RepeatSubmit
    public SseEmitter streamChat(@Valid @RequestBody AiDTO.ChatRequest request) {
        require("ai:chat:send");
        var currentUser = currentUser();
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            try {
                AiVO.ChatResponseVO response = aiManagementAppService.streamChat(currentUser, request, event -> sendEvent(emitter, event));
                sendEvent(emitter, AiVO.ChatStreamEventVO.done(response));
                emitter.complete();
            } catch (Exception exception) {
                sendEvent(emitter, AiVO.ChatStreamEventVO.error(resolveErrorMessage(exception)));
                emitter.complete();
            }
        });
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, AiVO.ChatStreamEventVO event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.getType())
                    .data(objectMapper.writeValueAsString(event)));
        } catch (Exception ignored) {
            emitter.complete();
        }
    }

    private String resolveErrorMessage(Exception exception) {
        if (exception instanceof BizException bizException) {
            return bizException.getMessage();
        }
        return exception.getMessage() == null || exception.getMessage().isBlank() ? "AI 回复生成失败" : exception.getMessage();
    }

    private com.lumira.common.security.CurrentUser currentUser() {
        return securityContextFacade.getCurrentUser();
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(currentUser(), permissionKey);
    }

    private void requireAny(String... permissionKeys) {
        var currentUser = currentUser();
        for (String permissionKey : permissionKeys) {
            try {
                permissionGuard.requirePermission(currentUser, permissionKey);
                return;
            } catch (BizException ignored) {
                // Try the next acceptable permission.
            }
        }
        require(permissionKeys.length == 0 ? null : permissionKeys[0]);
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
