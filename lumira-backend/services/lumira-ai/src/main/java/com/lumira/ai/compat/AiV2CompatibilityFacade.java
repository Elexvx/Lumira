package com.lumira.ai.compat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.ai.dto.AiCommandModels.ChatAttachmentItem;
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
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.ai.app.AiKnowledgeBaseAppService;
import com.lumira.saas.modules.ai.app.AiManagementAppService;
import com.lumira.saas.modules.ai.app.AiNativeToolRuntimeService;
import com.lumira.saas.modules.ai.app.AiToolOrchestrationService;
import com.lumira.saas.modules.ai.dto.AiDTO;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Compatibility facade for the public V2 AI contract.
 *
 * <p>The request and response classes in {@code com.lumira.ai.dto} and
 * {@code com.lumira.ai.vo} are deliberately retained so that existing clients
 * keep their JSON shape.  All business behavior is delegated to the canonical
 * AI bounded-context application services; this class owns no persistence or
 * orchestration behavior of its own.</p>
 */
@Component
public class AiV2CompatibilityFacade {

    private final AiManagementAppService managementAppService;
    private final AiKnowledgeBaseAppService knowledgeBaseAppService;
    private final AiNativeToolRuntimeService nativeToolRuntimeService;
    private final AiToolOrchestrationService toolOrchestrationService;
    private final ObjectMapper compatibilityMapper;

    public AiV2CompatibilityFacade(
            AiManagementAppService managementAppService,
            AiKnowledgeBaseAppService knowledgeBaseAppService,
            AiNativeToolRuntimeService nativeToolRuntimeService,
            AiToolOrchestrationService toolOrchestrationService,
            ObjectMapper objectMapper
    ) {
        this.managementAppService = managementAppService;
        this.knowledgeBaseAppService = knowledgeBaseAppService;
        this.nativeToolRuntimeService = nativeToolRuntimeService;
        this.toolOrchestrationService = toolOrchestrationService;
        this.compatibilityMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public PageResponse<AiEmployeeVO> listEmployees(CurrentUser currentUser, long pageNo, long pageSize) {
        return page(managementAppService.listEmployees(currentUser, pageNo, pageSize), AiEmployeeVO.class);
    }

    public AiEmployeeVO getAssistantEmployee(CurrentUser currentUser) {
        return compatibility(managementAppService.getAssistantEmployee(currentUser), AiEmployeeVO.class);
    }

    public PageResponse<AiConversationVO> listConversations(
            CurrentUser currentUser, Long employeeId, long pageNo, long pageSize
    ) {
        return page(managementAppService.listConversations(currentUser, employeeId, pageNo, pageSize), AiConversationVO.class);
    }

    public List<AiMessageVO> listConversationMessages(CurrentUser currentUser, Long conversationId) {
        return managementAppService.listConversationMessages(currentUser, conversationId).stream()
                .map(value -> compatibility(value, AiMessageVO.class))
                .toList();
    }

    public AiChatResponseVO chat(CurrentUser currentUser, ChatRequest request) {
        return compatibility(managementAppService.chat(currentUser, canonical(request)), AiChatResponseVO.class);
    }

    public PageResponse<AiKnowledgeBaseVO> listKnowledgeBases(
            CurrentUser currentUser,
            String keyword,
            String status,
            String scope,
            long pageNo,
            long pageSize
    ) {
        return page(
                knowledgeBaseAppService.listKnowledgeBases(currentUser, keyword, status, scope, pageNo, pageSize),
                AiKnowledgeBaseVO.class
        );
    }

    public AiKnowledgeBaseVO getKnowledgeBase(CurrentUser currentUser, Long id) {
        return compatibility(knowledgeBaseAppService.getKnowledgeBase(currentUser, id), AiKnowledgeBaseVO.class);
    }

    public PageResponse<AiKnowledgeDocumentVO> listKnowledgeDocuments(
            CurrentUser currentUser, Long knowledgeBaseId, long pageNo, long pageSize
    ) {
        return page(
                knowledgeBaseAppService.listDocuments(currentUser, knowledgeBaseId, pageNo, pageSize),
                AiKnowledgeDocumentVO.class
        );
    }

    public AiKnowledgeDocumentVO uploadKnowledgeDocument(CurrentUser currentUser, Long knowledgeBaseId,
                                                            org.springframework.web.multipart.MultipartFile file) {
        return compatibility(knowledgeBaseAppService.uploadDocument(currentUser, knowledgeBaseId, file),
                AiKnowledgeDocumentVO.class);
    }

    public AiKnowledgeDocumentVO reindexKnowledgeDocument(CurrentUser currentUser, Long knowledgeBaseId, Long documentId) {
        return compatibility(knowledgeBaseAppService.reindexDocument(currentUser, knowledgeBaseId, documentId),
                AiKnowledgeDocumentVO.class);
    }

    public List<AiKnowledgeReferenceVO> searchKnowledge(CurrentUser currentUser, KnowledgeSearchRequest request) {
        int limit = request.limit() == null ? 5 : request.limit();
        return knowledgeBaseAppService.retrieve(currentUser, request.query(), request.knowledgeBaseIds(), limit).stream()
                .map(value -> compatibility(value, AiKnowledgeReferenceVO.class))
                .toList();
    }

    public List<AiToolVO> listTools(CurrentUser currentUser) {
        return nativeToolRuntimeService.listTools(currentUser).stream()
                .map(value -> compatibility(value, AiToolVO.class))
                .toList();
    }

    public AiToolExecuteResultVO executeTool(CurrentUser currentUser, ToolExecuteRequest request) {
        return compatibility(nativeToolRuntimeService.execute(currentUser, canonical(request)), AiToolExecuteResultVO.class);
    }

    public AiToolPlanVO proposeTool(CurrentUser currentUser, ToolProposeRequest request) {
        return compatibility(toolOrchestrationService.propose(currentUser, canonical(request)), AiToolPlanVO.class);
    }

    public AiToolExecuteResultVO confirmTool(CurrentUser currentUser, ToolConfirmRequest request) {
        return compatibility(toolOrchestrationService.confirm(currentUser, canonical(request)), AiToolExecuteResultVO.class);
    }

    private AiDTO.ChatRequest canonical(ChatRequest source) {
        AiDTO.ChatRequest target = new AiDTO.ChatRequest();
        target.setEmployeeId(source.employeeId());
        target.setEmployeeIds(source.employeeIds());
        target.setConversationId(source.conversationId());
        target.setPendingToolCallId(source.pendingToolCallId());
        target.setMessage(source.message());
        target.setEnableThinking(source.enableThinking());
        target.setAttachments(attachments(source.attachments()));
        target.setSkillCodes(source.skillCodes());
        target.setKnowledgeBaseIds(source.knowledgeBaseIds());
        target.setKnowledgeReferences(source.knowledgeReferences() == null ? null : source.knowledgeReferences().stream()
                .map(value -> compatibility(value, com.lumira.saas.modules.ai.vo.AiVO.KnowledgeReferenceVO.class))
                .toList());
        target.setConfirmed(source.confirmed());
        return target;
    }

    private AiDTO.ToolExecuteRequest canonical(ToolExecuteRequest source) {
        AiDTO.ToolExecuteRequest target = new AiDTO.ToolExecuteRequest();
        target.setEmployeeId(source.employeeId());
        target.setConversationId(source.conversationId());
        target.setToolCode(source.toolCode());
        target.setArguments(source.arguments());
        target.setConfirmed(source.confirmed());
        return target;
    }

    private AiDTO.ToolProposeRequest canonical(ToolProposeRequest source) {
        AiDTO.ToolProposeRequest target = new AiDTO.ToolProposeRequest();
        target.setEmployeeId(source.employeeId());
        target.setConversationId(source.conversationId());
        target.setMessage(source.message());
        target.setToolCode(source.toolCode());
        target.setArguments(source.arguments());
        target.setAttachments(attachments(source.attachments()));
        return target;
    }

    private AiDTO.ToolConfirmRequest canonical(ToolConfirmRequest source) {
        AiDTO.ToolConfirmRequest target = new AiDTO.ToolConfirmRequest();
        target.setPendingToolCallId(source.pendingToolCallId());
        return target;
    }

    private List<AiDTO.ChatAttachmentItem> attachments(List<ChatAttachmentItem> source) {
        if (source == null) {
            return null;
        }
        return source.stream().map(item -> {
            AiDTO.ChatAttachmentItem target = new AiDTO.ChatAttachmentItem();
            target.setFileId(item.fileId());
            return target;
        }).toList();
    }

    private <T> PageResponse<T> page(com.lumira.common.vo.PageResponse<?> source, Class<T> targetType) {
        PageResponse<T> target = new PageResponse<>();
        target.setPageNo(source.getPageNo());
        target.setPageSize(source.getPageSize());
        target.setTotal(source.getTotal());
        target.setHasMore(source.getHasMore());
        target.setRecords(source.getRecords() == null ? List.of() : source.getRecords().stream()
                .map(value -> compatibility(value, targetType))
                .toList());
        return target;
    }

    private <T> T compatibility(Object source, Class<T> targetType) {
        return source == null ? null : compatibilityMapper.convertValue(source, targetType);
    }
}
