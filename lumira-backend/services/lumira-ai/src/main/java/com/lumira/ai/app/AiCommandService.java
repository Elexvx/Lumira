package com.lumira.ai.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.ai.dto.AiCommandModels.ChatRequest;
import com.lumira.ai.dto.AiCommandModels.KnowledgeSearchRequest;
import com.lumira.ai.dto.AiCommandModels.ToolConfirmRequest;
import com.lumira.ai.dto.AiCommandModels.ToolExecuteRequest;
import com.lumira.ai.dto.AiCommandModels.ToolProposeRequest;
import com.lumira.ai.integration.AiOwnerToolGateway;
import com.lumira.ai.provider.AiProviderRuntime;
import com.lumira.ai.repository.AiConversationRepository;
import com.lumira.ai.repository.AiConversationRepository.ConversationIdentity;
import com.lumira.ai.repository.AiKnowledgeChunkRepository;
import com.lumira.ai.repository.AiKnowledgeDocumentRepository;
import com.lumira.ai.repository.AiMessageRepository;
import com.lumira.ai.repository.AiToolAuditLogRepository;
import com.lumira.ai.repository.AiToolCallPlanRepository;
import com.lumira.ai.vo.AiChatResponseVO;
import com.lumira.ai.vo.AiEmployeeVO;
import com.lumira.ai.vo.AiKnowledgeDocumentVO;
import com.lumira.ai.vo.AiKnowledgeReferenceVO;
import com.lumira.ai.vo.AiToolExecuteResultVO;
import com.lumira.ai.vo.AiToolPlanVO;
import com.lumira.ai.vo.AiToolVO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AiCommandService {

    private static final int CHUNK_SIZE = 1400;
    private static final int CHUNK_OVERLAP = 180;
    private static final int MAX_SEARCH_LIMIT = 20;

    private final AiKnowledgeDocumentRepository knowledgeDocumentRepository;
    private final AiKnowledgeChunkRepository knowledgeChunkRepository;
    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final AiToolCallPlanRepository toolCallPlanRepository;
    private final AiToolAuditLogRepository toolAuditLogRepository;
    private final AiReadQueryService readQueryService;
    private final AiOwnerToolGateway ownerToolGateway;
    private final AiProviderRuntime providerRuntime;
    private final ObjectMapper objectMapper;
    private final PermissionGuard permissionGuard;

    public AiCommandService(
            AiKnowledgeDocumentRepository knowledgeDocumentRepository,
            AiKnowledgeChunkRepository knowledgeChunkRepository,
            AiConversationRepository conversationRepository,
            AiMessageRepository messageRepository,
            AiToolCallPlanRepository toolCallPlanRepository,
            AiToolAuditLogRepository toolAuditLogRepository,
            AiReadQueryService readQueryService,
            AiOwnerToolGateway ownerToolGateway,
            AiProviderRuntime providerRuntime,
            ObjectMapper objectMapper,
            PermissionGuard permissionGuard
    ) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.toolCallPlanRepository = toolCallPlanRepository;
        this.toolAuditLogRepository = toolAuditLogRepository;
        this.readQueryService = readQueryService;
        this.ownerToolGateway = ownerToolGateway;
        this.providerRuntime = providerRuntime;
        this.objectMapper = objectMapper;
        this.permissionGuard = permissionGuard;
    }

    @Transactional
    public AiKnowledgeDocumentVO uploadKnowledgeDocument(CurrentUser currentUser, Long knowledgeBaseId, MultipartFile file) {
        readQueryService.getKnowledgeBase(currentUser, knowledgeBaseId);
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Knowledge document file is required");
        }
        String originalFilename = file.getOriginalFilename() == null ? "knowledge.txt" : file.getOriginalFilename();
        String extractedText = extractText(file);
        LocalDateTime now = LocalDateTime.now();
        Long documentId = knowledgeDocumentRepository.createDocument(
                knowledgeBaseId,
                stripExtension(originalFilename),
                originalFilename,
                extension(originalFilename),
                file.getContentType(),
                file.getSize(),
                extractedText,
                currentUserId(currentUser),
                now
        );
        int chunkCount = rebuildChunks(knowledgeBaseId, documentId, extractedText, now);
        knowledgeDocumentRepository.updateChunkCount(documentId, chunkCount, now);
        return readQueryService.listKnowledgeDocuments(currentUser, knowledgeBaseId, 1, 100).getRecords().stream()
                .filter(document -> document.id().equals(documentId))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Knowledge document not found"));
    }

    @Transactional
    public AiKnowledgeDocumentVO reindexKnowledgeDocument(CurrentUser currentUser, Long knowledgeBaseId, Long documentId) {
        readQueryService.getKnowledgeBase(currentUser, knowledgeBaseId);
        String text = knowledgeDocumentRepository.findExtractedText(knowledgeBaseId, documentId);
        LocalDateTime now = LocalDateTime.now();
        int chunkCount = rebuildChunks(knowledgeBaseId, documentId, text, now);
        knowledgeDocumentRepository.markIndexed(documentId, text.length(), chunkCount, now);
        return readQueryService.listKnowledgeDocuments(currentUser, knowledgeBaseId, 1, 100).getRecords().stream()
                .filter(item -> item.id().equals(documentId))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Knowledge document not found"));
    }

    public List<AiKnowledgeReferenceVO> searchKnowledge(CurrentUser currentUser, KnowledgeSearchRequest request) {
        if (request == null || !StringUtils.hasText(request.query())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Search query is required");
        }
        int limit = Math.min(Math.max(request.limit() == null ? 8 : request.limit(), 1), MAX_SEARCH_LIMIT);
        String like = "%" + request.query().trim() + "%";
        return knowledgeChunkRepository.search(like, request.knowledgeBaseIds(), limit);
    }

    @Transactional
    public AiChatResponseVO chat(CurrentUser currentUser, ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Message is required");
        }
        Long employeeId = request.employeeId() == null ? assistantId(currentUser) : request.employeeId();
        ConversationIdentity conversation = request.conversationId() == null
                ? createConversation(currentUserId(currentUser), employeeId, request.message())
                : existingConversation(currentUserId(currentUser), request.conversationId());
        LocalDateTime now = LocalDateTime.now();
        insertMessage(conversation.id(), "USER", request.message(), now);
        List<AiKnowledgeReferenceVO> references = searchKnowledge(currentUser, new KnowledgeSearchRequest(request.message(), request.knowledgeBaseIds(), 5));
        AiProviderRuntime.ChatCompletion completion = providerRuntime.complete(new AiProviderRuntime.ChatPrompt(request.message(), references));
        String replyText = completion.text();
        insertMessage(conversation.id(), "ASSISTANT", replyText, now);
        conversationRepository.updateLatestMessageAt(conversation.id(), now, now);
        return new AiChatResponseVO(
                conversation.id(),
                conversation.code(),
                employeeId,
                replyText,
                null,
                "ASSISTANT",
                completion.provider(),
                completion.model(),
                references,
                null,
                null,
                now
        );
    }

    @Transactional
    public AiToolPlanVO proposeTool(CurrentUser currentUser, ToolProposeRequest request) {
        if (request == null || !StringUtils.hasText(request.toolCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Tool code is required");
        }
        AiToolVO tool = findTool(request.toolCode());
        requireToolPermission(currentUser, tool);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(10);
        Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
        String summary = StringUtils.hasText(request.message()) ? request.message() : "Execute tool " + tool.toolName();
        String supervisorMessage = Boolean.TRUE.equals(tool.needConfirm()) ? "Confirmation required" : "Low risk tool can execute directly";
        Long planId = toolCallPlanRepository.createPlan(
                request.conversationId(),
                request.employeeId(),
                currentUserId(currentUser),
                tool.toolCode(),
                tool.toolName(),
                tool.riskLevel(),
                summary,
                tool.requiredPermission(),
                Boolean.TRUE.equals(tool.needConfirm()),
                supervisorMessage,
                toJson(arguments),
                expiresAt,
                now
        );
        return new AiToolPlanVO(
                planId,
                request.conversationId(),
                request.employeeId(),
                tool.toolCode(),
                tool.toolName(),
                "EXECUTE",
                tool.riskLevel(),
                summary,
                tool.requiredPermission(),
                tool.needConfirm(),
                "REQUIRE_CONFIRM",
                supervisorMessage,
                "ALLOW",
                null,
                "PENDING",
                arguments,
                expiresAt,
                now
        );
    }

    @Transactional
    public AiToolExecuteResultVO confirmTool(CurrentUser currentUser, ToolConfirmRequest request) {
        if (request == null || request.pendingToolCallId() == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Pending tool call is required");
        }
        Map<String, Object> plan = toolCallPlanRepository.findPendingPlan(currentUserId(currentUser), request.pendingToolCallId());
        ToolExecuteRequest executeRequest = new ToolExecuteRequest(
                objectLong(plan, "employee_id"),
                objectLong(plan, "conversation_id"),
                String.valueOf(plan.get("tool_code")),
                fromJsonMap(String.valueOf(plan.getOrDefault("arguments_json", "{}"))),
                true
        );
        AiToolExecuteResultVO result = executeTool(currentUser, executeRequest);
        toolCallPlanRepository.confirmPlan(request.pendingToolCallId(), currentUserId(currentUser), LocalDateTime.now());
        return result;
    }

    @Transactional
    public AiToolExecuteResultVO executeTool(CurrentUser currentUser, ToolExecuteRequest request) {
        if (request == null || !StringUtils.hasText(request.toolCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Tool code is required");
        }
        AiToolVO tool = findTool(request.toolCode());
        requireToolPermission(currentUser, tool);
        if (Boolean.TRUE.equals(tool.needConfirm()) && !Boolean.TRUE.equals(request.confirmed())) {
            throw new BizException(ErrorCode.FORBIDDEN, "Tool confirmation is required");
        }
        AiOwnerToolGateway.ToolExecution execution = ownerToolGateway.execute(
                currentUser,
                tool,
                request.arguments() == null ? Map.of() : request.arguments()
        );
        Map<String, Object> data = new LinkedHashMap<>(execution.data());
        data.put("remoteOwnerCall", execution.remote());
        data.put("degraded", execution.degraded());
        AiToolExecuteResultVO result = new AiToolExecuteResultVO(tool.toolCode(), "SUCCESS", "Tool executed successfully", data, LocalDateTime.now());
        recordToolAudit(currentUser, request, tool, result);
        return result;
    }

    private void recordToolAudit(CurrentUser currentUser, ToolExecuteRequest request, AiToolVO tool, AiToolExecuteResultVO result) {
        boolean confirmed = Boolean.TRUE.equals(request.confirmed());
        toolAuditLogRepository.addAuditLog(
                request.conversationId(),
                request.employeeId(),
                tool.toolCode(),
                tool.toolName(),
                Boolean.TRUE.equals(tool.needConfirm()),
                confirmed,
                confirmed ? currentUserId(currentUser) : null,
                confirmed ? LocalDateTime.now() : null,
                result.resultStatus(),
                result.message(),
                toJson(request.arguments() == null ? Map.of() : request.arguments()),
                toJson(result.data()),
                result.executedAt()
        );
    }

    private int rebuildChunks(Long knowledgeBaseId, Long documentId, String text, LocalDateTime now) {
        knowledgeChunkRepository.softDeleteByDocument(documentId, now);
        List<String> chunks = splitChunks(text == null ? "" : text);
        int index = 0;
        for (String chunk : chunks) {
            AiProviderRuntime.EmbeddingVector embedding = providerRuntime.embed(chunk);
            knowledgeChunkRepository.addChunk(
                    knowledgeBaseId,
                    documentId,
                    index++,
                    chunk,
                    chunk.toLowerCase(Locale.ROOT),
                    Math.max(1, chunk.length() / 4),
                    embedding.model(),
                    embedding.dimension(),
                    toJson(embedding.values()),
                    now
            );
        }
        return chunks.size();
    }

    private List<String> splitChunks(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of("");
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + CHUNK_SIZE);
            chunks.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
        return chunks;
    }

    private String extractText(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Failed to read knowledge document");
        }
    }

    private Long assistantId(CurrentUser currentUser) {
        AiEmployeeVO assistant = readQueryService.getAssistantEmployee(currentUser);
        if (assistant == null || assistant.getId() == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "AI assistant not found");
        }
        return assistant.getId();
    }

    private ConversationIdentity createConversation(Long ownerUserId, Long employeeId, String message) {
        String code = "conv_" + UUID.randomUUID().toString().replace("-", "");
        String title = message.trim().length() > 60 ? message.trim().substring(0, 60) : message.trim();
        LocalDateTime now = LocalDateTime.now();
        return conversationRepository.createConversation(ownerUserId, employeeId, code, title, now);
    }

    private ConversationIdentity existingConversation(Long ownerUserId, Long conversationId) {
        return conversationRepository.findActiveConversation(ownerUserId, conversationId);
    }

    private void insertMessage(Long conversationId, String role, String content, LocalDateTime now) {
        messageRepository.addMessage(conversationId, role, content, now);
    }

    private AiToolVO findTool(String toolCode) {
        return readQueryService.listTools(null).stream()
                .filter(tool -> tool.toolCode().equals(toolCode))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "AI 闁诲氦顫夐幃鍫曞磿闁秴鐭楅柟绋挎捣閳绘梻鈧箍鍎遍幊鎰板箺閻樼粯鐓? " + toolCode));
    }

    private void requireToolPermission(CurrentUser currentUser, AiToolVO tool) {
        if (tool != null && StringUtils.hasText(tool.requiredPermission())) {
            permissionGuard.requirePermission(currentUser, tool.requiredPermission());
        }
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? null : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? filename : filename.substring(0, index);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private Map<String, Object> fromJsonMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private Long currentUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getUserId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        return currentUser.getUserId();
    }


    private Long objectLong(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.parseLong(String.valueOf(value));
    }

}
