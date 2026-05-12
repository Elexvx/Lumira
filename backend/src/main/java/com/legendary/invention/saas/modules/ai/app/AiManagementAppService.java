package com.legendary.invention.saas.modules.ai.app;

import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.common.vo.PageResponse;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.ai.dto.AiDTO;
import com.legendary.invention.saas.modules.ai.infrastructure.AiSecretCryptoService;
import com.legendary.invention.saas.modules.ai.vo.AiVO;
import com.legendary.invention.saas.modules.audit.app.OperationAuditService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AiManagementAppService {

    private static final String DEFAULT_SYSTEM_PROMPT_TEMPLATE = """
            你是一名企业级 SaaS 平台中的数字员工。
            你的目标是：基于当前租户的授权范围，稳妥、专业、清晰地完成用户交办的任务。
            你必须遵循以下要求：
            1. 先确认上下文，再执行任务。
            2. 遵守租户隔离和权限边界，不越权访问数据。
            3. 当任务涉及高风险操作时，先请求二次确认。
            4. 输出尽量简洁、结构清晰，优先给出可执行结论。
            """;

    private final JdbcTemplate jdbcTemplate;
    private final OperationAuditService operationAuditService;
    private final AiSecretCryptoService aiSecretCryptoService;
    private final AiEmployeeRuntimeService aiEmployeeRuntimeService;

    public AiManagementAppService(
            JdbcTemplate jdbcTemplate,
            OperationAuditService operationAuditService,
            AiSecretCryptoService aiSecretCryptoService,
            AiEmployeeRuntimeService aiEmployeeRuntimeService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.operationAuditService = operationAuditService;
        this.aiSecretCryptoService = aiSecretCryptoService;
        this.aiEmployeeRuntimeService = aiEmployeeRuntimeService;
    }

    public PageResponse<AiVO.EmployeeVO> listEmployees(CurrentUser currentUser, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        return pageQuery(
                """
                        select e.id, e.tenant_id as tenantId, e.username, e.nickname, e.position, e.avatar_key as avatarKey,
                               e.description, e.greeting, e.default_llm_service_id as defaultLlmServiceId,
                               e.enabled, e.sort_order as sortOrder, e.create_time as createTime, e.update_time as updateTime,
                               s.title as defaultLlmServiceTitle
                        from ai_employee e
                        left join ai_llm_service s
                          on s.id = e.default_llm_service_id
                         and s.tenant_id = e.tenant_id
                         and s.is_deleted = 0
                        where e.tenant_id = ?
                          and e.is_deleted = 0
                        order by e.sort_order asc, e.id desc
                        """,
                "select count(1) from ai_employee e where e.tenant_id = ? and e.is_deleted = 0",
                AiVO.EmployeeVO.class,
                pageNo,
                pageSize,
                List.of(tenantId)
        );
    }

    public AiVO.EmployeeDetailVO getEmployee(CurrentUser currentUser, Long id) {
        Long tenantId = currentTenantId(currentUser);
        AiVO.EmployeeDetailVO employee = queryEmployeeDetail(tenantId, id);
        employee.setDefaultSystemPromptTemplate(DEFAULT_SYSTEM_PROMPT_TEMPLATE);
        employee.setSkills(listEmployeeSkills(tenantId, id));
        return employee;
    }

    @Transactional
    public AiVO.EmployeeDetailVO createEmployee(CurrentUser currentUser, AiDTO.EmployeeUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        validateEmployeeUsernameAvailable(tenantId, request.getUsername().trim(), null);
        validateDefaultLlmService(tenantId, request.getDefaultLlmServiceId());
        LocalDateTime now = LocalDateTime.now();
        String systemPrompt = StringUtils.hasText(request.getSystemPrompt()) ? request.getSystemPrompt() : DEFAULT_SYSTEM_PROMPT_TEMPLATE;
        jdbcTemplate.update(
                """
                        insert into ai_employee (
                            tenant_id, username, nickname, position, avatar_key, description, greeting,
                            system_prompt, default_llm_service_id, enabled, sort_order, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, 0, ?, ?)
                        """,
                tenantId,
                request.getUsername().trim(),
                request.getNickname().trim(),
                cleanNullable(request.getPosition()),
                cleanNullable(request.getAvatarKey()),
                cleanNullable(request.getDescription()),
                cleanNullable(request.getGreeting()),
                systemPrompt,
                request.getDefaultLlmServiceId(),
                request.getSortOrder() == null ? 0 : request.getSortOrder(),
                now,
                now
        );
        Long employeeId = queryEmployeeId(tenantId, request.getUsername().trim());
        if (request.getSkills() != null) {
            replaceEmployeeSkills(tenantId, employeeId, request.getSkills());
        }
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "employee-create", "CREATE", "SUCCESS", "创建数字员工: " + request.getUsername());
        return getEmployee(currentUser, employeeId);
    }

    @Transactional
    public AiVO.EmployeeDetailVO updateEmployee(CurrentUser currentUser, Long id, AiDTO.EmployeeUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        requireEmployee(tenantId, id);
        validateEmployeeUsernameAvailable(tenantId, request.getUsername().trim(), id);
        validateDefaultLlmService(tenantId, request.getDefaultLlmServiceId());
        jdbcTemplate.update(
                """
                        update ai_employee
                        set username = ?, nickname = ?, position = ?, avatar_key = ?, description = ?, greeting = ?,
                            system_prompt = ?, default_llm_service_id = ?, sort_order = ?, update_time = ?
                        where tenant_id = ? and id = ? and is_deleted = 0
                        """,
                request.getUsername().trim(),
                request.getNickname().trim(),
                cleanNullable(request.getPosition()),
                cleanNullable(request.getAvatarKey()),
                cleanNullable(request.getDescription()),
                cleanNullable(request.getGreeting()),
                cleanNullable(request.getSystemPrompt()),
                request.getDefaultLlmServiceId(),
                request.getSortOrder() == null ? 0 : request.getSortOrder(),
                LocalDateTime.now(),
                tenantId,
                id
        );
        if (request.getSkills() != null) {
            replaceEmployeeSkills(tenantId, id, request.getSkills());
        }
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "employee-update", "UPDATE", "SUCCESS", "更新数字员工: " + request.getUsername());
        return getEmployee(currentUser, id);
    }

    @Transactional
    public boolean deleteEmployee(CurrentUser currentUser, Long id) {
        Long tenantId = currentTenantId(currentUser);
        requireEmployee(tenantId, id);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        update ai_employee
                        set is_deleted = 1, update_time = ?
                        where tenant_id = ? and id = ? and is_deleted = 0
                        """,
                now,
                tenantId,
                id
        );
        jdbcTemplate.update(
                """
                        update ai_employee_skill
                        set is_deleted = 1, update_time = ?
                        where tenant_id = ? and employee_id = ? and is_deleted = 0
                        """,
                now,
                tenantId,
                id
        );
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "employee-delete", "DELETE", "SUCCESS", "删除数字员工: " + id);
        return true;
    }

    @Transactional
    public boolean updateEmployeeEnabled(CurrentUser currentUser, Long id, boolean enabled) {
        Long tenantId = currentTenantId(currentUser);
        requireEmployee(tenantId, id);
        jdbcTemplate.update(
                """
                        update ai_employee
                        set enabled = ?, update_time = ?
                        where tenant_id = ? and id = ? and is_deleted = 0
                        """,
                enabled ? 1 : 0,
                LocalDateTime.now(),
                tenantId,
                id
        );
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "employee-enabled", "UPDATE", "SUCCESS", "更新数字员工状态: " + id + " -> " + enabled);
        return true;
    }

    public AiVO.PromptTemplateVO employeeTemplate(CurrentUser currentUser) {
        AiVO.PromptTemplateVO template = new AiVO.PromptTemplateVO();
        template.setDefaultSystemPromptTemplate(DEFAULT_SYSTEM_PROMPT_TEMPLATE);
        return template;
    }

    public PageResponse<AiVO.LlmServiceVO> listLlmServices(CurrentUser currentUser, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        return pageQuery(
                """
                        select id, tenant_id as tenantId, provider, code, title, base_url as baseUrl,
                               default_model as defaultModel, enabled, timeout_ms as timeoutMs, temperature,
                               max_tokens as maxTokens,
                               case when api_key_encrypted is null or api_key_encrypted = '' then 0 else 1 end as apiKeyConfigured,
                               case when api_key_encrypted is null or api_key_encrypted = '' then null else '******' end as apiKeyMasked,
                               create_time as createTime, update_time as updateTime
                        from ai_llm_service
                        where tenant_id = ?
                          and is_deleted = 0
                        order by id desc
                        """,
                "select count(1) from ai_llm_service where tenant_id = ? and is_deleted = 0",
                AiVO.LlmServiceVO.class,
                pageNo,
                pageSize,
                List.of(tenantId)
        );
    }

    public AiVO.LlmServiceVO getLlmService(CurrentUser currentUser, Long id) {
        Long tenantId = currentTenantId(currentUser);
        AiEntitiesHelper.LlmServiceRecord record = requireLlmService(tenantId, id);
        return toLlmServiceVO(record);
    }

    @Transactional
    public AiVO.LlmServiceVO createLlmService(CurrentUser currentUser, AiDTO.LlmServiceUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        validateLlmServiceCodeAvailable(tenantId, request.getCode().trim(), null);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        insert into ai_llm_service (
                            tenant_id, provider, code, title, base_url, api_key_encrypted, default_model, enabled,
                            timeout_ms, temperature, max_tokens, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                tenantId,
                request.getProvider().trim(),
                request.getCode().trim(),
                request.getTitle().trim(),
                cleanNullable(request.getBaseUrl()),
                aiSecretCryptoService.encrypt(cleanNullable(request.getApiKey())),
                cleanNullable(request.getDefaultModel()),
                request.getEnabled() == null || request.getEnabled() ? 1 : 0,
                request.getTimeoutMs() == null ? 60000 : request.getTimeoutMs(),
                request.getTemperature(),
                request.getMaxTokens(),
                now,
                now
        );
        Long serviceId = queryLlmServiceId(tenantId, request.getCode().trim());
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "llm-create", "CREATE", "SUCCESS", "创建 LLM 服务: " + request.getCode());
        return getLlmService(currentUser, serviceId);
    }

    @Transactional
    public AiVO.LlmServiceVO updateLlmService(CurrentUser currentUser, Long id, AiDTO.LlmServiceUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        AiEntitiesHelper.LlmServiceRecord existing = requireLlmService(tenantId, id);
        validateLlmServiceCodeAvailable(tenantId, request.getCode().trim(), id);
        String encryptedApiKey = StringUtils.hasText(request.getApiKey())
                ? aiSecretCryptoService.encrypt(request.getApiKey().trim())
                : existing.getApiKeyEncrypted();
        jdbcTemplate.update(
                """
                        update ai_llm_service
                        set provider = ?, code = ?, title = ?, base_url = ?, api_key_encrypted = ?, default_model = ?,
                            enabled = ?, timeout_ms = ?, temperature = ?, max_tokens = ?, update_time = ?
                        where tenant_id = ? and id = ? and is_deleted = 0
                        """,
                request.getProvider().trim(),
                request.getCode().trim(),
                request.getTitle().trim(),
                cleanNullable(request.getBaseUrl()),
                encryptedApiKey,
                cleanNullable(request.getDefaultModel()),
                request.getEnabled() == null || request.getEnabled() ? 1 : 0,
                request.getTimeoutMs() == null ? 60000 : request.getTimeoutMs(),
                request.getTemperature(),
                request.getMaxTokens(),
                LocalDateTime.now(),
                tenantId,
                id
        );
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "llm-update", "UPDATE", "SUCCESS", "更新 LLM 服务: " + request.getCode());
        return getLlmService(currentUser, id);
    }

    @Transactional
    public boolean deleteLlmService(CurrentUser currentUser, Long id) {
        Long tenantId = currentTenantId(currentUser);
        AiEntitiesHelper.LlmServiceRecord service = requireLlmService(tenantId, id);
        Integer refCount = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from ai_employee
                        where tenant_id = ?
                          and default_llm_service_id = ?
                          and is_deleted = 0
                        """,
                Integer.class,
                tenantId,
                id
        );
        if (refCount != null && refCount > 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 服务已被数字员工引用，无法删除");
        }
        jdbcTemplate.update(
                """
                        update ai_llm_service
                        set is_deleted = 1, update_time = ?
                        where tenant_id = ? and id = ? and is_deleted = 0
                        """,
                LocalDateTime.now(),
                tenantId,
                id
        );
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "llm-delete", "DELETE", "SUCCESS", "删除 LLM 服务: " + service.getCode());
        return true;
    }

    @Transactional
    public boolean updateLlmServiceEnabled(CurrentUser currentUser, Long id, boolean enabled) {
        Long tenantId = currentTenantId(currentUser);
        AiEntitiesHelper.LlmServiceRecord service = requireLlmService(tenantId, id);
        jdbcTemplate.update(
                """
                        update ai_llm_service
                        set enabled = ?, update_time = ?
                        where tenant_id = ? and id = ? and is_deleted = 0
                        """,
                enabled ? 1 : 0,
                LocalDateTime.now(),
                tenantId,
                id
        );
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "llm-enabled", "UPDATE", "SUCCESS", "更新 LLM 服务状态: " + service.getCode() + " -> " + enabled);
        return true;
    }

    public List<AiVO.SkillVO> listSkills(CurrentUser currentUser) {
        currentTenantId(currentUser);
        return jdbcTemplate.query(
                """
                        select id, skill_code as skillCode, skill_name as skillName, category, description, risk_level as riskLevel,
                               read_only as readOnly, need_confirm as needConfirm, enabled,
                               create_time as createTime, update_time as updateTime
                        from ai_skill
                        where is_deleted = 0
                          and enabled = 1
                        order by category asc, skill_code asc
                        """,
                new BeanPropertyRowMapper<>(AiVO.SkillVO.class)
        );
    }

    public List<AiVO.EmployeeSkillVO> getEmployeeSkills(CurrentUser currentUser, Long employeeId) {
        Long tenantId = currentTenantId(currentUser);
        requireEmployee(tenantId, employeeId);
        return listEmployeeSkills(tenantId, employeeId);
    }

    public AiVO.EmployeeVO getAssistantEmployee(CurrentUser currentUser) {
        Long tenantId = currentTenantId(currentUser);
        AiVO.EmployeeVO employee = jdbcTemplate.query(
                """
                        select e.id, e.tenant_id as tenantId, e.username, e.nickname, e.position, e.avatar_key as avatarKey,
                               e.description, e.greeting, e.default_llm_service_id as defaultLlmServiceId,
                               e.enabled, e.sort_order as sortOrder, e.create_time as createTime, e.update_time as updateTime,
                               s.title as defaultLlmServiceTitle
                        from ai_employee e
                        left join ai_llm_service s
                          on s.id = e.default_llm_service_id
                         and s.tenant_id = e.tenant_id
                         and s.is_deleted = 0
                        where e.tenant_id = ?
                          and e.is_deleted = 0
                          and e.enabled = 1
                        order by e.sort_order asc, e.id desc
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(AiVO.EmployeeVO.class),
                tenantId
        ).stream().findFirst().orElse(null);
        return employee;
    }

    public PageResponse<AiVO.ConversationVO> listConversations(CurrentUser currentUser, Long employeeId, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        requireEmployee(tenantId, employeeId);
        return pageQuery(
                """
                        select c.id, c.tenant_id as tenantId, c.employee_id as employeeId,
                               coalesce(e.nickname, e.username) as employeeName,
                               c.conversation_code as conversationCode, c.title, c.status,
                               c.is_pinned as pinned,
                               (
                                   select m.content
                                   from ai_message m
                                   where m.tenant_id = c.tenant_id
                                     and m.conversation_id = c.id
                                     and m.is_deleted = 0
                                   order by m.id desc
                                   limit 1
                               ) as preview,
                               c.latest_message_at as latestMessageAt,
                               c.create_time as createTime,
                               c.update_time as updateTime
                        from ai_conversation c
                        left join ai_employee e
                          on e.id = c.employee_id
                         and e.tenant_id = c.tenant_id
                         and e.is_deleted = 0
                        where c.tenant_id = ?
                          and c.employee_id = ?
                          and c.is_deleted = 0
                        order by c.is_pinned desc, coalesce(c.latest_message_at, c.create_time) desc, c.id desc
                        """,
                """
                        select count(1)
                        from ai_conversation c
                        where c.tenant_id = ?
                          and c.employee_id = ?
                          and c.is_deleted = 0
                        """,
                AiVO.ConversationVO.class,
                pageNo,
                pageSize,
                List.of(tenantId, employeeId)
        );
    }

    public List<AiVO.MessageVO> listConversationMessages(CurrentUser currentUser, Long conversationId) {
        Long tenantId = currentTenantId(currentUser);
        requireConversation(tenantId, conversationId);
        List<AiVO.MessageVO> messages = jdbcTemplate.query(
                """
                        select id, conversation_id as conversationId, role, content, create_time as createTime
                        from ai_message
                        where tenant_id = ?
                          and conversation_id = ?
                          and is_deleted = 0
                        order by id asc
                        """,
                new BeanPropertyRowMapper<>(AiVO.MessageVO.class),
                tenantId,
                conversationId
        );
        Map<Long, List<AiVO.MessageAttachmentVO>> attachmentMap = loadMessageAttachments(tenantId, conversationId);
        for (AiVO.MessageVO message : messages) {
            message.setAttachments(attachmentMap.getOrDefault(message.getId(), List.of()));
        }
        return messages;
    }

    @Transactional
    public boolean updateConversation(CurrentUser currentUser, Long conversationId, AiDTO.ConversationUpdateRequest request) {
        Long tenantId = currentTenantId(currentUser);
        AiVO.ConversationVO conversation = requireConversation(tenantId, conversationId);
        String title = request == null ? null : request.getTitle();
        Boolean pinned = request == null ? null : request.getPinned();
        jdbcTemplate.update(
                """
                        update ai_conversation
                        set title = coalesce(?, title),
                            is_pinned = coalesce(?, is_pinned),
                            update_time = ?
                        where tenant_id = ? and id = ? and is_deleted = 0
                        """,
                StringUtils.hasText(title) ? title.trim() : null,
                pinned == null ? null : (pinned ? 1 : 0),
                LocalDateTime.now(),
                tenantId,
                conversationId
        );
        operationAuditService.log(
                tenantId,
                currentUser.getUserId(),
                currentUser.getUsername(),
                "ai",
                "conversation-update",
                "UPDATE",
                "SUCCESS",
                "更新会话: " + conversation.getConversationCode()
        );
        return true;
    }

    @Transactional
    public boolean deleteConversation(CurrentUser currentUser, Long conversationId) {
        Long tenantId = currentTenantId(currentUser);
        AiVO.ConversationVO conversation = requireConversation(tenantId, conversationId);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        update ai_conversation
                        set is_deleted = 1, update_time = ?
                        where tenant_id = ? and id = ? and is_deleted = 0
                        """,
                now,
                tenantId,
                conversationId
        );
        jdbcTemplate.update(
                """
                        update ai_message
                        set is_deleted = 1, update_time = ?
                        where tenant_id = ? and conversation_id = ? and is_deleted = 0
                        """,
                now,
                tenantId,
                conversationId
        );
        jdbcTemplate.update(
                """
                        update ai_message_attachment
                        set is_deleted = 1, update_time = ?
                        where tenant_id = ? and conversation_id = ? and is_deleted = 0
                        """,
                now,
                tenantId,
                conversationId
        );
        jdbcTemplate.update(
                """
                        update ai_conversation_share
                        set is_deleted = 1, update_time = ?
                        where tenant_id = ? and conversation_id = ? and is_deleted = 0
                        """,
                now,
                tenantId,
                conversationId
        );
        operationAuditService.log(
                tenantId,
                currentUser.getUserId(),
                currentUser.getUsername(),
                "ai",
                "conversation-delete",
                "DELETE",
                "SUCCESS",
                "删除会话: " + conversation.getConversationCode()
        );
        return true;
    }

    @Transactional
    public AiVO.ConversationShareVO createConversationShare(CurrentUser currentUser, Long conversationId) {
        Long tenantId = currentTenantId(currentUser);
        AiVO.ConversationVO conversation = requireConversation(tenantId, conversationId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(30);
        String shareToken = "share_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(
                """
                        insert into ai_conversation_share (
                            tenant_id, conversation_id, share_token, title, status, expires_at, created_by, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, 'ACTIVE', ?, ?, 0, ?, ?)
                        """,
                tenantId,
                conversationId,
                shareToken,
                StringUtils.hasText(conversation.getTitle()) ? conversation.getTitle().trim() : conversation.getPreview(),
                expiresAt,
                currentUser.getUserId(),
                now,
                now
        );
        AiVO.ConversationShareVO share = new AiVO.ConversationShareVO();
        share.setShareToken(shareToken);
        share.setConversationId(conversationId);
        share.setShareTitle(StringUtils.hasText(conversation.getTitle()) ? conversation.getTitle().trim() : conversation.getPreview());
        share.setExpiresAt(expiresAt);
        share.setCreateTime(now);
        operationAuditService.log(
                tenantId,
                currentUser.getUserId(),
                currentUser.getUsername(),
                "ai",
                "conversation-share",
                "CREATE",
                "SUCCESS",
                "创建会话分享: " + conversation.getConversationCode()
        );
        return share;
    }

    public AiVO.ConversationShareDetailVO getConversationShare(CurrentUser currentUser, String shareToken) {
        Long tenantId = currentTenantId(currentUser);
        AiVO.ConversationShareVO share = requireConversationShare(tenantId, shareToken);
        AiVO.ConversationVO conversation = requireConversation(tenantId, share.getConversationId());
        AiVO.ConversationShareDetailVO detail = new AiVO.ConversationShareDetailVO();
        detail.setShare(share);
        detail.setConversation(conversation);
        detail.setMessages(listConversationMessages(currentUser, share.getConversationId()));
        return detail;
    }

    public AiVO.ConversationExportVO exportConversation(CurrentUser currentUser, Long conversationId, String format) {
        Long tenantId = currentTenantId(currentUser);
        AiVO.ConversationVO conversation = requireConversation(tenantId, conversationId);
        List<AiVO.MessageVO> messages = listConversationMessages(currentUser, conversationId);
        String normalizedFormat = normalizeExportFormat(format);
        String content = buildConversationExportContent(conversation, messages, normalizedFormat);
        AiVO.ConversationExportVO export = new AiVO.ConversationExportVO();
        export.setConversationId(conversationId);
        export.setTitle(StringUtils.hasText(conversation.getTitle()) ? conversation.getTitle().trim() : "会话");
        export.setFormat(normalizedFormat);
        export.setFileName(buildExportFileName(export.getTitle(), normalizedFormat));
        export.setMimeType("markdown".equals(normalizedFormat) ? "text/markdown;charset=utf-8" : "text/plain;charset=utf-8");
        export.setContent(content);
        return export;
    }

    @Transactional
    public boolean updateEmployeeSkills(CurrentUser currentUser, Long employeeId, AiDTO.EmployeeSkillsUpdateRequest request) {
        Long tenantId = currentTenantId(currentUser);
        requireEmployee(tenantId, employeeId);
        replaceEmployeeSkills(tenantId, employeeId, request.getSkills());
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "employee-skills", "UPDATE", "SUCCESS", "更新数字员工技能: " + employeeId);
        return true;
    }

    public AiVO.ChatResponseVO chat(CurrentUser currentUser, AiDTO.ChatRequest request) {
        return aiEmployeeRuntimeService.chat(currentUser, request);
    }

    public AiVO.ChatResponseVO streamChat(CurrentUser currentUser, AiDTO.ChatRequest request, java.util.function.Consumer<AiVO.ChatStreamEventVO> onEvent) {
        return aiEmployeeRuntimeService.streamChat(currentUser, request, onEvent);
    }

    public AiVO.PromptTemplateVO defaultTemplate() {
        return employeeTemplate(null);
    }

    private void replaceEmployeeSkills(Long tenantId, Long employeeId, List<AiDTO.EmployeeSkillItem> items) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        update ai_employee_skill
                        set is_deleted = 1, update_time = ?
                        where tenant_id = ? and employee_id = ? and is_deleted = 0
                        """,
                now,
                tenantId,
                employeeId
        );
        if (CollectionUtils.isEmpty(items)) {
            return;
        }
        Map<String, AiEntitiesHelper.SkillRecord> skillMap = loadSkillRecords(items.stream().map(AiDTO.EmployeeSkillItem::getSkillCode).toList());
        for (AiDTO.EmployeeSkillItem item : items) {
            if (item == null || !StringUtils.hasText(item.getSkillCode())) {
                continue;
            }
            AiEntitiesHelper.SkillRecord skill = skillMap.get(item.getSkillCode().trim());
            if (skill == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "技能不存在: " + item.getSkillCode());
            }
            jdbcTemplate.update(
                    """
                            insert into ai_employee_skill (
                                tenant_id, employee_id, skill_code, permission_mode, is_deleted, create_time, update_time
                            ) values (?, ?, ?, ?, 0, ?, ?)
                            on duplicate key update
                                permission_mode = values(permission_mode),
                                is_deleted = 0,
                                update_time = values(update_time)
                            """,
                    tenantId,
                    employeeId,
                    skill.getSkillCode(),
                    normalizePermissionMode(item.getPermissionMode(), skill.getReadOnly()),
                    now,
                    now
            );
        }
    }

    private List<AiVO.EmployeeSkillVO> listEmployeeSkills(Long tenantId, Long employeeId) {
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

    private Map<String, AiEntitiesHelper.SkillRecord> loadSkillRecords(List<String> skillCodes) {
        if (CollectionUtils.isEmpty(skillCodes)) {
            return Map.of();
        }
        List<String> normalizedCodes = skillCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalizedCodes.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", normalizedCodes.stream().map(code -> "?").toList());
        List<AiEntitiesHelper.SkillRecord> records = jdbcTemplate.query(
                """
                        select skill_code as skillCode, read_only as readOnly
                        from ai_skill
                        where is_deleted = 0
                          and skill_code in (%s)
                        """.formatted(placeholders),
                new BeanPropertyRowMapper<>(AiEntitiesHelper.SkillRecord.class),
                normalizedCodes.toArray()
        );
        Map<String, AiEntitiesHelper.SkillRecord> result = new HashMap<>();
        for (AiEntitiesHelper.SkillRecord record : records) {
            result.put(record.getSkillCode(), record);
        }
        return result;
    }

    private AiVO.EmployeeDetailVO queryEmployeeDetail(Long tenantId, Long id) {
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
                id
        ).stream().findFirst().orElse(null);
        if (employee == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "数字员工不存在");
        }
        employee.setDefaultSystemPromptTemplate(DEFAULT_SYSTEM_PROMPT_TEMPLATE);
        return employee;
    }

    private Long queryEmployeeId(Long tenantId, String username) {
        return jdbcTemplate.queryForObject(
                """
                        select id
                        from ai_employee
                        where tenant_id = ?
                          and username = ?
                          and is_deleted = 0
                        order by id desc
                        limit 1
                        """,
                Long.class,
                tenantId,
                username
        );
    }

    private Long queryLlmServiceId(Long tenantId, String code) {
        return jdbcTemplate.queryForObject(
                """
                        select id
                        from ai_llm_service
                        where tenant_id = ?
                          and code = ?
                          and is_deleted = 0
                        order by id desc
                        limit 1
                        """,
                Long.class,
                tenantId,
                code
        );
    }

    private AiEntitiesHelper.LlmServiceRecord requireLlmService(Long tenantId, Long id) {
        AiEntitiesHelper.LlmServiceRecord service = jdbcTemplate.query(
                """
                        select id, tenant_id as tenantId, provider, code, title, base_url as baseUrl,
                               api_key_encrypted as apiKeyEncrypted, default_model as defaultModel,
                               enabled, timeout_ms as timeoutMs, temperature, max_tokens as maxTokens,
                               create_time as createTime, update_time as updateTime
                        from ai_llm_service
                        where tenant_id = ?
                          and id = ?
                          and is_deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(AiEntitiesHelper.LlmServiceRecord.class),
                tenantId,
                id
        ).stream().findFirst().orElse(null);
        if (service == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "LLM 服务不存在");
        }
        return service;
    }

    private AiVO.LlmServiceVO toLlmServiceVO(AiEntitiesHelper.LlmServiceRecord record) {
        AiVO.LlmServiceVO vo = new AiVO.LlmServiceVO();
        vo.setId(record.getId());
        vo.setTenantId(record.getTenantId());
        vo.setProvider(record.getProvider());
        vo.setCode(record.getCode());
        vo.setTitle(record.getTitle());
        vo.setBaseUrl(record.getBaseUrl());
        vo.setDefaultModel(record.getDefaultModel());
        vo.setEnabled(Boolean.TRUE.equals(record.getEnabled()));
        vo.setTimeoutMs(record.getTimeoutMs());
        vo.setTemperature(record.getTemperature());
        vo.setMaxTokens(record.getMaxTokens());
        vo.setApiKeyConfigured(StringUtils.hasText(record.getApiKeyEncrypted()));
        vo.setApiKeyMasked(StringUtils.hasText(record.getApiKeyEncrypted()) ? aiSecretCryptoService.mask(record.getApiKeyEncrypted()) : null);
        vo.setCreateTime(record.getCreateTime());
        vo.setUpdateTime(record.getUpdateTime());
        return vo;
    }

    private void validateDefaultLlmService(Long tenantId, Long defaultLlmServiceId) {
        if (defaultLlmServiceId == null) {
            return;
        }
        requireLlmService(tenantId, defaultLlmServiceId);
    }

    private void requireEmployee(Long tenantId, Long employeeId) {
        AiVO.EmployeeVO employee = jdbcTemplate.query(
                """
                        select id
                        from ai_employee
                        where tenant_id = ?
                          and id = ?
                          and is_deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                tenantId,
                employeeId
        ).stream().findFirst().map(id -> {
            AiVO.EmployeeVO employeeVO = new AiVO.EmployeeVO();
            employeeVO.setId(id);
            return employeeVO;
        }).orElse(null);
        if (employee == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "数字员工不存在");
        }
    }

    private AiVO.ConversationVO requireConversation(Long tenantId, Long conversationId) {
        AiVO.ConversationVO conversation = jdbcTemplate.query(
                """
                        select c.id,
                               c.tenant_id as tenantId,
                               c.employee_id as employeeId,
                               coalesce(e.nickname, e.username) as employeeName,
                               c.conversation_code as conversationCode,
                               c.title,
                               (
                                   select m.content
                                   from ai_message m
                                   where m.tenant_id = c.tenant_id
                                     and m.conversation_id = c.id
                                     and m.is_deleted = 0
                                   order by m.id desc
                                   limit 1
                               ) as preview,
                               c.status,
                               c.is_pinned as pinned,
                               c.latest_message_at as latestMessageAt,
                               c.create_time as createTime,
                               c.update_time as updateTime
                        from ai_conversation c
                        left join ai_employee e
                          on e.id = c.employee_id
                         and e.tenant_id = c.tenant_id
                         and e.is_deleted = 0
                        where c.tenant_id = ?
                          and c.id = ?
                          and c.is_deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(AiVO.ConversationVO.class),
                tenantId,
                conversationId
        ).stream().findFirst().orElse(null);
        if (conversation == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return conversation;
    }

    private AiVO.ConversationShareVO requireConversationShare(Long tenantId, String shareToken) {
        AiVO.ConversationShareVO share = jdbcTemplate.query(
                """
                        select share_token as shareToken, conversation_id as conversationId, title as shareTitle,
                               expires_at as expiresAt, create_time as createTime
                        from ai_conversation_share
                        where tenant_id = ?
                          and share_token = ?
                          and is_deleted = 0
                          and status = 'ACTIVE'
                          and (expires_at is null or expires_at >= now())
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(AiVO.ConversationShareVO.class),
                tenantId,
                shareToken
        ).stream().findFirst().orElse(null);
        if (share == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分享链接不存在或已失效");
        }
        return share;
    }

    private Map<Long, List<AiVO.MessageAttachmentVO>> loadMessageAttachments(Long tenantId, Long conversationId) {
        Map<Long, List<AiVO.MessageAttachmentVO>> attachmentMap = new LinkedHashMap<>();
        jdbcTemplate.query(
                """
                        select id,
                               file_id as fileId,
                               message_id as messageId,
                               original_file_name as originalFileName,
                               file_extension as fileExtension,
                               mime_type as mimeType,
                               file_size_bytes as fileSizeBytes,
                               concat(round(coalesce(file_size_bytes, 0) / 1024, 1), ' KB') as fileSizeLabel,
                               public_url as publicUrl,
                               preview_url as previewUrl,
                               download_url as downloadUrl,
                               preview_mode as previewMode
                        from ai_message_attachment
                        where tenant_id = ?
                          and conversation_id = ?
                          and is_deleted = 0
                        order by id asc
                        """,
                (rs, rowNum) -> {
                    AiVO.MessageAttachmentVO attachment = new AiVO.MessageAttachmentVO();
                    attachment.setId(rs.getLong("id"));
                    attachment.setFileId(rs.getLong("fileId"));
                    attachment.setOriginalFileName(rs.getString("originalFileName"));
                    attachment.setFileExtension(rs.getString("fileExtension"));
                    attachment.setMimeType(rs.getString("mimeType"));
                    attachment.setFileSizeBytes(rs.getObject("fileSizeBytes") == null ? null : rs.getLong("fileSizeBytes"));
                    attachment.setFileSizeLabel(rs.getString("fileSizeLabel"));
                    attachment.setPublicUrl(rs.getString("publicUrl"));
                    attachment.setPreviewUrl(rs.getString("previewUrl"));
                    attachment.setDownloadUrl(rs.getString("downloadUrl"));
                    attachment.setPreviewMode(rs.getString("previewMode"));
                    Long messageId = rs.getLong("messageId");
                    attachmentMap.computeIfAbsent(messageId, ignored -> new ArrayList<>()).add(attachment);
                    return attachment;
                },
                tenantId,
                conversationId
        );
        return attachmentMap;
    }

    private String normalizeExportFormat(String format) {
        if (!StringUtils.hasText(format)) {
            return "markdown";
        }
        String normalized = format.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "md", "markdown" -> "markdown";
            case "txt", "text" -> "text";
            default -> throw new BizException(ErrorCode.BIZ_ERROR, "不支持的导出格式");
        };
    }

    private String buildConversationExportContent(AiVO.ConversationVO conversation, List<AiVO.MessageVO> messages, String format) {
        StringBuilder builder = new StringBuilder();
        boolean markdown = "markdown".equals(format);
        String title = StringUtils.hasText(conversation.getTitle()) ? conversation.getTitle().trim() : "会话";
        if (markdown) {
            builder.append("# ").append(title).append("\n\n");
        } else {
            builder.append(title).append("\n");
        }
        if (StringUtils.hasText(conversation.getEmployeeName())) {
            builder.append(markdown ? "- " : "").append("AI 员工: ").append(conversation.getEmployeeName()).append("\n");
        }
        if (conversation.getLatestMessageAt() != null) {
            builder.append(markdown ? "- " : "").append("更新时间: ").append(conversation.getLatestMessageAt()).append("\n");
        }
        builder.append("\n");
        for (AiVO.MessageVO message : messages) {
            String role = "USER".equalsIgnoreCase(message.getRole()) ? "用户" : "AI";
            if (markdown) {
                builder.append("## ").append(role).append("\n\n");
            } else {
                builder.append(role).append(":\n");
            }
            if (StringUtils.hasText(message.getContent())) {
                builder.append(message.getContent().trim()).append("\n");
            }
            if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
                if (markdown) {
                    builder.append("\n附件:\n");
                    for (AiVO.MessageAttachmentVO attachment : message.getAttachments()) {
                        builder.append("- ").append(attachment.getOriginalFileName());
                        if (StringUtils.hasText(attachment.getDownloadUrl())) {
                            builder.append(" (").append(attachment.getDownloadUrl()).append(")");
                        }
                        builder.append("\n");
                    }
                } else {
                    builder.append("附件:\n");
                    for (AiVO.MessageAttachmentVO attachment : message.getAttachments()) {
                        builder.append("- ").append(attachment.getOriginalFileName());
                        if (StringUtils.hasText(attachment.getDownloadUrl())) {
                            builder.append(" (").append(attachment.getDownloadUrl()).append(")");
                        }
                        builder.append("\n");
                    }
                }
            }
            builder.append("\n");
        }
        return builder.toString().trim();
    }

    private String buildExportFileName(String title, String format) {
        String safeTitle = StringUtils.hasText(title) ? title.trim().replaceAll("[\\\\/:*?\"<>|]", "_") : "ai-conversation";
        return safeTitle + ("markdown".equals(format) ? ".md" : ".txt");
    }

    private <T> PageResponse<T> pageQuery(String selectSql, String countSql, Class<T> voClass, long pageNo, long pageSize, List<Object> params) {
        long safePageNo = pageNo <= 0 ? 1 : pageNo;
        long safePageSize = pageSize <= 0 ? 10 : pageSize;
        long offset = (safePageNo - 1) * safePageSize;
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize);
        queryParams.add(offset);
        List<T> records = jdbcTemplate.query(selectSql + " limit ? offset ?", new BeanPropertyRowMapper<>(voClass), queryParams.toArray());
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0 : total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    private void validateEmployeeUsernameAvailable(Long tenantId, String username, Long excludeId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from ai_employee
                        where tenant_id = ?
                          and username = ?
                          and is_deleted = 0
                          and (? is null or id <> ?)
                        """,
                Integer.class,
                tenantId,
                username,
                excludeId,
                excludeId
        );
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "用户名已存在");
        }
    }

    private void validateLlmServiceCodeAvailable(Long tenantId, String code, Long excludeId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from ai_llm_service
                        where tenant_id = ?
                          and code = ?
                          and is_deleted = 0
                          and (? is null or id <> ?)
                        """,
                Integer.class,
                tenantId,
                code,
                excludeId,
                excludeId
        );
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 服务标识已存在");
        }
    }

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getCurrentTenantId() == null) {
            throw new BizException(ErrorCode.TENANT_ERROR, "租户上下文无效");
        }
        return currentUser.getCurrentTenantId();
    }

    private String cleanNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizePermissionMode(String permissionMode, Boolean readOnly) {
        String normalized = StringUtils.hasText(permissionMode) ? permissionMode.trim().toLowerCase(Locale.ROOT) : null;
        if (!StringUtils.hasText(normalized)) {
            return Boolean.TRUE.equals(readOnly) ? "visit" : "allow";
        }
        if (!List.of("visit", "allow", "deny").contains(normalized)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "技能权限模式仅支持 visit / allow / deny");
        }
        return normalized;
    }

    private static final class AiEntitiesHelper {
        private AiEntitiesHelper() {
        }

        public static class SkillRecord {
            private String skillCode;
            private Boolean readOnly;

            public String getSkillCode() {
                return skillCode;
            }

            public void setSkillCode(String skillCode) {
                this.skillCode = skillCode;
            }

            public Boolean getReadOnly() {
                return readOnly;
            }

            public void setReadOnly(Boolean readOnly) {
                this.readOnly = readOnly;
            }
        }

        public static class LlmServiceRecord {
            private Long id;
            private Long tenantId;
            private String provider;
            private String code;
            private String title;
            private String baseUrl;
            private String apiKeyEncrypted;
            private String defaultModel;
            private Boolean enabled;
            private Integer timeoutMs;
            private java.math.BigDecimal temperature;
            private Integer maxTokens;
            private LocalDateTime createTime;
            private LocalDateTime updateTime;

            public Long getId() {
                return id;
            }

            public void setId(Long id) {
                this.id = id;
            }

            public Long getTenantId() {
                return tenantId;
            }

            public void setTenantId(Long tenantId) {
                this.tenantId = tenantId;
            }

            public String getProvider() {
                return provider;
            }

            public void setProvider(String provider) {
                this.provider = provider;
            }

            public String getCode() {
                return code;
            }

            public void setCode(String code) {
                this.code = code;
            }

            public String getTitle() {
                return title;
            }

            public void setTitle(String title) {
                this.title = title;
            }

            public String getBaseUrl() {
                return baseUrl;
            }

            public void setBaseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
            }

            public String getApiKeyEncrypted() {
                return apiKeyEncrypted;
            }

            public void setApiKeyEncrypted(String apiKeyEncrypted) {
                this.apiKeyEncrypted = apiKeyEncrypted;
            }

            public String getDefaultModel() {
                return defaultModel;
            }

            public void setDefaultModel(String defaultModel) {
                this.defaultModel = defaultModel;
            }

            public Boolean getEnabled() {
                return enabled;
            }

            public void setEnabled(Boolean enabled) {
                this.enabled = enabled;
            }

            public Integer getTimeoutMs() {
                return timeoutMs;
            }

            public void setTimeoutMs(Integer timeoutMs) {
                this.timeoutMs = timeoutMs;
            }

            public java.math.BigDecimal getTemperature() {
                return temperature;
            }

            public void setTemperature(java.math.BigDecimal temperature) {
                this.temperature = temperature;
            }

            public Integer getMaxTokens() {
                return maxTokens;
            }

            public void setMaxTokens(Integer maxTokens) {
                this.maxTokens = maxTokens;
            }

            public LocalDateTime getCreateTime() {
                return createTime;
            }

            public void setCreateTime(LocalDateTime createTime) {
                this.createTime = createTime;
            }

            public LocalDateTime getUpdateTime() {
                return updateTime;
            }

            public void setUpdateTime(LocalDateTime updateTime) {
                this.updateTime = updateTime;
            }
        }
    }
}
