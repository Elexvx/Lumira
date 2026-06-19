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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
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

    private final JdbcTemplate jdbcTemplate;
    private final AiReadQueryService readQueryService;
    private final AiOwnerToolGateway ownerToolGateway;
    private final AiProviderRuntime providerRuntime;
    private final ObjectMapper objectMapper;
    private final PermissionGuard permissionGuard;

    public AiCommandService(
            JdbcTemplate jdbcTemplate,
            AiReadQueryService readQueryService,
            AiOwnerToolGateway ownerToolGateway,
            AiProviderRuntime providerRuntime,
            ObjectMapper objectMapper,
            PermissionGuard permissionGuard
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.readQueryService = readQueryService;
        this.ownerToolGateway = ownerToolGateway;
        this.providerRuntime = providerRuntime;
        this.objectMapper = objectMapper;
        this.permissionGuard = permissionGuard;
    }

    @Transactional
    public AiKnowledgeDocumentVO uploadKnowledgeDocument(CurrentUser currentUser, Long knowledgeBaseId, MultipartFile file) {
        Long tenantId = currentTenantId(currentUser);
        readQueryService.getKnowledgeBase(currentUser, knowledgeBaseId);
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename() == null ? "knowledge.txt" : file.getOriginalFilename();
        String extractedText = extractText(file);
        LocalDateTime now = LocalDateTime.now();
        Long documentId = insertAndReturnId("""
                        insert into ai_knowledge_document (
                            tenant_id, knowledge_base_id, file_id, title, original_file_name, file_extension,
                            mime_type, file_size_bytes, status, parse_error, extracted_text, extracted_char_count,
                            chunk_count, created_by, updated_by, is_deleted, create_time, update_time
                        ) values (?, ?, null, ?, ?, ?, ?, ?, 'INDEXED', null, ?, ?, 0, ?, ?, 0, ?, ?)
                        """,
                ps -> {
                    ps.setLong(1, tenantId);
                    ps.setLong(2, knowledgeBaseId);
                    ps.setString(3, stripExtension(originalFilename));
                    ps.setString(4, originalFilename);
                    ps.setString(5, extension(originalFilename));
                    ps.setString(6, file.getContentType());
                    ps.setLong(7, file.getSize());
                    ps.setString(8, extractedText);
                    ps.setInt(9, extractedText.length());
                    ps.setLong(10, currentUserId(currentUser));
                    ps.setLong(11, currentUserId(currentUser));
                    ps.setTimestamp(12, Timestamp.valueOf(now));
                    ps.setTimestamp(13, Timestamp.valueOf(now));
                }
        );
        int chunkCount = rebuildChunks(tenantId, knowledgeBaseId, documentId, extractedText, now);
        jdbcTemplate.update(
                "update ai_knowledge_document set chunk_count = ?, update_time = ? where tenant_id = ? and id = ?",
                chunkCount,
                now,
                tenantId,
                documentId
        );
        return readQueryService.listKnowledgeDocuments(currentUser, knowledgeBaseId, 1, 100).getRecords().stream()
                .filter(document -> document.id().equals(documentId))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "知识文档不存在"));
    }

    @Transactional
    public AiKnowledgeDocumentVO reindexKnowledgeDocument(CurrentUser currentUser, Long knowledgeBaseId, Long documentId) {
        Long tenantId = currentTenantId(currentUser);
        readQueryService.getKnowledgeBase(currentUser, knowledgeBaseId);
        Map<String, Object> document = jdbcTemplate.queryForMap(
                """
                        select extracted_text
                        from ai_knowledge_document
                        where tenant_id = ? and knowledge_base_id = ? and id = ? and is_deleted = 0
                        """,
                tenantId,
                knowledgeBaseId,
                documentId
        );
        String text = String.valueOf(document.getOrDefault("extracted_text", ""));
        LocalDateTime now = LocalDateTime.now();
        int chunkCount = rebuildChunks(tenantId, knowledgeBaseId, documentId, text, now);
        jdbcTemplate.update(
                """
                        update ai_knowledge_document
                        set status = 'INDEXED', parse_error = null, extracted_char_count = ?, chunk_count = ?, update_time = ?
                        where tenant_id = ? and id = ?
                        """,
                text.length(),
                chunkCount,
                now,
                tenantId,
                documentId
        );
        return readQueryService.listKnowledgeDocuments(currentUser, knowledgeBaseId, 1, 100).getRecords().stream()
                .filter(item -> item.id().equals(documentId))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "知识文档不存在"));
    }

    public List<AiKnowledgeReferenceVO> searchKnowledge(CurrentUser currentUser, KnowledgeSearchRequest request) {
        Long tenantId = currentTenantId(currentUser);
        if (request == null || !StringUtils.hasText(request.query())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "检索内容不能为空");
        }
        int limit = Math.min(Math.max(request.limit() == null ? 8 : request.limit(), 1), MAX_SEARCH_LIMIT);
        String like = "%" + request.query().trim() + "%";
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(like);
        args.add(like);
        String idFilter = "";
        if (request.knowledgeBaseIds() != null && !request.knowledgeBaseIds().isEmpty()) {
            idFilter = " and kb.id in (" + "?,".repeat(request.knowledgeBaseIds().size()).replaceFirst(",$", "") + ")";
            args.addAll(request.knowledgeBaseIds());
        }
        args.add(limit);
        return jdbcTemplate.query(
                """
                        select c.id as chunk_id, kb.id as knowledge_base_id, kb.name as knowledge_base_name,
                               d.id as document_id, d.title as document_title, d.file_id, d.original_file_name,
                               c.chunk_index, c.content
                        from ai_knowledge_chunk c
                        join ai_knowledge_document d
                          on d.tenant_id = c.tenant_id and d.id = c.document_id and d.is_deleted = 0
                        join ai_knowledge_base kb
                          on kb.tenant_id = c.tenant_id and kb.id = c.knowledge_base_id and kb.is_deleted = 0
                        where c.tenant_id = ?
                          and c.is_deleted = 0
                          and (c.search_text like ? or c.content like ?)
                        """ + idFilter + """
                        order by c.update_time desc, c.id desc
                        limit ?
                        """,
                (rs, rowNum) -> new AiKnowledgeReferenceVO(
                        rs.getLong("chunk_id"),
                        rs.getLong("knowledge_base_id"),
                        rs.getString("knowledge_base_name"),
                        rs.getLong("document_id"),
                        rs.getString("document_title"),
                        objectLong(rs, "file_id"),
                        rs.getString("original_file_name"),
                        rs.getInt("chunk_index"),
                        rs.getString("content")
                ),
                args.toArray()
        );
    }

    @Transactional
    public AiChatResponseVO chat(CurrentUser currentUser, ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "消息不能为空");
        }
        Long tenantId = currentTenantId(currentUser);
        Long employeeId = request.employeeId() == null ? assistantId(currentUser) : request.employeeId();
        ConversationIdentity conversation = request.conversationId() == null
                ? createConversation(tenantId, currentUserId(currentUser), employeeId, request.message())
                : existingConversation(tenantId, currentUserId(currentUser), request.conversationId());
        LocalDateTime now = LocalDateTime.now();
        insertMessage(tenantId, conversation.id(), "USER", request.message(), now);
        List<AiKnowledgeReferenceVO> references = searchKnowledge(currentUser, new KnowledgeSearchRequest(request.message(), request.knowledgeBaseIds(), 5));
        AiProviderRuntime.ChatCompletion completion = providerRuntime.complete(new AiProviderRuntime.ChatPrompt(request.message(), references));
        String replyText = completion.text();
        insertMessage(tenantId, conversation.id(), "ASSISTANT", replyText, now);
        jdbcTemplate.update(
                "update ai_conversation set latest_message_at = ?, update_time = ? where tenant_id = ? and id = ?",
                now,
                now,
                tenantId,
                conversation.id()
        );
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
            throw new BizException(ErrorCode.VALIDATION_ERROR, "工具编码不能为空");
        }
        AiToolVO tool = findTool(request.toolCode());
        requireToolPermission(currentUser, tool);
        Long tenantId = currentTenantId(currentUser);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(10);
        Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
        Long planId = insertAndReturnId("""
                        insert into ai_tool_call_plan (
                            tenant_id, conversation_id, employee_id, owner_user_id, tool_code, tool_name, action_type,
                            risk_level, summary, permission_key, requires_confirm, supervisor_verdict, supervisor_message,
                            policy_verdict, policy_message, arguments_json, status, expires_at, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, 'EXECUTE', ?, ?, ?, ?, 'REQUIRE_CONFIRM', ?, 'ALLOW', null, ?, 'PENDING', ?, 0, ?, ?)
                        """,
                ps -> {
                    ps.setLong(1, tenantId);
                    setNullableLong(ps, 2, request.conversationId());
                    setNullableLong(ps, 3, request.employeeId());
                    ps.setLong(4, currentUserId(currentUser));
                    ps.setString(5, tool.toolCode());
                    ps.setString(6, tool.toolName());
                    ps.setString(7, tool.riskLevel());
                    ps.setString(8, StringUtils.hasText(request.message()) ? request.message() : "执行工具 " + tool.toolName());
                    ps.setString(9, tool.requiredPermission());
                    ps.setInt(10, Boolean.TRUE.equals(tool.needConfirm()) ? 1 : 0);
                    ps.setString(11, Boolean.TRUE.equals(tool.needConfirm()) ? "需要确认后执行" : "低风险工具可直接执行");
                    ps.setString(12, toJson(arguments));
                    ps.setTimestamp(13, Timestamp.valueOf(expiresAt));
                    ps.setTimestamp(14, Timestamp.valueOf(now));
                    ps.setTimestamp(15, Timestamp.valueOf(now));
                }
        );
        return new AiToolPlanVO(
                planId,
                tenantId,
                request.conversationId(),
                request.employeeId(),
                tool.toolCode(),
                tool.toolName(),
                "EXECUTE",
                tool.riskLevel(),
                StringUtils.hasText(request.message()) ? request.message() : "执行工具 " + tool.toolName(),
                tool.requiredPermission(),
                tool.needConfirm(),
                "REQUIRE_CONFIRM",
                Boolean.TRUE.equals(tool.needConfirm()) ? "需要确认后执行" : "低风险工具可直接执行",
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
            throw new BizException(ErrorCode.VALIDATION_ERROR, "待确认工具调用不能为空");
        }
        Long tenantId = currentTenantId(currentUser);
        Map<String, Object> plan = jdbcTemplate.queryForMap(
                """
                        select id, employee_id, conversation_id, tool_code, arguments_json
                        from ai_tool_call_plan
                        where tenant_id = ? and owner_user_id = ? and id = ? and status = 'PENDING'
                          and is_deleted = 0 and expires_at >= now()
                        """,
                tenantId,
                currentUserId(currentUser),
                request.pendingToolCallId()
        );
        ToolExecuteRequest executeRequest = new ToolExecuteRequest(
                objectLong(plan, "employee_id"),
                objectLong(plan, "conversation_id"),
                String.valueOf(plan.get("tool_code")),
                fromJsonMap(String.valueOf(plan.getOrDefault("arguments_json", "{}"))),
                true
        );
        AiToolExecuteResultVO result = executeTool(currentUser, executeRequest);
        jdbcTemplate.update(
                """
                        update ai_tool_call_plan
                        set status = 'CONFIRMED', confirmed_by = ?, confirmed_at = ?, update_time = ?
                        where tenant_id = ? and id = ?
                        """,
                currentUserId(currentUser),
                LocalDateTime.now(),
                LocalDateTime.now(),
                tenantId,
                request.pendingToolCallId()
        );
        return result;
    }

    @Transactional
    public AiToolExecuteResultVO executeTool(CurrentUser currentUser, ToolExecuteRequest request) {
        if (request == null || !StringUtils.hasText(request.toolCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "工具编码不能为空");
        }
        AiToolVO tool = findTool(request.toolCode());
        requireToolPermission(currentUser, tool);
        if (Boolean.TRUE.equals(tool.needConfirm()) && !Boolean.TRUE.equals(request.confirmed())) {
            throw new BizException(ErrorCode.FORBIDDEN, "高风险工具需要确认后执行");
        }
        AiOwnerToolGateway.ToolExecution execution = ownerToolGateway.execute(
                currentUser,
                tool,
                request.arguments() == null ? Map.of() : request.arguments()
        );
        Map<String, Object> data = new LinkedHashMap<>(execution.data());
        data.put("remoteOwnerCall", execution.remote());
        data.put("degraded", execution.degraded());
        AiToolExecuteResultVO result = new AiToolExecuteResultVO(tool.toolCode(), "SUCCESS", "工具调用成功", data, LocalDateTime.now());
        recordToolAudit(currentUser, request, tool, result);
        return result;
    }

    private void recordToolAudit(CurrentUser currentUser, ToolExecuteRequest request, AiToolVO tool, AiToolExecuteResultVO result) {
        jdbcTemplate.update(
                """
                        insert into ai_tool_audit_log (
                            tenant_id, conversation_id, employee_id, skill_code, tool_name, permission_mode,
                            confirm_required, confirm_result, confirmed_by, confirmed_at, result_status,
                            detail_message, request_payload_json, response_payload_json, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, 'allow', ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                currentTenantId(currentUser),
                request.conversationId(),
                request.employeeId(),
                tool.toolCode(),
                tool.toolName(),
                Boolean.TRUE.equals(tool.needConfirm()) ? 1 : 0,
                Boolean.TRUE.equals(request.confirmed()) ? 1 : 0,
                Boolean.TRUE.equals(request.confirmed()) ? currentUserId(currentUser) : null,
                Boolean.TRUE.equals(request.confirmed()) ? LocalDateTime.now() : null,
                result.resultStatus(),
                result.message(),
                toJson(request.arguments() == null ? Map.of() : request.arguments()),
                toJson(result.data()),
                result.executedAt(),
                result.executedAt()
        );
    }

    private int rebuildChunks(Long tenantId, Long knowledgeBaseId, Long documentId, String text, LocalDateTime now) {
        jdbcTemplate.update(
                "update ai_knowledge_chunk set is_deleted = 1, update_time = ? where tenant_id = ? and document_id = ? and is_deleted = 0",
                now,
                tenantId,
                documentId
        );
        List<String> chunks = splitChunks(text == null ? "" : text);
        int index = 0;
        for (String chunk : chunks) {
            AiProviderRuntime.EmbeddingVector embedding = providerRuntime.embed(chunk);
            jdbcTemplate.update(
                    """
                            insert into ai_knowledge_chunk (
                                tenant_id, knowledge_base_id, document_id, chunk_index, content, search_text,
                                token_count, embedding_model, embedding_dim, embedding_vector_json, vector_indexed_at,
                                is_deleted, create_time, update_time
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                            """,
                    tenantId,
                    knowledgeBaseId,
                    documentId,
                    index++,
                    chunk,
                    chunk.toLowerCase(Locale.ROOT),
                    Math.max(1, chunk.length() / 4),
                    embedding.model(),
                    embedding.dimension(),
                    toJson(embedding.values()),
                    now,
                    now,
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
            throw new BizException(ErrorCode.BIZ_ERROR, "知识文档读取失败");
        }
    }

    private Long assistantId(CurrentUser currentUser) {
        AiEmployeeVO assistant = readQueryService.getAssistantEmployee(currentUser);
        if (assistant == null || assistant.getId() == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "AI 助手不存在");
        }
        return assistant.getId();
    }

    private ConversationIdentity createConversation(Long tenantId, Long ownerUserId, Long employeeId, String message) {
        String code = "conv_" + UUID.randomUUID().toString().replace("-", "");
        String title = message.trim().length() > 60 ? message.trim().substring(0, 60) : message.trim();
        LocalDateTime now = LocalDateTime.now();
        Long id = insertAndReturnId("""
                        insert into ai_conversation (
                            tenant_id, employee_id, owner_user_id, conversation_code, title, status,
                            is_pinned, latest_message_at, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, 'ACTIVE', 0, ?, 0, ?, ?)
                        """,
                ps -> {
                    ps.setLong(1, tenantId);
                    ps.setLong(2, employeeId);
                    ps.setLong(3, ownerUserId);
                    ps.setString(4, code);
                    ps.setString(5, title);
                    ps.setTimestamp(6, Timestamp.valueOf(now));
                    ps.setTimestamp(7, Timestamp.valueOf(now));
                    ps.setTimestamp(8, Timestamp.valueOf(now));
                }
        );
        return new ConversationIdentity(id, code);
    }

    private ConversationIdentity existingConversation(Long tenantId, Long ownerUserId, Long conversationId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                        select id, conversation_code
                        from ai_conversation
                        where tenant_id = ? and owner_user_id = ? and id = ? and is_deleted = 0
                        """,
                tenantId,
                ownerUserId,
                conversationId
        );
        return new ConversationIdentity(objectLong(row, "id"), String.valueOf(row.get("conversation_code")));
    }

    private void insertMessage(Long tenantId, Long conversationId, String role, String content, LocalDateTime now) {
        jdbcTemplate.update(
                """
                        insert into ai_message (
                            tenant_id, conversation_id, role, content, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, 0, ?, ?)
                        """,
                tenantId,
                conversationId,
                role,
                content,
                now,
                now
        );
    }

    private AiToolVO findTool(String toolCode) {
        return readQueryService.listTools(null).stream()
                .filter(tool -> tool.toolCode().equals(toolCode))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "AI 工具不存在: " + toolCode));
    }

    private void requireToolPermission(CurrentUser currentUser, AiToolVO tool) {
        if (tool != null && StringUtils.hasText(tool.requiredPermission())) {
            permissionGuard.requirePermission(currentUser, tool.requiredPermission());
        }
    }

    private Long insertAndReturnId(String sql, StatementBinder binder) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            binder.bind(ps);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "新增记录失败");
        }
        return key.longValue();
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

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getCurrentTenantId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Tenant context is required");
        }
        return currentUser.getCurrentTenantId();
    }

    private Long currentUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getUserId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        return currentUser.getUserId();
    }

    private Long objectLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Long objectLong(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.parseLong(String.valueOf(value));
    }

    private void setNullableLong(PreparedStatement ps, int parameterIndex, Long value) throws java.sql.SQLException {
        if (value == null) {
            ps.setObject(parameterIndex, null);
        } else {
            ps.setLong(parameterIndex, value);
        }
    }

    private record ConversationIdentity(Long id, String code) {
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement ps) throws java.sql.SQLException;
    }
}
