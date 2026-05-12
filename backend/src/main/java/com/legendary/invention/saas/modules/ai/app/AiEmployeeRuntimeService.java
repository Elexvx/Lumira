package com.legendary.invention.saas.modules.ai.app;

import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.ai.dto.AiDTO;
import com.legendary.invention.saas.modules.ai.vo.AiVO;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

public interface AiEmployeeRuntimeService {

    AiVO.ChatResponseVO chat(CurrentUser currentUser, AiDTO.ChatRequest request);

    AiVO.ChatResponseVO streamChat(CurrentUser currentUser, AiDTO.ChatRequest request, Consumer<AiVO.ChatStreamEventVO> onEvent);
}

@Service
@Primary
class DefaultAiEmployeeRuntimeService implements AiEmployeeRuntimeService {

    private final JdbcTemplate jdbcTemplate;
    private final AiLlmServiceConfigProvider aiLlmServiceConfigProvider;
    private final AiChatModelFactory aiChatModelFactory;
    private final AiConversationService aiConversationService;
    private final AiToolRegistry aiToolRegistry;
    private final AiSkillPermissionChecker aiSkillPermissionChecker;

    DefaultAiEmployeeRuntimeService(
            JdbcTemplate jdbcTemplate,
            AiLlmServiceConfigProvider aiLlmServiceConfigProvider,
            AiChatModelFactory aiChatModelFactory,
            AiConversationService aiConversationService,
            AiToolRegistry aiToolRegistry,
            AiSkillPermissionChecker aiSkillPermissionChecker
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiLlmServiceConfigProvider = aiLlmServiceConfigProvider;
        this.aiChatModelFactory = aiChatModelFactory;
        this.aiConversationService = aiConversationService;
        this.aiToolRegistry = aiToolRegistry;
        this.aiSkillPermissionChecker = aiSkillPermissionChecker;
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
        emit(onEvent, AiVO.ChatStreamEventVO.status("正在加载数字员工配置"));
        AiVO.EmployeeDetailVO employee = queryEmployeeDetail(tenantId, request.getEmployeeId());
        if (!Boolean.TRUE.equals(employee.getEnabled())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "数字员工已禁用");
        }
        emit(onEvent, AiVO.ChatStreamEventVO.status("正在校验技能授权"));
        boolean confirmed = Boolean.TRUE.equals(request.getConfirmed());
        aiSkillPermissionChecker.verifyAllowed(tenantId, employee.getId(), request.getSkillCodes(), confirmed);
        emit(onEvent, AiVO.ChatStreamEventVO.status("正在创建会话并保存用户消息"));
        Long conversationId = aiConversationService.ensureConversation(
                tenantId,
                employee.getId(),
                request.getConversationId(),
                buildConversationTitle(request.getMessage())
        );
        Long userMessageId = aiConversationService.recordMessage(tenantId, conversationId, "USER", request.getMessage());
        aiConversationService.recordMessageAttachments(tenantId, conversationId, userMessageId, request.getAttachments());

        AiLlmServiceConfig config = aiLlmServiceConfigProvider.findById(tenantId, employee.getDefaultLlmServiceId())
                .or(() -> aiLlmServiceConfigProvider.findDefaultForEmployee(tenantId, employee.getId()))
                .orElse(null);
        List<AiVO.SkillVO> skills = aiToolRegistry.listRegisteredSkills(tenantId, employee.getId());
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
        response.setConversationId(conversationId);
        if (response.getConversationCode() == null) {
            response.setConversationCode(queryConversationCode(tenantId, conversationId));
        }
        aiConversationService.recordMessage(tenantId, conversationId, "ASSISTANT", response.getReplyText());
        recordToolAuditLog(tenantId, employee.getId(), conversationId, request, response, confirmed);
        return response;
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
                request.getSkillCodes() == null || request.getSkillCodes().isEmpty() ? null : request.getSkillCodes().get(0),
                "chat",
                "allow",
                request.getSkillCodes() == null || request.getSkillCodes().isEmpty() ? 0 : 1,
                confirmed ? 1 : 0,
                "SUCCESS",
                "数字员工聊天请求已处理",
                buildChatPayload(request),
                buildChatResponsePayload(response),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private String buildChatPayload(AiDTO.ChatRequest request) {
        return "{\"message\":\"" + safeJson(request.getMessage()) + "\",\"skillCodes\":\"" + safeJson(String.valueOf(request.getSkillCodes())) + "\"}";
    }

    private String buildChatResponsePayload(AiVO.ChatResponseVO response) {
        return "{\"replyText\":\"" + safeJson(response.getReplyText()) + "\",\"provider\":\"" + safeJson(response.getProvider()) + "\",\"model\":\"" + safeJson(response.getModel()) + "\"}";
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
        if (currentUser == null || currentUser.getCurrentTenantId() == null) {
            throw new com.legendary.invention.saas.common.exception.BizException(com.legendary.invention.saas.common.enums.ErrorCode.TENANT_ERROR, "租户上下文无效");
        }
        return currentUser.getCurrentTenantId();
    }
}
