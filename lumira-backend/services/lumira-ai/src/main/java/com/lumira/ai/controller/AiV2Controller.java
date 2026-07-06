package com.lumira.ai.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
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
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v2/ai")
public class AiV2Controller {
    private static final String STATUS_ENABLED = "ENABLED";

    private final AiReadQueryService aiReadQueryService;
    private final AiCommandService aiCommandService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final SystemInternalApi systemInternalApi;

    public AiV2Controller(
            AiReadQueryService aiReadQueryService,
            AiCommandService aiCommandService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(aiReadQueryService, aiCommandService, securityContextFacade, permissionGuard, null);
    }

    @Autowired
    public AiV2Controller(
            AiReadQueryService aiReadQueryService,
            AiCommandService aiCommandService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            SystemInternalApi systemInternalApi
    ) {
        this.aiReadQueryService = aiReadQueryService;
        this.aiCommandService = aiCommandService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.systemInternalApi = systemInternalApi;
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
        CurrentUser currentUser = requireTrustedUser(securityContextFacade.getCurrentUser());
        permissionGuard.requirePermission(currentUser, permissionKey);
        return currentUser;
    }

    private CurrentUser requireAny(String... permissionKeys) {
        CurrentUser currentUser = requireTrustedUser(securityContextFacade.getCurrentUser());
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

    private CurrentUser requireTrustedUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return refreshTrustedCurrentUser(currentUser);
    }

    private CurrentUser refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser) || systemInternalApi == null) {
            return currentUser;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = currentUser.getUserUuid() == null ? null : currentUser.getUserUuid().trim();
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
        if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!StringUtils.hasText(userSnapshot.userUuid())
                || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!StringUtils.hasText(userSnapshot.status())
                || !STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        PermissionSnapshotDTO permissionSnapshot = systemInternalApi.permissionSnapshot(
                userId,
                userSnapshot.userUuid().trim()
        );
        if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permissions are unavailable");
        }
        currentUser.setUserId(userSnapshot.userId());
        currentUser.setUserUuid(userSnapshot.userUuid().trim());
        currentUser.setUsername(userSnapshot.username());
        currentUser.setPermissions(permissionSnapshot.permissions() == null ? Set.of() : Set.copyOf(permissionSnapshot.permissions()));
        currentUser.setRoleIds(permissionSnapshot.roleIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.roleIds()));
        currentUser.setPrimaryDeptId(permissionSnapshot.primaryDeptId());
        currentUser.setDeptIds(permissionSnapshot.deptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.deptIds()));
        currentUser.setDescendantDeptIds(
                permissionSnapshot.descendantDeptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.descendantDeptIds())
        );
        currentUser.setDataScopes(permissionSnapshot.dataScopes() == null ? List.of() : List.copyOf(permissionSnapshot.dataScopes()));
        currentUser.setPermissionsVersion(permissionSnapshot.version().trim());
        currentUser.setDefaultHomePath(permissionSnapshot.defaultHomePath());
        return currentUser;
    }
}
