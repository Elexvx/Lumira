package com.lumira.saas.modules.ai.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.vo.AiVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public interface AiEmployeeRuntimeService {

    AiVO.ChatResponseVO chat(CurrentUser currentUser, AiDTO.ChatRequest request);

    AiVO.ChatResponseVO streamChat(CurrentUser currentUser, AiDTO.ChatRequest request, Consumer<AiVO.ChatStreamEventVO> onEvent);
}

@Service
@Primary
class DefaultAiEmployeeRuntimeService implements AiEmployeeRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiEmployeeRuntimeService.class);
    private static final String GENERAL_CHAT_SKILL_CODE = "chat.general";
    private static final String PERMISSION_AI_CHAT_SEND = "ai:chat:send";
    private static final String STATUS_ENABLED = "ENABLED";

    private final MyBatisQueryOperations jdbcTemplate;
    private final AiLlmServiceConfigProvider aiLlmServiceConfigProvider;
    private final AiChatModelFactory aiChatModelFactory;
    private final AiConversationService aiConversationService;
    private final AiToolRegistry aiToolRegistry;
    private final AiSkillPermissionChecker aiSkillPermissionChecker;
    private final AiKnowledgeBaseAppService aiKnowledgeBaseAppService;
    private final AiToolOrchestrationService aiToolOrchestrationService;
    private final AiAssistantEmployeeResolver aiAssistantEmployeeResolver;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    DefaultAiEmployeeRuntimeService(
            MyBatisQueryOperations jdbcTemplate,
            AiLlmServiceConfigProvider aiLlmServiceConfigProvider,
            AiChatModelFactory aiChatModelFactory,
            AiConversationService aiConversationService,
            AiToolRegistry aiToolRegistry,
            AiSkillPermissionChecker aiSkillPermissionChecker,
            AiKnowledgeBaseAppService aiKnowledgeBaseAppService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(
                jdbcTemplate,
                aiLlmServiceConfigProvider,
                aiChatModelFactory,
                aiConversationService,
                aiToolRegistry,
                aiSkillPermissionChecker,
                aiKnowledgeBaseAppService,
                null,
                permissionSnapshotService,
                null,
                null,
                false
        );
    }

    @Autowired
    DefaultAiEmployeeRuntimeService(
            MyBatisQueryOperations jdbcTemplate,
            AiLlmServiceConfigProvider aiLlmServiceConfigProvider,
            AiChatModelFactory aiChatModelFactory,
            AiConversationService aiConversationService,
            AiToolRegistry aiToolRegistry,
            AiSkillPermissionChecker aiSkillPermissionChecker,
            AiKnowledgeBaseAppService aiKnowledgeBaseAppService,
            AiToolOrchestrationService aiToolOrchestrationService,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                jdbcTemplate,
                aiLlmServiceConfigProvider,
                aiChatModelFactory,
                aiConversationService,
                aiToolRegistry,
                aiSkillPermissionChecker,
                aiKnowledgeBaseAppService,
                aiToolOrchestrationService,
                permissionSnapshotService,
                null,
                sessionAuthenticationService,
                true
        );
    }

    DefaultAiEmployeeRuntimeService(
            MyBatisQueryOperations jdbcTemplate,
            AiLlmServiceConfigProvider aiLlmServiceConfigProvider,
            AiChatModelFactory aiChatModelFactory,
            AiConversationService aiConversationService,
            AiToolRegistry aiToolRegistry,
            AiSkillPermissionChecker aiSkillPermissionChecker,
            AiKnowledgeBaseAppService aiKnowledgeBaseAppService,
            AiToolOrchestrationService aiToolOrchestrationService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                jdbcTemplate,
                aiLlmServiceConfigProvider,
                aiChatModelFactory,
                aiConversationService,
                aiToolRegistry,
                aiSkillPermissionChecker,
                aiKnowledgeBaseAppService,
                aiToolOrchestrationService,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                true
        );
    }

    private DefaultAiEmployeeRuntimeService(
            MyBatisQueryOperations jdbcTemplate,
            AiLlmServiceConfigProvider aiLlmServiceConfigProvider,
            AiChatModelFactory aiChatModelFactory,
            AiConversationService aiConversationService,
            AiToolRegistry aiToolRegistry,
            AiSkillPermissionChecker aiSkillPermissionChecker,
            AiKnowledgeBaseAppService aiKnowledgeBaseAppService,
            AiToolOrchestrationService aiToolOrchestrationService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiLlmServiceConfigProvider = aiLlmServiceConfigProvider;
        this.aiChatModelFactory = aiChatModelFactory;
        this.aiConversationService = aiConversationService;
        this.aiToolRegistry = aiToolRegistry;
        this.aiSkillPermissionChecker = aiSkillPermissionChecker;
        this.aiKnowledgeBaseAppService = aiKnowledgeBaseAppService;
        this.aiToolOrchestrationService = aiToolOrchestrationService;
        this.aiAssistantEmployeeResolver = new AiAssistantEmployeeResolver(jdbcTemplate);
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @Override
    public AiVO.ChatResponseVO chat(CurrentUser currentUser, AiDTO.ChatRequest request) {
        return executeChat(currentUser, request, null);
    }

    @Override
    public AiVO.ChatResponseVO streamChat(CurrentUser currentUser, AiDTO.ChatRequest request, Consumer<AiVO.ChatStreamEventVO> onEvent) {
        return executeChat(currentUser, request, onEvent);
    }

    private AiVO.ChatResponseVO executeChat(CurrentUser currentUser, AiDTO.ChatRequest request, Consumer<AiVO.ChatStreamEventVO> onEvent) {
        CurrentUser runtimeUser = refreshTrustedCurrentUser(currentUser);
        Long actorUserId = requireChatPermission(runtimeUser);
        List<Long> employeeIds = normalizeEmployeeIds(request);
        Long employeeId = employeeIds.size() == 1 ? employeeIds.get(0) : request == null ? null : request.getEmployeeId();
        Long conversationId = request == null ? null : request.getConversationId();
        boolean confirmed = request != null && Boolean.TRUE.equals(request.getConfirmed());
        boolean generalConversation = employeeId == null || employeeId <= 0;
        if (request != null && request.getPendingToolCallId() != null) {
            return executeConfirmedToolChat(runtimeUser, employeeId, request, onEvent);
        }
        if (employeeIds.size() > 1) {
            return executeMultiEmployeeChat(runtimeUser, employeeIds, request, onEvent, confirmed);
        }
        try {
            emit(onEvent, AiVO.ChatStreamEventVO.status(generalConversation ? "Preparing general chat" : "Preparing employee chat"));
            AiVO.EmployeeDetailVO employee;
            List<AiVO.SkillVO> skills;
            AiLlmServiceConfig config;
            if (generalConversation) {
                employee = aiAssistantEmployeeResolver.getOrCreateAssistantEmployeeDetail();
                employeeId = employee.getId();
                skills = List.of();
                config = aiLlmServiceConfigProvider.findDefault().orElse(null);
            } else {
                employee = queryEmployeeDetail(employeeId);
                employeeId = employee.getId();
                if (!Boolean.TRUE.equals(employee.getEnabled())) {
                    throw new BizException(ErrorCode.BIZ_ERROR, "AI employee is disabled");
                }
                emit(onEvent, AiVO.ChatStreamEventVO.status("Checking skill permissions"));
                if (hasRequestedSkills(request)) {
                    aiSkillPermissionChecker.verifyAllowed(employee.getId(), request.getSkillCodes(), confirmed);
                }
                config = aiLlmServiceConfigProvider.findById(employee.getDefaultLlmServiceId())
                        .or(() -> aiLlmServiceConfigProvider.findDefaultForEmployee(employee.getId()))
                        .orElse(null);
                skills = aiToolRegistry.listRegisteredSkills(employee.getId());
            }
            emit(onEvent, AiVO.ChatStreamEventVO.status("Creating conversation and saving user message"));
            conversationId = aiConversationService.ensureConversation(
                    actorUserId,
                    runtimeUser.getUserUuid(),
                    employeeId,
                    request.getConversationId(),
                    buildConversationTitle(request.getMessage())
            );
            Long userMessageId = aiConversationService.recordMessage(actorUserId, runtimeUser.getUserUuid(), conversationId, "USER", request.getMessage());
            aiConversationService.recordMessageAttachments(runtimeUser, conversationId, userMessageId, request.getAttachments());

            AiDTO.ToolProposeRequest proposeRequest = new AiDTO.ToolProposeRequest();
            proposeRequest.setEmployeeId(employeeId);
            proposeRequest.setConversationId(conversationId);
            proposeRequest.setMessage(request.getMessage());
            proposeRequest.setAttachments(request.getAttachments());
            var proposedTool = aiToolOrchestrationService == null
                    ? java.util.Optional.<AiVO.ToolPlanVO>empty()
                    : aiToolOrchestrationService.tryPropose(runtimeUser, proposeRequest);
            if (proposedTool.isPresent()) {
                AiVO.ToolPlanVO plan = proposedTool.get();
                if ("BLOCKED".equalsIgnoreCase(plan.getStatus())) {
                    emit(onEvent, AiVO.ChatStreamEventVO.toolBlocked(plan, firstText(plan.getPolicyMessage(), plan.getSupervisorMessage(), "Tool call blocked by policy")));
                } else if (!Boolean.TRUE.equals(plan.getRequiresConfirm())) {
                    aiSkillPermissionChecker.verifyAllowed(employeeId, List.of(plan.getToolCode()), false);
                    AiDTO.ToolConfirmRequest confirmRequest = new AiDTO.ToolConfirmRequest();
                    confirmRequest.setPendingToolCallId(plan.getId());
                    AiVO.ToolExecuteResultVO result = aiToolOrchestrationService.confirm(runtimeUser, confirmRequest);
                    emit(onEvent, AiVO.ChatStreamEventVO.toolResult(result));
                    AiVO.ChatResponseVO response = new AiVO.ChatResponseVO();
                    response.setConversationId(conversationId);
                    response.setConversationCode(queryConversationCode(conversationId));
                    response.setEmployeeId(employeeId);
                    response.setReplyRole("ASSISTANT");
                    response.setReplyText(buildToolResultReply(result));
                    response.setToolPlan(plan);
                    response.setToolResult(result);
                    response.setReplyAt(LocalDateTime.now());
                    aiConversationService.recordMessage(actorUserId, runtimeUser.getUserUuid(), conversationId, "ASSISTANT", response.getReplyText());
                    return response;
                } else {
                    emit(onEvent, AiVO.ChatStreamEventVO.toolProposal(plan));
                }
                AiVO.ChatResponseVO response = new AiVO.ChatResponseVO();
                response.setConversationId(conversationId);
                response.setConversationCode(queryConversationCode(conversationId));
                response.setEmployeeId(employeeId);
                response.setReplyRole("ASSISTANT");
                response.setReplyText("Tool plan created. Please confirm before execution.");
                response.setToolPlan(plan);
                response.setReplyAt(LocalDateTime.now());
                aiConversationService.recordMessage(actorUserId, runtimeUser.getUserUuid(), conversationId, "ASSISTANT", response.getReplyText());
                return response;
            }

            emit(onEvent, AiVO.ChatStreamEventVO.status(generalConversation ? "Preparing context" : "Searching knowledge base"));
            List<AiVO.KnowledgeReferenceVO> references = resolveKnowledgeReferences(runtimeUser, generalConversation ? null : employeeId, request);
            request.setKnowledgeReferences(references);
            emit(onEvent, AiVO.ChatStreamEventVO.status("Calling model"));
            AiVO.ChatResponseVO response = onEvent == null
                    ? aiChatModelFactory.create(config).chat(request, employee, skills)
                    : aiChatModelFactory.create(config).streamChat(
                            request,
                            employee,
                            skills,
                            delta -> emit(onEvent, AiVO.ChatStreamEventVO.delta(delta)),
                            thinking -> emit(onEvent, AiVO.ChatStreamEventVO.thinking(thinking))
                    );
            emit(onEvent, AiVO.ChatStreamEventVO.status("Saving AI reply"));
            response.setReferences(references);
            response.setConversationId(conversationId);
            if (response.getConversationCode() == null) {
                response.setConversationCode(queryConversationCode(conversationId));
            }
            aiConversationService.recordMessage(actorUserId, runtimeUser.getUserUuid(), conversationId, "ASSISTANT", response.getReplyText());
            recordToolAuditLog(actorUserId, runtimeUser.getUserUuid(), employeeId, conversationId, request, response, confirmed);
            return response;
        } catch (RuntimeException exception) {
            recordFailedToolAuditLog(actorUserId, runtimeUser.getUserUuid(), employeeId, conversationId, request, confirmed, exception);
            throw exception;
        }
    }

    private AiVO.ChatResponseVO executeConfirmedToolChat(
            CurrentUser currentUser,
            Long employeeId,
            AiDTO.ChatRequest request,
            Consumer<AiVO.ChatStreamEventVO> onEvent
    ) {
        Long conversationId = request.getConversationId();
        try {
            Long actorUserId = requireLogin(currentUser);
            emit(onEvent, AiVO.ChatStreamEventVO.status("Executing confirmed tool"));
            if (employeeId == null || employeeId <= 0) {
                employeeId = aiAssistantEmployeeResolver.getOrCreateAssistantEmployee().getId();
            }
            if (conversationId == null) {
                conversationId = aiConversationService.ensureConversation(
                        actorUserId,
                        currentUser.getUserUuid(),
                        employeeId,
                        null,
                        buildConversationTitle(request.getMessage())
                );
            }
            aiConversationService.recordMessage(actorUserId, currentUser.getUserUuid(), conversationId, "USER", request.getMessage());
            AiDTO.ToolConfirmRequest confirmRequest = new AiDTO.ToolConfirmRequest();
            confirmRequest.setPendingToolCallId(request.getPendingToolCallId());
            AiVO.ToolExecuteResultVO result = aiToolOrchestrationService.confirm(currentUser, confirmRequest);
            emit(onEvent, AiVO.ChatStreamEventVO.toolResult(result));
            AiVO.ChatResponseVO response = new AiVO.ChatResponseVO();
            response.setConversationId(conversationId);
            response.setConversationCode(queryConversationCode(conversationId));
            response.setEmployeeId(employeeId);
            response.setReplyRole("ASSISTANT");
            response.setReplyText(result.getMessage() == null ? "Tool execution completed." : result.getMessage());
            response.setToolResult(result);
            response.setReplyAt(LocalDateTime.now());
            aiConversationService.recordMessage(actorUserId, currentUser.getUserUuid(), conversationId, "ASSISTANT", response.getReplyText());
            return response;
        } catch (RuntimeException exception) {
            recordFailedToolAuditLog(currentUser.getUserId(), currentUser.getUserUuid(), employeeId, conversationId, request, true, exception);
            throw exception;
        }
    }

    private AiVO.ChatResponseVO executeMultiEmployeeChat(
            CurrentUser currentUser,
            List<Long> employeeIds,
            AiDTO.ChatRequest request,
            Consumer<AiVO.ChatStreamEventVO> onEvent,
            boolean confirmed
    ) {
        Long conversationId = request == null ? null : request.getConversationId();
        try {
            Long actorUserId = requireLogin(currentUser);
            emit(onEvent, AiVO.ChatStreamEventVO.status("Preparing multi-employee chat"));
            conversationId = aiConversationService.ensureConversation(
                    actorUserId,
                    currentUser.getUserUuid(),
                    employeeIds.get(0),
                    conversationId,
                    buildConversationTitle(request.getMessage())
            );
            Long userMessageId = aiConversationService.recordMessage(actorUserId, currentUser.getUserUuid(), conversationId, "USER", request.getMessage());
            aiConversationService.recordMessageAttachments(currentUser, conversationId, userMessageId, request.getAttachments());

            StringBuilder replyText = new StringBuilder();
            StringBuilder thinkingContent = new StringBuilder();
            List<AiVO.KnowledgeReferenceVO> references = new ArrayList<>();

            for (Long targetEmployeeId : employeeIds) {
                AiDTO.ChatRequest employeeRequest = copyChatRequest(request, targetEmployeeId);
                AiVO.EmployeeDetailVO employee = queryEmployeeDetail(targetEmployeeId);
                if (!Boolean.TRUE.equals(employee.getEnabled())) {
                    throw new BizException(ErrorCode.BIZ_ERROR, "AI employee is disabled: " + displayEmployeeName(employee));
                }

                emit(onEvent, AiVO.ChatStreamEventVO.status("Checking skill permissions for " + displayEmployeeName(employee)));
                if (hasRequestedSkills(employeeRequest)) {
                    aiSkillPermissionChecker.verifyAllowed(employee.getId(), employeeRequest.getSkillCodes(), confirmed);
                }
                AiLlmServiceConfig config = aiLlmServiceConfigProvider.findById(employee.getDefaultLlmServiceId())
                        .or(() -> aiLlmServiceConfigProvider.findDefaultForEmployee(employee.getId()))
                        .orElse(null);
                List<AiVO.SkillVO> skills = aiToolRegistry.listRegisteredSkills(employee.getId());

                emit(onEvent, AiVO.ChatStreamEventVO.status("Searching knowledge base for " + displayEmployeeName(employee)));
                List<AiVO.KnowledgeReferenceVO> employeeReferences = resolveKnowledgeReferences(currentUser, employee.getId(), employeeRequest);
                references.addAll(employeeReferences);
                employeeRequest.setKnowledgeReferences(employeeReferences);

                String heading = "### " + displayEmployeeName(employee) + "\n\n";
                replyText.append(heading);
                emit(onEvent, AiVO.ChatStreamEventVO.delta(heading));
                emit(onEvent, AiVO.ChatStreamEventVO.status("正在调用数字员工 " + displayEmployeeName(employee)));
                AiVO.ChatResponseVO response = onEvent == null
                        ? aiChatModelFactory.create(config).chat(employeeRequest, employee, skills)
                        : aiChatModelFactory.create(config).streamChat(
                                employeeRequest,
                                employee,
                                skills,
                                delta -> emit(onEvent, AiVO.ChatStreamEventVO.delta(delta)),
                                thinking -> emit(onEvent, AiVO.ChatStreamEventVO.thinking(thinking))
                        );
                replyText.append(response.getReplyText() == null ? "" : response.getReplyText()).append("\n\n");
                if (StringUtils.hasText(response.getThinkingContent())) {
                    thinkingContent.append(heading).append(response.getThinkingContent()).append("\n\n");
                }
                recordToolAuditLog(actorUserId, currentUser.getUserUuid(), employee.getId(), conversationId, employeeRequest, response, confirmed);
            }

            emit(onEvent, AiVO.ChatStreamEventVO.status("Saving combined reply"));
            AiVO.ChatResponseVO combinedResponse = new AiVO.ChatResponseVO();
            combinedResponse.setConversationId(conversationId);
            combinedResponse.setConversationCode(queryConversationCode(conversationId));
            combinedResponse.setReplyText(replyText.toString().trim());
            String combinedThinkingContent = thinkingContent.toString().trim();
            combinedResponse.setThinkingContent(StringUtils.hasText(combinedThinkingContent) ? combinedThinkingContent : null);
            combinedResponse.setReplyRole("ASSISTANT");
            combinedResponse.setReferences(references);
            combinedResponse.setReplyAt(LocalDateTime.now());
            aiConversationService.recordMessage(actorUserId, currentUser.getUserUuid(), conversationId, "ASSISTANT", combinedResponse.getReplyText());
            return combinedResponse;
        } catch (RuntimeException exception) {
            recordFailedToolAuditLog(currentUser.getUserId(), currentUser.getUserUuid(), null, conversationId, request, confirmed, exception);
            throw exception;
        }
    }

    private List<Long> normalizeEmployeeIds(AiDTO.ChatRequest request) {
        if (request == null) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (request.getEmployeeIds() != null) {
            request.getEmployeeIds().stream()
                    .filter(id -> id != null && id > 0)
                    .forEach(ids::add);
        }
        if (ids.isEmpty() && request.getEmployeeId() != null && request.getEmployeeId() > 0) {
            ids.add(request.getEmployeeId());
        }
        return new ArrayList<>(ids);
    }

    private AiDTO.ChatRequest copyChatRequest(AiDTO.ChatRequest request, Long employeeId) {
        AiDTO.ChatRequest copy = new AiDTO.ChatRequest();
        copy.setEmployeeId(employeeId);
        copy.setConversationId(request.getConversationId());
        copy.setMessage(request.getMessage());
        copy.setEnableThinking(request.getEnableThinking());
        copy.setAttachments(request.getAttachments());
        copy.setSkillCodes(request.getSkillCodes());
        copy.setKnowledgeBaseIds(request.getKnowledgeBaseIds());
        copy.setConfirmed(request.getConfirmed());
        return copy;
    }

    private String displayEmployeeName(AiVO.EmployeeDetailVO employee) {
        if (StringUtils.hasText(employee.getNickname())) {
            return employee.getNickname().trim();
        }
        return employee.getUsername();
    }

    private String buildToolResultReply(AiVO.ToolExecuteResultVO result) {
        if (result == null) {
            return "Tool execution completed.";
        }
        if ("system.user.search".equals(result.getToolCode()) && result.getData() != null) {
            Object total = result.getData().get("total");
            Object count = result.getData().get("count");
            Object limit = result.getData().get("limit");
            return "Query complete: total users " + firstText(textValue(total), textValue(count), "0")
                    + ", returned " + firstText(textValue(count), "0")
                    + ", limit " + firstText(textValue(limit), "0") + ".";
        }
        return StringUtils.hasText(result.getMessage()) ? result.getMessage() : "Tool execution completed.";
    }

    private String textValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<AiVO.KnowledgeReferenceVO> resolveKnowledgeReferences(CurrentUser currentUser, Long employeeId, AiDTO.ChatRequest request) {
        if (!shouldUseKnowledge(request)) {
            return List.of();
        }
        if (request.getKnowledgeBaseIds() != null && !request.getKnowledgeBaseIds().isEmpty()) {
            return aiKnowledgeBaseAppService.retrieve(currentUser, request.getMessage(), request.getKnowledgeBaseIds(), 6);
        }
        if (employeeId == null) {
            return List.of();
        }
        return aiKnowledgeBaseAppService.retrieveForEmployee(currentUser, employeeId, request.getMessage(), 6);
    }

    private boolean shouldUseKnowledge(AiDTO.ChatRequest request) {
        if (request.getKnowledgeBaseIds() != null && !request.getKnowledgeBaseIds().isEmpty()) {
            return true;
        }
        if (request.getSkillCodes() == null || request.getSkillCodes().isEmpty()) {
            return true;
        }
        return request.getSkillCodes().contains("knowledge.search");
    }

    private void emit(Consumer<AiVO.ChatStreamEventVO> onEvent, AiVO.ChatStreamEventVO event) {
        if (onEvent != null) {
            onEvent.accept(event);
        }
    }

    private String buildConversationTitle(String message) {
        if (!StringUtils.hasText(message)) {
            return "New conversation";
        }
        String trimmed = message.trim().replaceAll("\\s+", " ");
        return trimmed.length() > 32 ? trimmed.substring(0, 32) + "..." : trimmed;
    }

    private void recordToolAuditLog(Long ownerUserId, String ownerUserUuid, Long employeeId, Long conversationId, AiDTO.ChatRequest request, AiVO.ChatResponseVO response, boolean confirmed) {
        insertToolAuditLog(
                ownerUserId,
                ownerUserUuid,
                employeeId,
                conversationId,
                request,
                confirmed,
                "allow",
                "SUCCESS",
                "AI chat completed",
                buildChatResponsePayload(response)
        );
    }

    private void recordFailedToolAuditLog(Long ownerUserId, String ownerUserUuid, Long employeeId, Long conversationId, AiDTO.ChatRequest request, boolean confirmed, RuntimeException exception) {
        try {
            String resultStatus = exception instanceof BizException ? "FAIL" : "ERROR";
            insertToolAuditLog(
                    ownerUserId,
                    ownerUserUuid,
                    employeeId,
                    conversationId,
                    request,
                    confirmed,
                    resolvePermissionMode(exception),
                    resultStatus,
                    truncate(defaultErrorMessage(exception), 512),
                    buildErrorResponsePayload(exception)
            );
        } catch (RuntimeException auditException) {
            log.warn("Failed to record AI tool audit failure employeeId={} conversationId={}", employeeId, conversationId, auditException);
        }
    }

    private void insertToolAuditLog(
            Long ownerUserId,
            String ownerUserUuid,
            Long employeeId,
            Long conversationId,
            AiDTO.ChatRequest request,
            boolean confirmed,
            String permissionMode,
            String resultStatus,
            String detailMessage,
            String responsePayloadJson
    ) {
        String auditSkillCode = firstSkillCode(request);
        boolean hasRequestedSkill = auditSkillCode != null;
        int inserted = jdbcTemplate.update(
                """
                        insert into ai_tool_audit_log (
                            conversation_id, employee_id, owner_user_id, owner_user_uuid, skill_code, tool_name, permission_mode,
                            confirm_required, confirm_result, result_status, detail_message,
                            request_payload_json, response_payload_json, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                conversationId,
                employeeId,
                ownerUserId,
                ownerUserUuid,
                hasRequestedSkill ? auditSkillCode : GENERAL_CHAT_SKILL_CODE,
                "chat",
                permissionMode,
                hasRequestedSkill ? 1 : 0,
                confirmed ? 1 : 0,
                resultStatus,
                detailMessage,
                buildChatPayload(request),
                responsePayloadJson,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        if (inserted != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "AI tool audit changed, please retry");
        }
    }

    private String buildChatPayload(AiDTO.ChatRequest request) {
        if (request == null) {
            return "{}";
        }
        return "{\"message\":\"" + safeJson(request.getMessage()) + "\",\"skillCodes\":\"" + safeJson(String.valueOf(request.getSkillCodes())) + "\"}";
    }

    private String buildChatResponsePayload(AiVO.ChatResponseVO response) {
        return "{\"replyText\":\"" + safeJson(response.getReplyText()) + "\",\"provider\":\"" + safeJson(response.getProvider()) + "\",\"model\":\"" + safeJson(response.getModel()) + "\"}";
    }

    private String buildErrorResponsePayload(RuntimeException exception) {
        String code = exception instanceof BizException bizException ? bizException.getErrorCode().getCode() : ErrorCode.SYSTEM_ERROR.getCode();
        return "{\"error\":\"" + safeJson(defaultErrorMessage(exception)) + "\",\"code\":\"" + safeJson(code) + "\"}";
    }

    private String firstSkillCode(AiDTO.ChatRequest request) {
        if (request == null || request.getSkillCodes() == null || request.getSkillCodes().isEmpty()) {
            return null;
        }
        return request.getSkillCodes().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private boolean hasRequestedSkills(AiDTO.ChatRequest request) {
        return firstSkillCode(request) != null;
    }

    private String resolvePermissionMode(RuntimeException exception) {
        if (exception instanceof BizException bizException && ErrorCode.FORBIDDEN.equals(bizException.getErrorCode())) {
            return "deny";
        }
        return "allow";
    }

    private String defaultErrorMessage(RuntimeException exception) {
        if (exception instanceof BizException bizException) {
            String message = bizException.getMessage();
            if (StringUtils.hasText(message)) {
                return message;
            }
            String userMessage = bizException.getUserMessage();
            if (StringUtils.hasText(userMessage)) {
                return userMessage;
            }
        }
        return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : "AI chat failed";
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private AiVO.EmployeeDetailVO queryEmployeeDetail(Long employeeId) {
        AiVO.EmployeeDetailVO employee = jdbcTemplate.query(
                """
                        select e.id, e.username, e.nickname, e.position, e.avatar_key as avatarKey,
                               e.description, e.greeting, e.system_prompt as systemPrompt,
                               e.default_llm_service_id as defaultLlmServiceId,
                               e.enabled, e.sort_order as sortOrder, e.create_time as createTime, e.update_time as updateTime,
                               s.title as defaultLlmServiceTitle
                        from ai_employee e
                        left join ai_llm_service s
                          on s.id = e.default_llm_service_id
                         and s.is_deleted = 0
                        where e.id = ?
                          and e.is_deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(AiVO.EmployeeDetailVO.class),
                employeeId
        ).stream().findFirst().orElse(null);
        if (employee == null) {
            throw new com.lumira.common.exception.BizException(com.lumira.common.enums.ErrorCode.NOT_FOUND, "AI employee not found");
        }
        return employee;
    }

    private String queryConversationCode(Long conversationId) {
        return jdbcTemplate.query(
                """
                        select conversation_code
                        from ai_conversation
                        where id = ?
                          and is_deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> rs.getString("conversation_code"),
                conversationId
        ).stream().findFirst().orElse(null);
    }

    private Long requireLogin(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Login required");
        }
        return currentUser.getUserId();
    }

    private Long requireChatPermission(CurrentUser currentUser) {
        Long actorUserId = requireLogin(currentUser);
        Set<String> permissions = currentUser.getPermissions() == null ? Set.of() : currentUser.getPermissions();
        if (!permissions.contains("*") && !permissions.contains(PERMISSION_AI_CHAT_SEND)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + PERMISSION_AI_CHAT_SEND);
        }
        return actorUserId;
    }

    private CurrentUser refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return currentUser;
        }
        if (sessionAuthenticationService != null) {
            CurrentUser refreshedUser = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    )
            );
            copyTrustedCurrentUser(currentUser, refreshedUser);
            return currentUser;
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user resolver is unavailable");
            }
            return currentUser;
        }
        Long userId = currentUser.getUserId();
        String userUuid = currentUser.getUserUuid();
        if (userId == null || userId <= 0 || !StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Trusted user context is invalid");
        }
        String normalizedUserUuid = userUuid.trim();
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user context is invalid");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid()) || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user context is invalid");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user is disabled or no longer active");
            }
            if (!StringUtils.hasText(userSnapshot.username())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user username is unavailable");
            }
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(userSnapshot.username().trim());
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Trusted user is disabled or no longer active");
        }
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        PermissionSnapshotService.PermissionSnapshot snapshot = simulatedRoleId != null
                ? permissionSnapshotService.loadGrantedRoleSnapshot(
                userId,
                normalizedUserUuid,
                simulatedRoleId
        )
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        if (snapshot == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user permission snapshot is unavailable");
            }
            return currentUser;
        }
        CurrentUser refreshed = new CurrentUser(
                userId,
                currentUser.getUsername(),
                currentUser.getSessionId(),
                currentUser.getSessionVersion(),
                true,
                snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()),
                snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()),
                snapshot.getPrimaryDeptId(),
                snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()),
                snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()),
                snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes())
        );
        refreshed.setUserUuid(normalizedUserUuid);
        refreshed.setPermissionsVersion(snapshot.getVersion());
        refreshed.setDefaultHomePath(snapshot.getDefaultHomePath());
        refreshed.setRequiresPasswordChange(currentUser.getRequiresPasswordChange());
        refreshed.setSimulatedRoleId(simulatedRoleId);
        refreshed.setLoginType(currentUser.getLoginType());
        copyTrustedCurrentUser(currentUser, refreshed);
        return currentUser;
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        CurrentUser refreshedUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(refreshedUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Trusted user context is invalid");
        }
        return refreshedUser;
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private void copyTrustedCurrentUser(CurrentUser target, CurrentUser source) {
        target.setUserId(source.getUserId());
        target.setUserUuid(source.getUserUuid());
        target.setUsername(source.getUsername());
        target.setSessionId(source.getSessionId());
        target.setSessionVersion(source.getSessionVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setPermissions(source.getPermissions() == null ? Set.of() : Set.copyOf(source.getPermissions()));
        target.setRoleIds(source.getRoleIds() == null ? Set.of() : Set.copyOf(source.getRoleIds()));
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds() == null ? Set.of() : Set.copyOf(source.getDeptIds()));
        target.setDescendantDeptIds(source.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(source.getDescendantDeptIds()));
        target.setDataScopes(source.getDataScopes() == null ? List.of() : List.copyOf(source.getDataScopes()));
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
    }
}
