package com.lumira.saas.modules.ai.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
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

    private final MyBatisQueryOperations jdbcTemplate;
    private final AiLlmServiceConfigProvider aiLlmServiceConfigProvider;
    private final AiChatModelFactory aiChatModelFactory;
    private final AiConversationService aiConversationService;
    private final AiToolRegistry aiToolRegistry;
    private final AiSkillPermissionChecker aiSkillPermissionChecker;
    private final AiKnowledgeBaseAppService aiKnowledgeBaseAppService;
    private final AiToolOrchestrationService aiToolOrchestrationService;
    private final AiAssistantEmployeeResolver aiAssistantEmployeeResolver;

    @Autowired
    DefaultAiEmployeeRuntimeService(
            MyBatisQueryOperations jdbcTemplate,
            AiLlmServiceConfigProvider aiLlmServiceConfigProvider,
            AiChatModelFactory aiChatModelFactory,
            AiConversationService aiConversationService,
            AiToolRegistry aiToolRegistry,
            AiSkillPermissionChecker aiSkillPermissionChecker,
            AiKnowledgeBaseAppService aiKnowledgeBaseAppService
    ) {
        this(
                jdbcTemplate,
                aiLlmServiceConfigProvider,
                aiChatModelFactory,
                aiConversationService,
                aiToolRegistry,
                aiSkillPermissionChecker,
                aiKnowledgeBaseAppService,
                null
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
            AiToolOrchestrationService aiToolOrchestrationService
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
        requireLogin(currentUser);
        List<Long> employeeIds = normalizeEmployeeIds(request);
        Long employeeId = employeeIds.size() == 1 ? employeeIds.get(0) : request == null ? null : request.getEmployeeId();
        Long conversationId = request == null ? null : request.getConversationId();
        boolean confirmed = request != null && Boolean.TRUE.equals(request.getConfirmed());
        boolean generalConversation = employeeId == null || employeeId <= 0;
        if (request != null && request.getPendingToolCallId() != null) {
            return executeConfirmedToolChat(currentUser, employeeId, request, onEvent);
        }
        if (employeeIds.size() > 1) {
            return executeMultiEmployeeChat(currentUser, employeeIds, request, onEvent, confirmed);
        }
        try {
            emit(onEvent, AiVO.ChatStreamEventVO.status(generalConversation ? "正在加载对话配置" : "正在加载数字员工配置"));
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
                    throw new BizException(ErrorCode.BIZ_ERROR, "数字员工已禁用");
                }
                emit(onEvent, AiVO.ChatStreamEventVO.status("正在校验技能授权"));
                if (hasRequestedSkills(request)) {
                    aiSkillPermissionChecker.verifyAllowed(employee.getId(), request.getSkillCodes(), confirmed);
                }
                config = aiLlmServiceConfigProvider.findById(employee.getDefaultLlmServiceId())
                        .or(() -> aiLlmServiceConfigProvider.findDefaultForEmployee(employee.getId()))
                        .orElse(null);
                skills = aiToolRegistry.listRegisteredSkills(employee.getId());
            }
            emit(onEvent, AiVO.ChatStreamEventVO.status("正在创建会话并保存用户消息"));
            conversationId = aiConversationService.ensureConversation(
                    currentUser.getUserId(),
                    employeeId,
                    request.getConversationId(),
                    buildConversationTitle(request.getMessage())
            );
            Long userMessageId = aiConversationService.recordMessage(conversationId, "USER", request.getMessage());
            aiConversationService.recordMessageAttachments(conversationId, userMessageId, request.getAttachments());

            AiDTO.ToolProposeRequest proposeRequest = new AiDTO.ToolProposeRequest();
            proposeRequest.setEmployeeId(employeeId);
            proposeRequest.setConversationId(conversationId);
            proposeRequest.setMessage(request.getMessage());
            proposeRequest.setAttachments(request.getAttachments());
            var proposedTool = aiToolOrchestrationService == null
                    ? java.util.Optional.<AiVO.ToolPlanVO>empty()
                    : aiToolOrchestrationService.tryPropose(currentUser, proposeRequest);
            if (proposedTool.isPresent()) {
                AiVO.ToolPlanVO plan = proposedTool.get();
                if ("BLOCKED".equalsIgnoreCase(plan.getStatus())) {
                    emit(onEvent, AiVO.ChatStreamEventVO.toolBlocked(plan, firstText(plan.getPolicyMessage(), plan.getSupervisorMessage(), "该操作已被平台防护规则拦截")));
                } else if (!Boolean.TRUE.equals(plan.getRequiresConfirm())) {
                    aiSkillPermissionChecker.verifyAllowed(employeeId, List.of(plan.getToolCode()), false);
                    AiDTO.ToolConfirmRequest confirmRequest = new AiDTO.ToolConfirmRequest();
                    confirmRequest.setPendingToolCallId(plan.getId());
                    AiVO.ToolExecuteResultVO result = aiToolOrchestrationService.confirm(currentUser, confirmRequest);
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
                    aiConversationService.recordMessage(conversationId, "ASSISTANT", response.getReplyText());
                    return response;
                } else {
                    emit(onEvent, AiVO.ChatStreamEventVO.toolProposal(plan));
                }
                AiVO.ChatResponseVO response = new AiVO.ChatResponseVO();
                response.setConversationId(conversationId);
                response.setConversationCode(queryConversationCode(conversationId));
                response.setEmployeeId(employeeId);
                response.setReplyRole("ASSISTANT");
                response.setReplyText("我已生成系统操作计划，请确认后执行。");
                response.setToolPlan(plan);
                response.setReplyAt(LocalDateTime.now());
                aiConversationService.recordMessage(conversationId, "ASSISTANT", response.getReplyText());
                return response;
            }

            emit(onEvent, AiVO.ChatStreamEventVO.status(generalConversation ? "正在准备上下文" : "正在检索知识库"));
            List<AiVO.KnowledgeReferenceVO> references = resolveKnowledgeReferences(currentUser, generalConversation ? null : employeeId, request);
            request.setKnowledgeReferences(references);
            emit(onEvent, AiVO.ChatStreamEventVO.status("正在调用模型"));
            AiVO.ChatResponseVO response = onEvent == null
                    ? aiChatModelFactory.create(config).chat(request, employee, skills)
                    : aiChatModelFactory.create(config).streamChat(
                            request,
                            employee,
                            skills,
                            delta -> emit(onEvent, AiVO.ChatStreamEventVO.delta(delta)),
                            thinking -> emit(onEvent, AiVO.ChatStreamEventVO.thinking(thinking))
                    );
            emit(onEvent, AiVO.ChatStreamEventVO.status("正在保存 AI 回复"));
            response.setReferences(references);
            response.setConversationId(conversationId);
            if (response.getConversationCode() == null) {
                response.setConversationCode(queryConversationCode(conversationId));
            }
            aiConversationService.recordMessage(conversationId, "ASSISTANT", response.getReplyText());
            recordToolAuditLog(employeeId, conversationId, request, response, confirmed);
            return response;
        } catch (RuntimeException exception) {
            recordFailedToolAuditLog(employeeId, conversationId, request, confirmed, exception);
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
            emit(onEvent, AiVO.ChatStreamEventVO.status("正在执行已确认的系统操作"));
            if (employeeId == null || employeeId <= 0) {
                employeeId = aiAssistantEmployeeResolver.getOrCreateAssistantEmployee().getId();
            }
            if (conversationId == null) {
                conversationId = aiConversationService.ensureConversation(
                        currentUser.getUserId(),
                        employeeId,
                        null,
                        buildConversationTitle(request.getMessage())
                );
            }
            aiConversationService.recordMessage(conversationId, "USER", request.getMessage());
            AiDTO.ToolConfirmRequest confirmRequest = new AiDTO.ToolConfirmRequest();
            confirmRequest.setPendingToolCallId(request.getPendingToolCallId());
            AiVO.ToolExecuteResultVO result = aiToolOrchestrationService.confirm(currentUser, confirmRequest);
            emit(onEvent, AiVO.ChatStreamEventVO.toolResult(result));
            AiVO.ChatResponseVO response = new AiVO.ChatResponseVO();
            response.setConversationId(conversationId);
            response.setConversationCode(queryConversationCode(conversationId));
            response.setEmployeeId(employeeId);
            response.setReplyRole("ASSISTANT");
            response.setReplyText(result.getMessage() == null ? "系统操作已执行完成。" : result.getMessage());
            response.setToolResult(result);
            response.setReplyAt(LocalDateTime.now());
            aiConversationService.recordMessage(conversationId, "ASSISTANT", response.getReplyText());
            return response;
        } catch (RuntimeException exception) {
            recordFailedToolAuditLog(employeeId, conversationId, request, true, exception);
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
            emit(onEvent, AiVO.ChatStreamEventVO.status("正在准备多智能体协同"));
            conversationId = aiConversationService.ensureConversation(
                    currentUser.getUserId(),
                    employeeIds.get(0),
                    conversationId,
                    buildConversationTitle(request.getMessage())
            );
            Long userMessageId = aiConversationService.recordMessage(conversationId, "USER", request.getMessage());
            aiConversationService.recordMessageAttachments(conversationId, userMessageId, request.getAttachments());

            StringBuilder replyText = new StringBuilder();
            StringBuilder thinkingContent = new StringBuilder();
            List<AiVO.KnowledgeReferenceVO> references = new ArrayList<>();

            for (Long targetEmployeeId : employeeIds) {
                AiDTO.ChatRequest employeeRequest = copyChatRequest(request, targetEmployeeId);
                AiVO.EmployeeDetailVO employee = queryEmployeeDetail(targetEmployeeId);
                if (!Boolean.TRUE.equals(employee.getEnabled())) {
                    throw new BizException(ErrorCode.BIZ_ERROR, "数字员工已禁用：" + displayEmployeeName(employee));
                }

                emit(onEvent, AiVO.ChatStreamEventVO.status("正在校验 " + displayEmployeeName(employee) + " 的技能授权"));
                if (hasRequestedSkills(employeeRequest)) {
                    aiSkillPermissionChecker.verifyAllowed(employee.getId(), employeeRequest.getSkillCodes(), confirmed);
                }
                AiLlmServiceConfig config = aiLlmServiceConfigProvider.findById(employee.getDefaultLlmServiceId())
                        .or(() -> aiLlmServiceConfigProvider.findDefaultForEmployee(employee.getId()))
                        .orElse(null);
                List<AiVO.SkillVO> skills = aiToolRegistry.listRegisteredSkills(employee.getId());

                emit(onEvent, AiVO.ChatStreamEventVO.status("正在检索 " + displayEmployeeName(employee) + " 的知识库"));
                List<AiVO.KnowledgeReferenceVO> employeeReferences = resolveKnowledgeReferences(currentUser, employee.getId(), employeeRequest);
                references.addAll(employeeReferences);
                employeeRequest.setKnowledgeReferences(employeeReferences);

                String heading = "### " + displayEmployeeName(employee) + "\n\n";
                replyText.append(heading);
                emit(onEvent, AiVO.ChatStreamEventVO.delta(heading));
                emit(onEvent, AiVO.ChatStreamEventVO.status("正在调用 " + displayEmployeeName(employee)));
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
                recordToolAuditLog(employee.getId(), conversationId, employeeRequest, response, confirmed);
            }

            emit(onEvent, AiVO.ChatStreamEventVO.status("正在保存多智能体回复"));
            AiVO.ChatResponseVO combinedResponse = new AiVO.ChatResponseVO();
            combinedResponse.setConversationId(conversationId);
            combinedResponse.setConversationCode(queryConversationCode(conversationId));
            combinedResponse.setReplyText(replyText.toString().trim());
            String combinedThinkingContent = thinkingContent.toString().trim();
            combinedResponse.setThinkingContent(StringUtils.hasText(combinedThinkingContent) ? combinedThinkingContent : null);
            combinedResponse.setReplyRole("ASSISTANT");
            combinedResponse.setReferences(references);
            combinedResponse.setReplyAt(LocalDateTime.now());
            aiConversationService.recordMessage(conversationId, "ASSISTANT", combinedResponse.getReplyText());
            return combinedResponse;
        } catch (RuntimeException exception) {
            recordFailedToolAuditLog(null, conversationId, request, confirmed, exception);
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
            return "系统工具已执行完成。";
        }
        if ("system.user.search".equals(result.getToolCode()) && result.getData() != null) {
            Object total = result.getData().get("total");
            Object count = result.getData().get("count");
            Object limit = result.getData().get("limit");
            return "查询完成：当前权限范围内共有 " + firstText(textValue(total), textValue(count), "0")
                    + " 个系统用户。本次返回 " + firstText(textValue(count), "0")
                    + " 条脱敏用户记录，最多展示 " + firstText(textValue(limit), "0") + " 条。";
        }
        return StringUtils.hasText(result.getMessage()) ? result.getMessage() : "系统工具已执行完成。";
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
            return "新会话";
        }
        String trimmed = message.trim().replaceAll("\\s+", " ");
        return trimmed.length() > 32 ? trimmed.substring(0, 32) + "..." : trimmed;
    }

    private void recordToolAuditLog(Long employeeId, Long conversationId, AiDTO.ChatRequest request, AiVO.ChatResponseVO response, boolean confirmed) {
        insertToolAuditLog(
                employeeId,
                conversationId,
                request,
                confirmed,
                "allow",
                "SUCCESS",
                "数字员工聊天请求已处理",
                buildChatResponsePayload(response)
        );
    }

    private void recordFailedToolAuditLog(Long employeeId, Long conversationId, AiDTO.ChatRequest request, boolean confirmed, RuntimeException exception) {
        try {
            String resultStatus = exception instanceof BizException ? "FAIL" : "ERROR";
            insertToolAuditLog(
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
        jdbcTemplate.update(
                """
                        insert into ai_tool_audit_log (
                            conversation_id, employee_id, skill_code, tool_name, permission_mode,
                            confirm_required, confirm_result, result_status, detail_message,
                            request_payload_json, response_payload_json, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                conversationId,
                employeeId,
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
        return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : "AI 聊天请求处理失败";
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
            throw new com.lumira.common.exception.BizException(com.lumira.common.enums.ErrorCode.NOT_FOUND, "数字员工不存在");
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

    private void requireLogin(CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "Login required");
        }
    }
}
