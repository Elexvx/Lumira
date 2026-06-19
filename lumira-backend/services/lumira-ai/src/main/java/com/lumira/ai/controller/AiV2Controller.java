package com.lumira.ai.controller;

import com.lumira.ai.app.AiCommandService;
import com.lumira.ai.app.AiReadQueryService;
import com.lumira.ai.dto.AiCommandModels.ChatRequest;
import com.lumira.ai.dto.AiCommandModels.KnowledgeSearchRequest;
import com.lumira.ai.dto.AiCommandModels.ToolConfirmRequest;
import com.lumira.ai.dto.AiCommandModels.ToolExecuteRequest;
import com.lumira.ai.dto.AiCommandModels.ToolProposeRequest;
import com.lumira.ai.vo.AiChatResponseVO;
import com.lumira.ai.vo.AiConversationVO;
import com.lumira.ai.vo.AiEmployeeVO;
import com.lumira.ai.vo.AiKnowledgeBaseVO;
import com.lumira.ai.vo.AiKnowledgeDocumentVO;
import com.lumira.ai.vo.AiKnowledgeReferenceVO;
import com.lumira.ai.vo.AiMessageVO;
import com.lumira.ai.vo.AiToolExecuteResultVO;
import com.lumira.ai.vo.AiToolPlanVO;
import com.lumira.ai.vo.AiToolVO;
import com.lumira.ai.vo.PageResponse;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v2/ai")
public class AiV2Controller {

    private final AiReadQueryService aiReadQueryService;
    private final AiCommandService aiCommandService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public AiV2Controller(
            AiReadQueryService aiReadQueryService,
            AiCommandService aiCommandService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.aiReadQueryService = aiReadQueryService;
        this.aiCommandService = aiCommandService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/employees")
    public ApiResponse<PageResponse<AiEmployeeVO>> employees(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = requireAny("ai:view", "ai:chat:send");
        return ApiResponse.success(aiReadQueryService.listEmployees(currentUser, pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/assistant")
    public ApiResponse<AiEmployeeVO> assistant() {
        CurrentUser currentUser = require("ai:chat:send");
        return ApiResponse.success(aiReadQueryService.getAssistantEmployee(currentUser), TraceContext.getRequestId());
    }

    @GetMapping("/conversations")
    public ApiResponse<PageResponse<AiConversationVO>> conversations(
            @RequestParam(name = "employeeId", required = false) Long employeeId,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("ai:chat:send");
        return ApiResponse.success(aiReadQueryService.listConversations(currentUser, employeeId, pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<java.util.List<AiMessageVO>> conversationMessages(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("ai:chat:send");
        return ApiResponse.success(aiReadQueryService.listConversationMessages(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/chat")
    public ApiResponse<AiChatResponseVO> chat(@Valid @RequestBody ChatRequest request) {
        CurrentUser currentUser = require("ai:chat:send");
        return ApiResponse.success(aiCommandService.chat(currentUser, request), TraceContext.getRequestId());
    }

    @GetMapping("/knowledge-bases")
    public ApiResponse<PageResponse<AiKnowledgeBaseVO>> knowledgeBases(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("ai:knowledge:view");
        return ApiResponse.success(aiReadQueryService.listKnowledgeBases(currentUser, keyword, status, scope, pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/knowledge-bases/{id}")
    public ApiResponse<AiKnowledgeBaseVO> knowledgeBase(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("ai:knowledge:view");
        return ApiResponse.success(aiReadQueryService.getKnowledgeBase(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/knowledge-bases/{id}/documents")
    public ApiResponse<PageResponse<AiKnowledgeDocumentVO>> knowledgeDocuments(
            @PathVariable("id") Long id,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("ai:knowledge:view");
        return ApiResponse.success(aiReadQueryService.listKnowledgeDocuments(currentUser, id, pageNo, pageSize), TraceContext.getRequestId());
    }

    @PostMapping(value = "/knowledge-bases/{id}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AiKnowledgeDocumentVO> uploadKnowledgeDocument(
            @PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file
    ) {
        CurrentUser currentUser = require("ai:knowledge:document:upload");
        return ApiResponse.success(aiCommandService.uploadKnowledgeDocument(currentUser, id, file), TraceContext.getRequestId());
    }

    @PostMapping("/knowledge-bases/{id}/documents/{documentId}/reindex")
    public ApiResponse<AiKnowledgeDocumentVO> reindexKnowledgeDocument(
            @PathVariable("id") Long id,
            @PathVariable("documentId") Long documentId
    ) {
        CurrentUser currentUser = require("ai:knowledge:document:index");
        return ApiResponse.success(aiCommandService.reindexKnowledgeDocument(currentUser, id, documentId), TraceContext.getRequestId());
    }

    @PostMapping("/knowledge-bases/search")
    public ApiResponse<List<AiKnowledgeReferenceVO>> searchKnowledge(@Valid @RequestBody KnowledgeSearchRequest request) {
        CurrentUser currentUser = require("ai:knowledge:query");
        return ApiResponse.success(aiCommandService.searchKnowledge(currentUser, request), TraceContext.getRequestId());
    }

    @GetMapping("/tools")
    public ApiResponse<java.util.List<AiToolVO>> tools() {
        CurrentUser currentUser = require("ai:tool:view");
        return ApiResponse.success(aiReadQueryService.listTools(currentUser), TraceContext.getRequestId());
    }

    @PostMapping("/tools/execute")
    public ApiResponse<AiToolExecuteResultVO> executeTool(@Valid @RequestBody ToolExecuteRequest request) {
        CurrentUser currentUser = require("ai:tool:execute");
        return ApiResponse.success(aiCommandService.executeTool(currentUser, request), TraceContext.getRequestId());
    }

    @PostMapping("/tools/propose")
    public ApiResponse<AiToolPlanVO> proposeTool(@RequestBody ToolProposeRequest request) {
        CurrentUser currentUser = require("ai:tool:execute");
        return ApiResponse.success(aiCommandService.proposeTool(currentUser, request), TraceContext.getRequestId());
    }

    @PostMapping("/tools/confirm")
    public ApiResponse<AiToolExecuteResultVO> confirmTool(@Valid @RequestBody ToolConfirmRequest request) {
        CurrentUser currentUser = require("ai:tool:execute");
        return ApiResponse.success(aiCommandService.confirmTool(currentUser, request), TraceContext.getRequestId());
    }

    private CurrentUser require(String permissionKey) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        permissionGuard.requirePermission(currentUser, permissionKey);
        return currentUser;
    }

    private CurrentUser requireAny(String... permissionKeys) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
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
