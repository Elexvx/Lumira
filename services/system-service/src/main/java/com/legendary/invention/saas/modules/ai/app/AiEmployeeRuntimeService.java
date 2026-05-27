package com.legendary.invention.saas.modules.ai.app;

import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.ai.dto.AiDTO;
import com.legendary.invention.saas.modules.ai.vo.AiVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
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

    private final MyBatisQueryOperations jdbcTemplate;
    private final AiLlmServiceConfigProvider aiLlmServiceConfigProvider;
    private final AiChatModelFactory aiChatModelFactory;
    private final AiConversationService aiConversationService;
    private final AiToolRegistry aiToolRegistry;
    private final AiSkillPermissionChecker aiSkillPermissionChecker;
    private final AiKnowledgeBaseAppService aiKnowledgeBaseAppService;

    DefaultAiEmployeeRuntimeService(
            MyBatisQueryOperations jdbcTemplate,
            AiLlmServiceConfigProvider aiLlmServiceConfigProvider,
            AiChatModelFactory aiChatModelFactory,
            AiConversationService aiConversationService,
            AiToolRegistry aiToolRegistry,
            AiSkillPermissionChecker aiSkillPermissionChecker,
            AiKnowledgeBaseAppService aiKnowledgeBaseAppService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiLlmServiceConfigProvider = aiLlmServiceConfigProvider;
        this.aiChatModelFactory = aiChatModelFactory;
        this.aiConversationService = aiConversationService;
        this.aiToolRegistry = aiToolRegistry;
        this.aiSkillPermissionChecker = aiSkillPermissionChecker;
        this.aiKnowledgeBaseAppService = aiKnowledgeBaseAppService;
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
        Long tenantId = currentTenantId(currentUser);
        List<Long> employeeIds = normalizeEmployeeIds(request);
        Long employeeId = employeeIds.size() == 1 ? employeeIds.get(0) : request == null ? null : request.getEmployeeId();
        Long conversationId = request == null ? null : request.getConversationId();
        boolean confirmed = request != null && Boolean.TRUE.equals(request.getConfirmed());
        if (employeeIds.size() > 1) {
            return executeMultiEmployeeChat(currentUser, tenantId, employeeIds, request, onEvent, confirmed);
        }
        try {
            emit(onEvent, AiVO.ChatStreamEventVO.status(employeeId == null ? "正在加载对话配置" : "正在加载数字员工配置"));
            AiVO.EmployeeDetailVO employee;
            List<AiVO.SkillVO> skills;
            AiLlmServiceConfig config;
            if (employeeId == null) {
                employee = buildDefaultConversationEmployee();
                skills = List.of();
                config = aiLlmServiceConfigProvider.findDefault(tenantId).orElse(null);
            } else {
                employee = queryEmployeeDetail(tenantId, employeeId);
                employeeId = employee.getId();
                if (!Boolean.TRUE.equals(employee.getEnabled())) {
                    throw new BizException(ErrorCode.BIZ_ERROR, "数字员工已禁用");
                }
                emit(onEvent, AiVO.ChatStreamEventVO.status("正在校验技能授权"));
                aiSkillPermissionChecker.verifyAllowed(tenantId, employee.getId(), request.getSkillCodes(), confirmed);
                config = aiLlmServiceConfigProvider.findById(tenantId, employee.getDefaultLlmServiceId())
                        .or(() -> aiLlmServiceConfigProvider.findDefaultForEmployee(tenantId, employee.getId()))
                        .orElse(null);
                skills = aiToolRegistry.listRegisteredSkills(tenantId, employee.getId());
            }
            emit(onEvent, AiVO.ChatStreamEventVO.status("正在创建会话并保存用户消息"));
            conversationId = aiConversationService.ensureConversation(
                    tenantId,
                    currentUser.getUserId(),
                    employeeId,
                    request.getConversationId(),
                    buildConversationTitle(request.getMessage())
            );
            Long userMessageId = aiConversationService.recordMessage(tenantId, conversationId, "USER", request.getMessage());
            aiConversationService.recordMessageAttachments(tenantId, conversationId, userMessageId, request.getAttachments());

            emit(onEvent, AiVO.ChatStreamEventVO.status(employeeId == null ? "正在准备上下文" : "正在检索知识库"));
            List<AiVO.KnowledgeReferenceVO> references = resolveKnowledgeReferences(currentUser, employeeId, request);
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
                response.setConversationCode(queryConversationCode(tenantId, conversationId));
            }
            aiConversationService.recordMessage(tenantId, conversationId, "ASSISTANT", response.getReplyText());
            recordToolAuditLog(tenantId, employeeId, conversationId, request, response, confirmed);
            return response;
        } catch (RuntimeException exception) {
            recordFailedToolAuditLog(tenantId, employeeId, conversationId, request, confirmed, exception);
            throw exception;
        }
    }

    private AiVO.ChatResponseVO executeMultiEmployeeChat(
            CurrentUser currentUser,
            Long tenantId,
            List<Long> employeeIds,
            AiDTO.ChatRequest request,
            Consumer<AiVO.ChatStreamEventVO> onEvent,
            boolean confirmed
    ) {
        Long conversationId = request == null ? null : request.getConversationId();
        try {
            emit(onEvent, AiVO.ChatStreamEventVO.status("正在准备多智能体协同"));
            conversationId = aiConversationService.ensureConversation(
                    tenantId,
                    currentUser.getUserId(),
                    null,
                    conversationId,
                    buildConversationTitle(request.getMessage())
            );
            Long userMessageId = aiConversationService.recordMessage(tenantId, conversationId, "USER", request.getMessage());
            aiConversationService.recordMessageAttachments(tenantId, conversationId, userMessageId, request.getAttachments());

            StringBuilder replyText = new StringBuilder();
            StringBuilder thinkingContent = new StringBuilder();
            List<AiVO.KnowledgeReferenceVO> references = new ArrayList<>();

            for (Long targetEmployeeId : employeeIds) {
                AiDTO.ChatRequest employeeRequest = copyChatRequest(request, targetEmployeeId);
                AiVO.EmployeeDetailVO employee = queryEmployeeDetail(tenantId, targetEmployeeId);
                if (!Boolean.TRUE.equals(employee.getEnabled())) {
                    throw new BizException(ErrorCode.BIZ_ERROR, "数字员工已禁用：" + displayEmployeeName(employee));
                }

                emit(onEvent, AiVO.ChatStreamEventVO.status("正在校验 " + displayEmployeeName(employee) + " 的技能授权"));
                aiSkillPermissionChecker.verifyAllowed(tenantId, employee.getId(), employeeRequest.getSkillCodes(), confirmed);
                AiLlmServiceConfig config = aiLlmServiceConfigProvider.findById(tenantId, employee.getDefaultLlmServiceId())
                        .or(() -> aiLlmServiceConfigProvider.findDefaultForEmployee(tenantId, employee.getId()))
                        .orElse(null);
                List<AiVO.SkillVO> skills = aiToolRegistry.listRegisteredSkills(tenantId, employee.getId());

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
                recordToolAuditLog(tenantId, employee.getId(), conversationId, employeeRequest, response, confirmed);
            }

            emit(onEvent, AiVO.ChatStreamEventVO.status("正在保存多智能体回复"));
            AiVO.ChatResponseVO combinedResponse = new AiVO.ChatResponseVO();
            combinedResponse.setConversationId(conversationId);
            combinedResponse.setConversationCode(queryConversationCode(tenantId, conversationId));
            combinedResponse.setReplyText(replyText.toString().trim());
            String combinedThinkingContent = thinkingContent.toString().trim();
            combinedResponse.setThinkingContent(StringUtils.hasText(combinedThinkingContent) ? combinedThinkingContent : null);
            combinedResponse.setReplyRole("ASSISTANT");
            combinedResponse.setReferences(references);
            combinedResponse.setReplyAt(LocalDateTime.now());
            aiConversationService.recordMessage(tenantId, conversationId, "ASSISTANT", combinedResponse.getReplyText());
            return combinedResponse;
        } catch (RuntimeException exception) {
            recordFailedToolAuditLog(tenantId, null, conversationId, request, confirmed, exception);
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

    private AiVO.EmployeeDetailVO buildDefaultConversationEmployee() {
        AiVO.EmployeeDetailVO employee = new AiVO.EmployeeDetailVO();
        employee.setUsername("ai-assistant");
        employee.setNickname("AI 助手");
        employee.setPosition("通用对话");
        employee.setEnabled(true);
        employee.setSystemPrompt("你是企业后台系统中的通用 AI 助手。用户未选择任何数字员工时，你应作为普通对话助手提供清晰、准确、简洁的帮助；不要声称自己拥有特定数字员工的身份、技能、知识库或业务工具权限。");
        employee.setSkills(List.of());
        return employee;
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

    private void recordToolAuditLog(Long tenantId, Long employeeId, Long conversationId, AiDTO.ChatRequest request, AiVO.ChatResponseVO response, boolean confirmed) {
        insertToolAuditLog(
                tenantId,
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

    private void recordFailedToolAuditLog(Long tenantId, Long employeeId, Long conversationId, AiDTO.ChatRequest request, boolean confirmed, RuntimeException exception) {
        try {
            String resultStatus = exception instanceof BizException ? "FAIL" : "ERROR";
            insertToolAuditLog(
                    tenantId,
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
            log.warn("Failed to record AI tool audit failure tenantId={} employeeId={} conversationId={}", tenantId, employeeId, conversationId, auditException);
        }
    }

    private void insertToolAuditLog(
            Long tenantId,
            Long employeeId,
            Long conversationId,
            AiDTO.ChatRequest request,
            boolean confirmed,
            String permissionMode,
            String resultStatus,
            String detailMessage,
            String responsePayloadJson
    ) {
        jdbcTemplate.update(
                """
                        insert into ai_tool_audit_log (
                            tenant_id, conversation_id, employee_id, skill_code, tool_name, permission_mode,
                            confirm_required, confirm_result, result_status, detail_message,
                            request_payload_json, response_payload_json, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                tenantId,
                conversationId,
                employeeId,
                firstSkillCode(request),
                "chat",
                permissionMode,
                firstSkillCode(request) == null ? 0 : 1,
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
        return request == null || request.getSkillCodes() == null || request.getSkillCodes().isEmpty() ? null : request.getSkillCodes().get(0);
    }

    private String resolvePermissionMode(RuntimeException exception) {
        if (exception instanceof BizException bizException && ErrorCode.FORBIDDEN.equals(bizException.getErrorCode())) {
            return "deny";
        }
        return "allow";
    }

    private String defaultErrorMessage(RuntimeException exception) {
        if (exception instanceof BizException bizException && StringUtils.hasText(bizException.getUserMessage())) {
            return bizException.getUserMessage();
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

    private AiVO.EmployeeDetailVO queryEmployeeDetail(Long tenantId, Long employeeId) {
        AiVO.EmployeeDetailVO employee = jdbcTemplate.query(
                """
                        select e.id, e.tenant_id as tenantId, e.username, e.nickname, e.position, e.avatar_key as avatarKey,
                               e.description, e.greeting, e.system_prompt as systemPrompt,
                               e.default_llm_service_id as defaultLlmServiceId,
                               e.enabled, e.sort_order as sortOrder, e.create_time as createTime, e.update_time as updateTime,
                               s.title as defaultLlmServiceTitle
                        from ai_employee e
                        left join ai_llm_service s
                          on s.id = e.default_llm_service_id
                         and s.tenant_id = e.tenant_id
                         and s.is_deleted = 0
                        where e.tenant_id = ?
                          and e.id = ?
                          and e.is_deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(AiVO.EmployeeDetailVO.class),
                tenantId,
                employeeId
        ).stream().findFirst().orElse(null);
        if (employee == null) {
            throw new com.legendary.invention.saas.common.exception.BizException(com.legendary.invention.saas.common.enums.ErrorCode.NOT_FOUND, "数字员工不存在");
        }
        employee.setSkills(queryEmployeeSkills(tenantId, employeeId));
        return employee;
    }

    private List<AiVO.EmployeeSkillVO> queryEmployeeSkills(Long tenantId, Long employeeId) {
        return jdbcTemplate.query(
                """
                        select k.id, k.skill_code as skillCode, k.skill_name as skillName, k.category, k.description,
                               k.risk_level as riskLevel, k.read_only as readOnly, k.need_confirm as needConfirm,
                               k.enabled,
                               coalesce(r.permission_mode, case when k.read_only = 1 then 'visit' else 'deny' end) as permissionMode
                        from ai_skill k
                        left join ai_employee_skill r
                          on r.skill_code = k.skill_code
                         and r.tenant_id = ?
                         and r.employee_id = ?
                         and r.is_deleted = 0
                        where k.is_deleted = 0
                          and k.enabled = 1
                        order by k.category asc, k.skill_code asc
                        """,
                new BeanPropertyRowMapper<>(AiVO.EmployeeSkillVO.class),
                tenantId,
                employeeId
        );
    }

    private String queryConversationCode(Long tenantId, Long conversationId) {
        return jdbcTemplate.query(
                """
                        select conversation_code
                        from ai_conversation
                        where tenant_id = ?
                          and id = ?
                          and is_deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> rs.getString("conversation_code"),
                tenantId,
                conversationId
        ).stream().findFirst().orElse(null);
    }

    private Long currentTenantId(CurrentUser currentUser) {
        return com.legendary.invention.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
    }
}
