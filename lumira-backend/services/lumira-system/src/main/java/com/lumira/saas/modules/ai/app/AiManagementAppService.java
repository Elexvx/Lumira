package com.lumira.saas.modules.ai.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.infrastructure.AiSecretCryptoService;
import com.lumira.saas.modules.ai.vo.AiVO;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import org.springframework.dao.EmptyResultDataAccessException;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

@Service
public class AiManagementAppService {

    private static final long MAX_PAGE_SIZE = 100L;
    private static final long GOVERNANCE_OVERVIEW_CACHE_TTL_MS = 15_000L;
    private static final int GOVERNANCE_OVERVIEW_CACHE_MAX_ENTRIES = 2048;
    private static final String GOVERNANCE_OVERVIEW_CACHE_KEY = "global";
    private static final String PERMISSION_AI_VIEW = "ai:view";
    private static final String PERMISSION_AI_CHAT_SEND = "ai:chat:send";
    private static final String PERMISSION_AI_EMPLOYEE_CREATE = "ai:employee:create";
    private static final String PERMISSION_AI_EMPLOYEE_UPDATE = "ai:employee:update";
    private static final String PERMISSION_AI_EMPLOYEE_DELETE = "ai:employee:delete";
    private static final String PERMISSION_AI_EMPLOYEE_STATUS = "ai:employee:status";
    private static final String PERMISSION_AI_EMPLOYEE_SKILLS = "ai:employee:skills";
    private static final String PERMISSION_AI_LLM_CREATE = "ai:llm:create";
    private static final String PERMISSION_AI_LLM_UPDATE = "ai:llm:update";
    private static final String PERMISSION_AI_LLM_DELETE = "ai:llm:delete";
    private static final String PERMISSION_AI_LLM_STATUS = "ai:llm:status";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final Executor BLOCKING_IO_EXECUTOR = command -> Thread.ofVirtual().start(command);

    private static final String DEFAULT_SYSTEM_PROMPT_TEMPLATE = """
            你是 Lumira SaaS 平台的数字员工助手。
            你的目标是基于当前平台授权范围，稳妥、专业、清晰地完成用户交办的任务。
            请遵守以下规则：
            1. 只使用已授权的工具和知识库，不越权访问或编造系统数据。
            2. 涉及新增、修改、删除、启停、导出等高风险操作时，先说明影响并等待确认。
            3. 当工具返回权限不足、数据为空或系统不可用时，如实说明限制。
            4. 回复保持简洁，可执行，并优先给出下一步建议。
            """;

    private final MyBatisQueryOperations jdbcTemplate;
    private final OperationAuditService operationAuditService;
    private final AiSecretCryptoService aiSecretCryptoService;
    private final AiEmployeeRuntimeService aiEmployeeRuntimeService;
    private final AiChatModelFactory aiChatModelFactory;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final AiAssistantEmployeeResolver aiAssistantEmployeeResolver;
    private final Cache<String, AiVO.GovernanceOverviewVO> governanceOverviewCache;
    private final Cache<String, CompletableFuture<AiVO.GovernanceOverviewVO>> governanceOverviewLoadInFlight;
    private final boolean enforceTrustedUserResolution;

    @Autowired
    public AiManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            OperationAuditService operationAuditService,
            AiSecretCryptoService aiSecretCryptoService,
            AiEmployeeRuntimeService aiEmployeeRuntimeService,
            AiChatModelFactory aiChatModelFactory,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                jdbcTemplate,
                operationAuditService,
                aiSecretCryptoService,
                aiEmployeeRuntimeService,
                aiChatModelFactory,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                true
        );
    }

    private AiManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            OperationAuditService operationAuditService,
            AiSecretCryptoService aiSecretCryptoService,
            AiEmployeeRuntimeService aiEmployeeRuntimeService,
            AiChatModelFactory aiChatModelFactory,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.operationAuditService = operationAuditService;
        this.aiSecretCryptoService = aiSecretCryptoService;
        this.aiEmployeeRuntimeService = aiEmployeeRuntimeService;
        this.aiChatModelFactory = aiChatModelFactory;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.aiAssistantEmployeeResolver = new AiAssistantEmployeeResolver(jdbcTemplate);
        this.governanceOverviewCache = CacheBuilder.newBuilder()
                .maximumSize(GOVERNANCE_OVERVIEW_CACHE_MAX_ENTRIES)
                .expireAfterWrite(GOVERNANCE_OVERVIEW_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
        this.governanceOverviewLoadInFlight = CacheBuilder.newBuilder()
                .maximumSize(GOVERNANCE_OVERVIEW_CACHE_MAX_ENTRIES)
                .expireAfterWrite(GOVERNANCE_OVERVIEW_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    AiManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            OperationAuditService operationAuditService,
            AiSecretCryptoService aiSecretCryptoService,
            AiEmployeeRuntimeService aiEmployeeRuntimeService,
            AiChatModelFactory aiChatModelFactory
    ) {
        this(jdbcTemplate, operationAuditService, aiSecretCryptoService, aiEmployeeRuntimeService, aiChatModelFactory, null, null, null, false);
    }

    AiManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            OperationAuditService operationAuditService,
            AiSecretCryptoService aiSecretCryptoService,
            AiEmployeeRuntimeService aiEmployeeRuntimeService,
            AiChatModelFactory aiChatModelFactory,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(jdbcTemplate, operationAuditService, aiSecretCryptoService, aiEmployeeRuntimeService, aiChatModelFactory, permissionSnapshotService, null, null, false);
    }

    AiManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            OperationAuditService operationAuditService,
            AiSecretCryptoService aiSecretCryptoService,
            AiEmployeeRuntimeService aiEmployeeRuntimeService,
            AiChatModelFactory aiChatModelFactory,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(jdbcTemplate, operationAuditService, aiSecretCryptoService, aiEmployeeRuntimeService, aiChatModelFactory, permissionSnapshotService, null, sessionAuthenticationService, false);
    }

    public PageResponse<AiVO.EmployeeVO> listEmployees(CurrentUser currentUser, long pageNo, long pageSize) {
        requireViewPermission(currentUser);
        return pageQuery(
                """
                        select e.id, e.username, e.nickname, e.position, e.avatar_key as avatarKey,
                               e.description, e.greeting, e.default_llm_service_id as defaultLlmServiceId,
                               e.enabled, e.sort_order as sortOrder, e.create_time as createTime, e.update_time as updateTime,
                               s.title as defaultLlmServiceTitle
                        from ai_employee e
                        left join ai_llm_service s
                          on s.id = e.default_llm_service_id
                         and s.is_deleted = 0
                        where e.is_deleted = 0
                        order by e.sort_order asc, e.id desc
                        """,
                "select count(1) from ai_employee e where e.is_deleted = 0",
                AiVO.EmployeeVO.class,
                pageNo,
                pageSize,
                List.of()
        );
    }

    public AiVO.GovernanceOverviewVO governanceOverview(CurrentUser currentUser) {
        requireViewPermission(currentUser);
        AiVO.GovernanceOverviewVO cached = governanceOverviewCache.getIfPresent(GOVERNANCE_OVERVIEW_CACHE_KEY);
        if (cached != null) {
            return copyGovernanceOverview(cached);
        }
        return loadGovernanceOverview();
    }

    private AiVO.GovernanceOverviewVO loadGovernanceOverview() {
        try {
            CompletableFuture<AiVO.GovernanceOverviewVO> future = governanceOverviewLoadInFlight.get(
                    GOVERNANCE_OVERVIEW_CACHE_KEY,
                    () -> CompletableFuture.completedFuture(loadGovernanceOverviewFresh())
            );
            AiVO.GovernanceOverviewVO overview = future.join();
            governanceOverviewLoadInFlight.invalidate(GOVERNANCE_OVERVIEW_CACHE_KEY);
            return copyGovernanceOverview(overview);
        } catch (ExecutionException ex) {
            governanceOverviewLoadInFlight.invalidate(GOVERNANCE_OVERVIEW_CACHE_KEY);
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to load governance overview", cause);
        } catch (RuntimeException ex) {
            governanceOverviewLoadInFlight.invalidate(GOVERNANCE_OVERVIEW_CACHE_KEY);
            throw ex;
        }
    }

    private AiVO.GovernanceOverviewVO loadGovernanceOverviewFresh() {
        CompletableFuture<Map<String, Object>> employeeStatsFuture = CompletableFuture.supplyAsync(() -> querySingleRow("""
                select count(1) as employeeCount,
                       coalesce(sum(case when enabled = 1 then 1 else 0 end), 0) as enabledEmployeeCount
                from ai_employee
                where is_deleted = 0
                """), BLOCKING_IO_EXECUTOR);
        CompletableFuture<Map<String, Object>> llmServiceStatsFuture = CompletableFuture.supplyAsync(() -> querySingleRow("""
                select count(1) as llmServiceCount,
                       coalesce(sum(case when enabled = 1 then 1 else 0 end), 0) as enabledLlmServiceCount,
                       coalesce(sum(case when api_key_encrypted is null or api_key_encrypted = '' then 1 else 0 end), 0) as missingApiKeyServiceCount
                from ai_llm_service
                where is_deleted = 0
                """), BLOCKING_IO_EXECUTOR);
        CompletableFuture<Map<String, Object>> skillStatsFuture = CompletableFuture.supplyAsync(() -> querySingleRow("""
                select count(1) as skillCount,
                       coalesce(sum(case when risk_level = 'HIGH' then 1 else 0 end), 0) as highRiskSkillCount,
                       coalesce(sum(case when need_confirm = 1 then 1 else 0 end), 0) as confirmationRequiredSkillCount
                from ai_skill
                where enabled = 1 and is_deleted = 0
                """), BLOCKING_IO_EXECUTOR);
        CompletableFuture<Long> highRiskAllowedBindingCountFuture = CompletableFuture.supplyAsync(() -> count("""
                select count(1)
                from ai_employee_skill es
                join ai_skill s on s.skill_code = es.skill_code and s.is_deleted = 0
                where es.is_deleted = 0
                  and es.permission_mode = 'allow'
                  and s.risk_level = 'HIGH'
                """), BLOCKING_IO_EXECUTOR);

        AiVO.GovernanceOverviewVO overview = new AiVO.GovernanceOverviewVO();
        Map<String, Object> employeeStats = employeeStatsFuture.join();
        overview.setEmployeeCount(longValue(employeeStats.get("employeeCount")));
        overview.setEnabledEmployeeCount(longValue(employeeStats.get("enabledEmployeeCount")));

        Map<String, Object> llmServiceStats = llmServiceStatsFuture.join();
        overview.setLlmServiceCount(longValue(llmServiceStats.get("llmServiceCount")));
        overview.setEnabledLlmServiceCount(longValue(llmServiceStats.get("enabledLlmServiceCount")));
        overview.setMissingApiKeyServiceCount(longValue(llmServiceStats.get("missingApiKeyServiceCount")));

        Map<String, Object> skillStats = skillStatsFuture.join();
        overview.setSkillCount(longValue(skillStats.get("skillCount")));
        overview.setHighRiskSkillCount(longValue(skillStats.get("highRiskSkillCount")));
        overview.setConfirmationRequiredSkillCount(longValue(skillStats.get("confirmationRequiredSkillCount")));

        overview.setHighRiskAllowedBindingCount(highRiskAllowedBindingCountFuture.join());
        overview.setSampledAt(LocalDateTime.now());
        governanceOverviewCache.put(GOVERNANCE_OVERVIEW_CACHE_KEY, copyGovernanceOverview(overview));
        return overview;
    }

    public AiVO.EmployeeDetailVO getEmployee(CurrentUser currentUser, Long id) {
        requireViewPermission(currentUser);
        AiVO.EmployeeDetailVO employee = queryEmployeeDetail(id);
        employee.setDefaultSystemPromptTemplate(DEFAULT_SYSTEM_PROMPT_TEMPLATE);
        return employee;
    }

    @Transactional
    public AiVO.EmployeeDetailVO createEmployee(CurrentUser currentUser, AiDTO.EmployeeUpsertRequest request) {
        requireEmployeeCreatePermission(currentUser);
        validateEmployeeUsernameAvailable(request.getUsername().trim(), null);
        validateDefaultLlmService(request.getDefaultLlmServiceId());
        LocalDateTime now = LocalDateTime.now();
        String systemPrompt = cleanNullable(request.getSystemPrompt());
        int inserted = jdbcTemplate.update(
                """
                        insert into ai_employee (
                            username, nickname, position, avatar_key, description, greeting,
                            system_prompt, default_llm_service_id, enabled, sort_order, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, 0, ?, ?)
                        """,
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
        requireAiWrite(inserted, "AI employee changed, please retry");
        Long employeeId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        invalidateGovernanceOverviewCache();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "ai", "employee-create", "CREATE", "SUCCESS", "创建数字员工: " + request.getUsername());
        return getEmployee(currentUser, employeeId);
    }

    @Transactional
    public AiVO.EmployeeDetailVO updateEmployee(CurrentUser currentUser, Long id, AiDTO.EmployeeUpsertRequest request) {
        requireEmployeeUpdatePermission(currentUser);
        AiVO.EmployeeDetailVO existing = queryEmployeeDetail(id);
        validateEmployeeUsernameAvailable(request.getUsername().trim(), id);
        validateDefaultLlmService(request.getDefaultLlmServiceId());
        int updated = jdbcTemplate.update(
                """
                        update ai_employee
                        set username = ?, nickname = ?, position = ?, avatar_key = ?, description = ?, greeting = ?,
                            system_prompt = ?, default_llm_service_id = ?, sort_order = ?, update_time = ?
                        where id = ? and username = ? and enabled = ? and is_deleted = 0
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
                id,
                existing.getUsername(),
                Boolean.TRUE.equals(existing.getEnabled()) ? 1 : 0
        );
        requireAiWrite(updated, "AI employee changed, please retry");
        invalidateGovernanceOverviewCache();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "ai", "employee-update", "UPDATE", "SUCCESS", "更新数字员工: " + request.getUsername());
        return getEmployee(currentUser, id);
    }

    @Transactional
    public boolean deleteEmployee(CurrentUser currentUser, Long id) {
        requireEmployeeDeletePermission(currentUser);
        AiVO.EmployeeDetailVO existing = queryEmployeeDetail(id);
        LocalDateTime now = LocalDateTime.now();
        int employeeDeleted = jdbcTemplate.update(
                """
                        update ai_employee
                        set is_deleted = 1, update_time = ?
                        where id = ? and username = ? and enabled = ? and is_deleted = 0
                        """,
                now,
                id,
                existing.getUsername(),
                Boolean.TRUE.equals(existing.getEnabled()) ? 1 : 0
        );
        requireAiWrite(employeeDeleted, "AI employee changed, please retry");
        jdbcTemplate.update(
                """
                        update ai_employee_skill
                        set is_deleted = 1, update_time = ?
                        where employee_id in (
                            select id
                            from ai_employee
                            where id = ? and username = ? and is_deleted = 1
                        )
                          and is_deleted = 0
                        """,
                now,
                id,
                existing.getUsername()
        );
        invalidateGovernanceOverviewCache();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "ai", "employee-delete", "DELETE", "SUCCESS", "删除数字员工: " + id);
        return true;
    }

    @Transactional
    public boolean updateEmployeeEnabled(CurrentUser currentUser, Long id, boolean enabled) {
        requireEmployeeStatusPermission(currentUser);
        AiVO.EmployeeDetailVO existing = queryEmployeeDetail(id);
        int updated = jdbcTemplate.update(
                """
                        update ai_employee
                        set enabled = ?, update_time = ?
                        where id = ? and username = ? and enabled = ? and is_deleted = 0
                        """,
                enabled ? 1 : 0,
                LocalDateTime.now(),
                id,
                existing.getUsername(),
                Boolean.TRUE.equals(existing.getEnabled()) ? 1 : 0
        );
        requireAiWrite(updated, "AI employee changed, please retry");
        invalidateGovernanceOverviewCache();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "ai", "employee-enabled", "UPDATE", "SUCCESS", "更新数字员工状态: " + id + " -> " + enabled);
        return true;
    }

    public AiVO.PromptTemplateVO employeeTemplate(CurrentUser currentUser) {
        requireViewPermission(currentUser);
        return buildPromptTemplate();
    }

    private AiVO.PromptTemplateVO buildPromptTemplate() {
        AiVO.PromptTemplateVO template = new AiVO.PromptTemplateVO();
        template.setDefaultSystemPromptTemplate(DEFAULT_SYSTEM_PROMPT_TEMPLATE);
        return template;
    }

    public List<AiVO.EmployeeCapabilityVO> getEmployeeCapabilities(CurrentUser currentUser, Long employeeId) {
        requireViewPermission(currentUser);
        requireEmployee(employeeId);
        return listEmployeeCapabilities(employeeId);
    }

    @Transactional
    public boolean updateEmployeeCapabilities(CurrentUser currentUser, Long employeeId, AiDTO.EmployeeCapabilitiesUpdateRequest request) {
        requireEmployeeSkillsPermission(currentUser);
        AiVO.EmployeeDetailVO existing = queryEmployeeDetail(employeeId);
        replaceEmployeeCapabilities(existing, request == null ? List.of() : request.getCapabilities());
        invalidateGovernanceOverviewCache();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "ai", "employee-capabilities", "UPDATE", "SUCCESS", "更新数字员工技能权限: " + employeeId);
        return true;
    }

    public PageResponse<AiVO.LlmServiceVO> listLlmServices(CurrentUser currentUser, long pageNo, long pageSize) {
        requireViewPermission(currentUser);
        return pageQuery(
                """
                        select id, provider, code, title, base_url as baseUrl,
                               default_model as defaultModel, enabled, timeout_ms as timeoutMs, temperature,
                               max_tokens as maxTokens,
                               case when api_key_encrypted is null or api_key_encrypted = '' then 0 else 1 end as apiKeyConfigured,
                               case when api_key_encrypted is null or api_key_encrypted = '' then null else '******' end as apiKeyMasked,
                               create_time as createTime, update_time as updateTime
                        from ai_llm_service
                        where is_deleted = 0
                        order by id desc
                        """,
                "select count(1) from ai_llm_service where is_deleted = 0",
                AiVO.LlmServiceVO.class,
                pageNo,
                pageSize,
                List.of()
        );
    }

    public AiVO.LlmServiceVO getLlmService(CurrentUser currentUser, Long id) {
        requireViewPermission(currentUser);
        AiEntitiesHelper.LlmServiceRecord record = requireLlmService(id);
        return toLlmServiceVO(record);
    }

    @Transactional
    public AiVO.LlmServiceVO createLlmService(CurrentUser currentUser, AiDTO.LlmServiceUpsertRequest request) {
        requireLlmCreatePermission(currentUser);
        validateLlmServiceCodeAvailable(request.getCode().trim(), null);
        LocalDateTime now = LocalDateTime.now();
        int inserted = jdbcTemplate.update(
                """
                        insert into ai_llm_service (
                            provider, code, title, base_url, api_key_encrypted, default_model, enabled,
                            timeout_ms, temperature, max_tokens, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
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
        requireAiWrite(inserted, "AI LLM service changed, please retry");
        Long serviceId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        invalidateGovernanceOverviewCache();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "ai", "llm-create", "CREATE", "SUCCESS", "创建 LLM 服务: " + request.getCode());
        return getLlmService(currentUser, serviceId);
    }

    @Transactional
    public AiVO.LlmServiceVO updateLlmService(CurrentUser currentUser, Long id, AiDTO.LlmServiceUpsertRequest request) {
        requireLlmUpdatePermission(currentUser);
        AiEntitiesHelper.LlmServiceRecord existing = requireLlmService(id);
        validateLlmServiceCodeAvailable(request.getCode().trim(), id);
        String encryptedApiKey = StringUtils.hasText(request.getApiKey())
                ? aiSecretCryptoService.encrypt(request.getApiKey().trim())
                : existing.getApiKeyEncrypted();
        int updated = jdbcTemplate.update(
                """
                        update ai_llm_service
                        set provider = ?, code = ?, title = ?, base_url = ?, api_key_encrypted = ?, default_model = ?,
                            enabled = ?, timeout_ms = ?, temperature = ?, max_tokens = ?, update_time = ?
                        where id = ? and code = ? and provider = ? and enabled = ? and is_deleted = 0
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
                id,
                existing.getCode(),
                existing.getProvider(),
                Boolean.TRUE.equals(existing.getEnabled()) ? 1 : 0
        );
        requireAiWrite(updated, "AI LLM service changed, please retry");
        invalidateGovernanceOverviewCache();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "ai", "llm-update", "UPDATE", "SUCCESS", "更新 LLM 服务: " + request.getCode());
        return getLlmService(currentUser, id);
    }

    @Transactional
    public boolean deleteLlmService(CurrentUser currentUser, Long id) {
        requireLlmDeletePermission(currentUser);
        AiEntitiesHelper.LlmServiceRecord service = requireLlmService(id);
        Integer refCount = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from ai_employee
                        where default_llm_service_id = ?
                          and is_deleted = 0
                        """,
                Integer.class,
                id
        );
        if (refCount != null && refCount > 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM service is referenced by an AI employee and cannot be deleted");
        }
        int deleted = jdbcTemplate.update(
                """
                        update ai_llm_service
                        set is_deleted = 1, update_time = ?
                        where id = ? and code = ? and provider = ? and enabled = ? and is_deleted = 0
                        """,
                LocalDateTime.now(),
                id,
                service.getCode(),
                service.getProvider(),
                Boolean.TRUE.equals(service.getEnabled()) ? 1 : 0
        );
        requireAiWrite(deleted, "AI LLM service changed, please retry");
        invalidateGovernanceOverviewCache();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "ai", "llm-delete", "DELETE", "SUCCESS", "删除 LLM 服务: " + service.getCode());
        return true;
    }

    @Transactional
    public boolean updateLlmServiceEnabled(CurrentUser currentUser, Long id, boolean enabled) {
        requireLlmStatusPermission(currentUser);
        AiEntitiesHelper.LlmServiceRecord service = requireLlmService(id);
        int updated = jdbcTemplate.update(
                """
                        update ai_llm_service
                        set enabled = ?, update_time = ?
                        where id = ? and code = ? and provider = ? and enabled = ? and is_deleted = 0
                        """,
                enabled ? 1 : 0,
                LocalDateTime.now(),
                id,
                service.getCode(),
                service.getProvider(),
                Boolean.TRUE.equals(service.getEnabled()) ? 1 : 0
        );
        requireAiWrite(updated, "AI LLM service changed, please retry");
        invalidateGovernanceOverviewCache();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "ai", "llm-enabled", "UPDATE", "SUCCESS", "更新 LLM 服务状态: " + service.getCode() + " -> " + enabled);
        return true;
    }

    public AiVO.LlmServiceTestResultVO testLlmService(CurrentUser currentUser, AiDTO.LlmServiceTestRequest request) {
        requireLlmConfigurationPermission(currentUser);
        AiLlmServiceConfig config = buildTestConfig(request);
        AiDTO.ChatRequest chatRequest = new AiDTO.ChatRequest();
        chatRequest.setMessage("Please reply with OK only, used to verify whether the current LLM service configuration is available.");
        AiVO.EmployeeDetailVO testEmployee = new AiVO.EmployeeDetailVO();
        testEmployee.setId(0L);
        testEmployee.setNickname("LLM Service Test");
        testEmployee.setSystemPrompt("You are a connection test assistant. Please respond briefly to the user's request.");

        long startedAt = System.nanoTime();
        try {
            AiVO.ChatResponseVO response = aiChatModelFactory.create(config).chat(chatRequest, testEmployee, List.of());
            long latencyMs = elapsedMillis(startedAt);
            operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "ai", "llm-test", "TEST", "SUCCESS", "Test LLM service: " + safeAuditLabel(config));
            AiVO.LlmServiceTestResultVO result = new AiVO.LlmServiceTestResultVO();
            result.setSuccess(true);
            result.setMessage("Test passed");
            result.setProvider(response.getProvider());
            result.setModel(response.getModel());
            result.setLatencyMs(latencyMs);
            result.setReplyText(truncate(response.getReplyText(), 240));
            return result;
        } catch (RuntimeException exception) {
            long latencyMs = elapsedMillis(startedAt);
            String errorMessage = resolveFailureMessage(exception);
            operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "ai", "llm-test", "TEST", "FAIL", "Test LLM service failed: " + safeAuditLabel(config));
            AiVO.LlmServiceTestResultVO result = new AiVO.LlmServiceTestResultVO();
            result.setSuccess(false);
            result.setMessage(errorMessage);
            result.setProvider(config.getProvider());
            result.setModel(config.getDefaultModel());
            result.setLatencyMs(latencyMs);
            return result;
        }
    }

    private String resolveFailureMessage(RuntimeException exception) {
        if (exception == null) {
            return "LLM service test failed";
        }
        if (exception instanceof BizException bizException) {
            String message = bizException.getMessage();
            if (StringUtils.hasText(message)) {
                return message;
            }
            String userMessage = bizException.getUserMessage();
            return StringUtils.hasText(userMessage) ? userMessage : "LLM service test failed";
        }
        return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : "LLM service test failed";
    }

    public AiVO.EmployeeVO getAssistantEmployee(CurrentUser currentUser) {
        requireChatPermission(currentUser);
        return aiAssistantEmployeeResolver.getOrCreateAssistantEmployee();
    }

    public PageResponse<AiVO.ConversationVO> listConversations(CurrentUser currentUser, Long employeeId, long pageNo, long pageSize) {
        requireChatPermission(currentUser);
        if (employeeId != null) {
            requireEmployee(employeeId);
        }
        return pageQuery(
                """
                        select c.id, c.employee_id as employeeId,
                               c.owner_user_id as ownerUserId,
                               coalesce(e.nickname, e.username, 'AI 数字员工') as employeeName,
                               c.conversation_code as conversationCode, c.title, c.status,
                               c.is_pinned as pinned,
                               (
                                   select m.content
                                   from ai_message m
                                   where m.conversation_id = c.id
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
                         and e.is_deleted = 0
                        where c.owner_user_id = ?
                          and c.owner_user_uuid = ?
                          and (? is null or c.employee_id = ?)
                          and c.is_deleted = 0
                        order by c.is_pinned desc, coalesce(c.latest_message_at, c.create_time) desc, c.id desc
                        """,
                """
                        select count(1)
                        from ai_conversation c
                        where c.owner_user_id = ?
                          and c.owner_user_uuid = ?
                          and (? is null or c.employee_id = ?)
                          and c.is_deleted = 0
                        """,
                AiVO.ConversationVO.class,
                pageNo,
                pageSize,
                Arrays.asList(currentUser.getUserId(), currentUser.getUserUuid(), employeeId, employeeId)
        );
    }

    public List<AiVO.MessageVO> listConversationMessages(CurrentUser currentUser, Long conversationId) {
        requireChatPermission(currentUser);
        requireConversation(currentUser.getUserId(), currentUser.getUserUuid(), conversationId);
        CompletableFuture<List<AiVO.MessageVO>> messagesFuture = CompletableFuture.supplyAsync(() -> jdbcTemplate.query(
                """
                        select id, conversation_id as conversationId, role, content, create_time as createTime
                        from ai_message
                        where conversation_id = ?
                          and is_deleted = 0
                        order by id asc
                        """,
                new BeanPropertyRowMapper<>(AiVO.MessageVO.class),
                conversationId
        ), BLOCKING_IO_EXECUTOR);
        CompletableFuture<Map<Long, List<AiVO.MessageAttachmentVO>>> attachmentFuture = CompletableFuture.supplyAsync(
                () -> loadMessageAttachments(conversationId),
                BLOCKING_IO_EXECUTOR
        );
        List<AiVO.MessageVO> messages = messagesFuture.join();
        Map<Long, List<AiVO.MessageAttachmentVO>> attachmentMap = attachmentFuture.join();
        for (AiVO.MessageVO message : messages) {
            message.setAttachments(attachmentMap.getOrDefault(message.getId(), List.of()));
        }
        return messages;
    }

    @Transactional
    public boolean updateConversation(CurrentUser currentUser, Long conversationId, AiDTO.ConversationUpdateRequest request) {
        requireChatPermission(currentUser);
        AiVO.ConversationVO conversation = requireConversation(currentUser.getUserId(), currentUser.getUserUuid(), conversationId);
        String title = request == null ? null : request.getTitle();
        Boolean pinned = request == null ? null : request.getPinned();
        int updated = jdbcTemplate.update(
                """
                        update ai_conversation
                        set title = coalesce(?, title),
                            is_pinned = coalesce(?, is_pinned),
                            update_time = ?
                        where id = ?
                          and owner_user_id = ?
                          and owner_user_uuid = ?
                          and conversation_code = ?
                          and status = ?
                          and is_deleted = 0
                        """,
                StringUtils.hasText(title) ? title.trim() : null,
                pinned == null ? null : (pinned ? 1 : 0),
                LocalDateTime.now(),
                conversationId,
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                conversation.getConversationCode(),
                conversation.getStatus()
        );
        requireAiWrite(updated, "AI conversation changed, please retry");
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(),
                "ai",
                "conversation-update",
                "UPDATE",
                "SUCCESS",
                "更新 AI 会话: " + conversation.getConversationCode()
        );
        return true;
    }

    @Transactional
    public boolean deleteConversation(CurrentUser currentUser, Long conversationId) {
        requireChatPermission(currentUser);
        AiVO.ConversationVO conversation = requireConversation(currentUser.getUserId(), currentUser.getUserUuid(), conversationId);
        LocalDateTime now = LocalDateTime.now();
        int conversationDeleted = jdbcTemplate.update(
                """
                        update ai_conversation
                        set is_deleted = 1, update_time = ?
                        where id = ?
                          and owner_user_id = ?
                          and owner_user_uuid = ?
                          and conversation_code = ?
                          and status = ?
                          and is_deleted = 0
                        """,
                now,
                conversationId,
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                conversation.getConversationCode(),
                conversation.getStatus()
        );
        requireAiWrite(conversationDeleted, "AI conversation changed, please retry");
        jdbcTemplate.update(
                """
                        update ai_message
                        set is_deleted = 1, update_time = ?
                        where conversation_id in (
                            select id
                            from ai_conversation
                            where id = ?
                              and owner_user_id = ?
                              and owner_user_uuid = ?
                              and conversation_code = ?
                              and status = ?
                        )
                          and is_deleted = 0
                        """,
                now,
                conversationId,
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                conversation.getConversationCode(),
                conversation.getStatus()
        );
        jdbcTemplate.update(
                """
                        update ai_message_attachment
                        set is_deleted = 1, update_time = ?
                        where conversation_id in (
                            select id
                            from ai_conversation
                            where id = ?
                              and owner_user_id = ?
                              and owner_user_uuid = ?
                              and conversation_code = ?
                              and status = ?
                        )
                          and is_deleted = 0
                        """,
                now,
                conversationId,
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                conversation.getConversationCode(),
                conversation.getStatus()
        );
        if (conversationShareTableExists()) {
            jdbcTemplate.update(
                    """
                            update ai_conversation_share
                            set is_deleted = 1, update_time = ?
                            where conversation_id in (
                                select id
                                from ai_conversation
                                where id = ?
                                  and owner_user_id = ?
                                  and owner_user_uuid = ?
                                  and conversation_code = ?
                                  and status = ?
                            )
                              and is_deleted = 0
                            """,
                    now,
                    conversationId,
                    currentUser.getUserId(),
                    currentUser.getUserUuid(),
                    conversation.getConversationCode(),
                    conversation.getStatus()
            );
        }
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(),
                "ai",
                "conversation-delete",
                "DELETE",
                "SUCCESS",
                "删除 AI 会话: " + conversation.getConversationCode()
        );
        return true;
    }

    private boolean conversationShareTableExists() {
        return jdbcTemplate.exists(
                """
                        select 1
                        from information_schema.tables
                        where table_schema = database()
                          and table_name = 'ai_conversation_share'
                        limit 1
                        """
        );
    }

    @Transactional
    public AiVO.ConversationShareVO createConversationShare(CurrentUser currentUser, Long conversationId) {
        requireChatPermission(currentUser);
        AiVO.ConversationVO conversation = requireConversation(currentUser.getUserId(), currentUser.getUserUuid(), conversationId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(30);
        String shareToken = "share_" + UUID.randomUUID().toString().replace("-", "");
        int inserted = jdbcTemplate.update(
                """
                        insert into ai_conversation_share (
                            conversation_id, share_token, title, status, expires_at, created_by, created_by_uuid, is_deleted, create_time, update_time
                        )
                        select c.id, ?, ?, 'ACTIVE', ?, ?, ?, 0, ?, ?
                        from ai_conversation c
                        where c.id = ?
                          and c.owner_user_id = ?
                          and c.owner_user_uuid = ?
                          and c.conversation_code = ?
                          and c.status = ?
                          and c.is_deleted = 0
                        """,
                shareToken,
                StringUtils.hasText(conversation.getTitle()) ? conversation.getTitle().trim() : conversation.getPreview(),
                expiresAt,
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                now,
                now,
                conversationId,
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                conversation.getConversationCode(),
                conversation.getStatus()
        );
        requireAiWrite(inserted, "AI conversation changed, please retry");
        AiVO.ConversationShareVO share = new AiVO.ConversationShareVO();
        share.setShareToken(shareToken);
        share.setConversationId(conversationId);
        share.setShareTitle(StringUtils.hasText(conversation.getTitle()) ? conversation.getTitle().trim() : conversation.getPreview());
        share.setExpiresAt(expiresAt);
        share.setCreateTime(now);
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(),
                "ai",
                "conversation-share",
                "CREATE",
                "SUCCESS",
                "创建 AI 会话分享: " + conversation.getConversationCode()
        );
        return share;
    }

    public AiVO.ConversationShareDetailVO getConversationShare(CurrentUser currentUser, String shareToken) {
        requireChatPermission(currentUser);
        AiVO.ConversationShareVO share = requireConversationShare(shareToken);
        AiVO.ConversationVO conversation = requireConversation(currentUser.getUserId(), currentUser.getUserUuid(), share.getConversationId());
        AiVO.ConversationShareDetailVO detail = new AiVO.ConversationShareDetailVO();
        detail.setShare(share);
        detail.setConversation(conversation);
        detail.setMessages(listConversationMessages(currentUser, share.getConversationId()));
        return detail;
    }

    public AiVO.ConversationExportVO exportConversation(CurrentUser currentUser, Long conversationId, String format) {
        requireChatPermission(currentUser);
        AiVO.ConversationVO conversation = requireConversation(currentUser.getUserId(), currentUser.getUserUuid(), conversationId);
        List<AiVO.MessageVO> messages = listConversationMessages(currentUser, conversationId);
        String normalizedFormat = normalizeExportFormat(format);
        String content = buildConversationExportContent(conversation, messages, normalizedFormat);
        AiVO.ConversationExportVO export = new AiVO.ConversationExportVO();
        export.setConversationId(conversationId);
        export.setTitle(StringUtils.hasText(conversation.getTitle()) ? conversation.getTitle().trim() : "Conversation");
        export.setFormat(normalizedFormat);
        export.setFileName(buildExportFileName(export.getTitle(), normalizedFormat));
        export.setMimeType("markdown".equals(normalizedFormat) ? "text/markdown;charset=utf-8" : "text/plain;charset=utf-8");
        export.setContent(content);
        return export;
    }

    public AiVO.ChatResponseVO chat(CurrentUser currentUser, AiDTO.ChatRequest request) {
        return aiEmployeeRuntimeService.chat(currentUser, request);
    }

    public AiVO.ChatResponseVO streamChat(CurrentUser currentUser, AiDTO.ChatRequest request, java.util.function.Consumer<AiVO.ChatStreamEventVO> onEvent) {
        return aiEmployeeRuntimeService.streamChat(currentUser, request, onEvent);
    }

    private List<AiVO.EmployeeCapabilityVO> listEmployeeCapabilities(Long employeeId) {
        return jdbcTemplate.query(
                """
                        select k.skill_code as capabilityCode, k.skill_name as capabilityName, k.category, k.description,
                               k.risk_level as riskLevel, k.read_only as readOnly, k.need_confirm as needConfirm,
                               coalesce(r.permission_mode, case when k.read_only = 1 then 'visit' else 'deny' end) as permissionMode
                        from ai_skill k
                        left join ai_employee_skill r
                          on r.skill_code = k.skill_code
                         and r.employee_id = ?
                         and r.is_deleted = 0
                        where k.is_deleted = 0
                          and k.enabled = 1
                        order by k.category asc, k.skill_code asc
                        """,
                new BeanPropertyRowMapper<>(AiVO.EmployeeCapabilityVO.class),
                employeeId
        );
    }

    private void replaceEmployeeCapabilities(AiVO.EmployeeDetailVO employee, List<AiDTO.EmployeeCapabilityItem> items) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        update ai_employee_skill
                        set is_deleted = 1, update_time = ?
                        where employee_id in (
                            select id
                            from ai_employee
                            where id = ? and username = ? and enabled = ? and is_deleted = 0
                        )
                          and is_deleted = 0
                        """,
                now,
                employee.getId(),
                employee.getUsername(),
                Boolean.TRUE.equals(employee.getEnabled()) ? 1 : 0
        );
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<String, Boolean> capabilityReadOnlyMap = loadCapabilityReadOnlyMap(items.stream().map(AiDTO.EmployeeCapabilityItem::getCapabilityCode).toList());
        for (AiDTO.EmployeeCapabilityItem item : items) {
            if (item == null || !StringUtils.hasText(item.getCapabilityCode())) {
                continue;
            }
            String capabilityCode = item.getCapabilityCode().trim();
            Boolean readOnly = capabilityReadOnlyMap.get(capabilityCode);
            if (readOnly == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "AI 技能不存在: " + capabilityCode);
            }
            int inserted = jdbcTemplate.update(
                    """
                            insert into ai_employee_skill (
                                employee_id, skill_code, permission_mode, is_deleted, create_time, update_time
                            ) values (?, ?, ?, 0, ?, ?)
                            on duplicate key update
                                permission_mode = case when employee_id = values(employee_id) and skill_code = values(skill_code) then values(permission_mode) else permission_mode end,
                                is_deleted = case when employee_id = values(employee_id) and skill_code = values(skill_code) then 0 else is_deleted end,
                                update_time = case when employee_id = values(employee_id) and skill_code = values(skill_code) then values(update_time) else update_time end
                            """,
                    employee.getId(),
                    capabilityCode,
                    normalizeCapabilityMode(item.getPermissionMode(), readOnly),
                    now,
                    now
            );
            requireAiWrite(inserted, "AI employee capability changed, please retry");
        }
    }

    private Map<String, Boolean> loadCapabilityReadOnlyMap(List<String> capabilityCodes) {
        List<String> normalizedCodes = capabilityCodes == null
                ? List.of()
                : capabilityCodes.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
        if (normalizedCodes.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", normalizedCodes.stream().map(code -> "?").toList());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        select skill_code as skillCode, read_only as readOnly
                        from ai_skill
                        where is_deleted = 0
                          and enabled = 1
                          and skill_code in (%s)
                        """.formatted(placeholders),
                normalizedCodes.toArray()
        );
        Map<String, Boolean> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object code = row.get("skillCode");
            if (code != null) {
                result.put(String.valueOf(code), toBoolean(row.get("readOnly")));
            }
        }
        return result;
    }

    private String normalizeCapabilityMode(String permissionMode, Boolean readOnly) {
        String normalized = StringUtils.hasText(permissionMode) ? permissionMode.trim().toLowerCase(Locale.ROOT) : "deny";
        if ("deny".equals(normalized)) {
            return normalized;
        }
        if (Boolean.TRUE.equals(readOnly) && "visit".equals(normalized)) {
            return normalized;
        }
        if (!Boolean.TRUE.equals(readOnly) && "allow".equals(normalized)) {
            return normalized;
        }
        throw new BizException(
                ErrorCode.VALIDATION_ERROR,
                Boolean.TRUE.equals(readOnly)
                        ? "Read-only capability only supports visit or disable"
                        : "Executable capability only supports allow or disable"
        );
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value));
    }

    private AiVO.EmployeeDetailVO queryEmployeeDetail(Long id) {
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
                id
        ).stream().findFirst().orElse(null);
        if (employee == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "AI employee not found");
        }
        employee.setDefaultSystemPromptTemplate(DEFAULT_SYSTEM_PROMPT_TEMPLATE);
        return employee;
    }

    private AiEntitiesHelper.LlmServiceRecord requireLlmService(Long id) {
        AiEntitiesHelper.LlmServiceRecord service = jdbcTemplate.query(
                """
                        select id, provider, code, title, base_url as baseUrl,
                               api_key_encrypted as apiKeyEncrypted, default_model as defaultModel,
                               enabled, timeout_ms as timeoutMs, temperature, max_tokens as maxTokens,
                               create_time as createTime, update_time as updateTime
                        from ai_llm_service
                        where id = ?
                          and is_deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(AiEntitiesHelper.LlmServiceRecord.class),
                id
        ).stream().findFirst().orElse(null);
        if (service == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "LLM service not found");
        }
        return service;
    }

    private AiVO.LlmServiceVO toLlmServiceVO(AiEntitiesHelper.LlmServiceRecord record) {
        AiVO.LlmServiceVO vo = new AiVO.LlmServiceVO();
        vo.setId(record.getId());
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

    private void validateDefaultLlmService(Long defaultLlmServiceId) {
        if (defaultLlmServiceId == null) {
            return;
        }
        requireLlmService(defaultLlmServiceId);
    }

    private void requireEmployee(Long employeeId) {
        AiVO.EmployeeVO employee = jdbcTemplate.query(
                """
                        select id
                        from ai_employee
                        where id = ?
                          and is_deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                employeeId
        ).stream().findFirst().map(id -> {
            AiVO.EmployeeVO employeeVO = new AiVO.EmployeeVO();
            employeeVO.setId(id);
            return employeeVO;
        }).orElse(null);
        if (employee == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "AI employee not found");
        }
    }

    private AiVO.ConversationVO requireConversation(Long ownerUserId, String ownerUserUuid, Long conversationId) {
        AiVO.ConversationVO conversation = jdbcTemplate.query(
                """
                        select c.id,
                               c.employee_id as employeeId,
                               c.owner_user_id as ownerUserId,
                               c.owner_user_uuid as ownerUserUuid,
                               coalesce(e.nickname, e.username) as employeeName,
                               c.conversation_code as conversationCode,
                               c.title,
                               (
                                   select m.content
                                   from ai_message m
                                   where m.conversation_id = c.id
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
                         and e.is_deleted = 0
                        where c.owner_user_id = ?
                          and c.owner_user_uuid = ?
                          and c.id = ?
                          and c.is_deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(AiVO.ConversationVO.class),
                ownerUserId,
                ownerUserUuid,
                conversationId
        ).stream().findFirst().orElse(null);
        if (conversation == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Conversation not found");
        }
        return conversation;
    }

    private AiVO.ConversationShareVO requireConversationShare(String shareToken) {
        AiVO.ConversationShareVO share = jdbcTemplate.query(
                """
                        select share_token as shareToken, conversation_id as conversationId, title as shareTitle,
                               expires_at as expiresAt, create_time as createTime
                        from ai_conversation_share
                        where share_token = ?
                          and is_deleted = 0
                          and status = 'ACTIVE'
                          and (expires_at is null or expires_at >= now())
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(AiVO.ConversationShareVO.class),
                shareToken
        ).stream().findFirst().orElse(null);
        if (share == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Share link does not exist or has expired");
        }
        return share;
    }

    private Map<Long, List<AiVO.MessageAttachmentVO>> loadMessageAttachments(Long conversationId) {
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
                        where conversation_id = ?
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
            default -> throw new BizException(ErrorCode.BIZ_ERROR, "Unsupported export format");
        };
    }

    private String buildConversationExportContent(AiVO.ConversationVO conversation, List<AiVO.MessageVO> messages, String format) {
        StringBuilder builder = new StringBuilder();
        boolean markdown = "markdown".equals(format);
        String title = StringUtils.hasText(conversation.getTitle()) ? conversation.getTitle().trim() : "Conversation";
        if (markdown) {
            builder.append("# ").append(title).append("\n\n");
        } else {
            builder.append(title).append("\n");
        }
        if (StringUtils.hasText(conversation.getEmployeeName())) {
            builder.append(markdown ? "- " : "").append("AI Employee: ").append(conversation.getEmployeeName()).append("\n");
        }
        if (conversation.getLatestMessageAt() != null) {
            builder.append(markdown ? "- " : "").append("Updated At: ").append(conversation.getLatestMessageAt()).append("\n");
        }
        builder.append("\n");
        for (AiVO.MessageVO message : messages) {
            String role = "USER".equalsIgnoreCase(message.getRole()) ? "User" : "AI";
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
                    builder.append("\nAttachments:\n");
                    for (AiVO.MessageAttachmentVO attachment : message.getAttachments()) {
                        builder.append("- ").append(attachment.getOriginalFileName());
                        if (StringUtils.hasText(attachment.getDownloadUrl())) {
                            builder.append(" (").append(attachment.getDownloadUrl()).append(")");
                        }
                        builder.append("\n");
                    }
                } else {
                    builder.append("Attachments:\n");
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
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        long offset = (safePageNo - 1) * safePageSize;
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize);
        queryParams.add(offset);
        List<T> records = jdbcTemplate.query(selectSql + " limit ? offset ?", new BeanPropertyRowMapper<>(voClass), queryParams.toArray());
        long total = safePageNo == 1 && records.size() < safePageSize
                ? records.size()
                : nullToZero(jdbcTemplate.queryForObject(countSql, Long.class, params.toArray()));
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private void validateEmployeeUsernameAvailable(String username, Long excludeId) {
        boolean exists = jdbcTemplate.exists(
                """
                        select 1
                        from ai_employee
                        where username = ?
                          and is_deleted = 0
                          and (? is null or id <> ?)
                        limit 1
                        """,
                username,
                excludeId,
                excludeId
        );
        if (exists) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Username already exists");
        }
    }

    private void validateLlmServiceCodeAvailable(String code, Long excludeId) {
        boolean exists = jdbcTemplate.exists(
                """
                        select 1
                        from ai_llm_service
                        where code = ?
                          and is_deleted = 0
                          and (? is null or id <> ?)
                        limit 1
                        """,
                code,
                excludeId,
                excludeId
        );
        if (exists) {
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM service code already exists");
        }
    }
    private void requireLogin(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Login required");
        }
    }

    private void requireViewPermission(CurrentUser currentUser) {
        requirePermission(currentUser, PERMISSION_AI_VIEW);
    }

    private void requireChatPermission(CurrentUser currentUser) {
        requirePermission(currentUser, PERMISSION_AI_CHAT_SEND);
    }

    private void requireEmployeeCreatePermission(CurrentUser currentUser) {
        requirePermission(currentUser, PERMISSION_AI_EMPLOYEE_CREATE);
    }

    private void requireEmployeeUpdatePermission(CurrentUser currentUser) {
        requirePermission(currentUser, PERMISSION_AI_EMPLOYEE_UPDATE);
    }

    private void requireEmployeeDeletePermission(CurrentUser currentUser) {
        requirePermission(currentUser, PERMISSION_AI_EMPLOYEE_DELETE);
    }

    private void requireEmployeeStatusPermission(CurrentUser currentUser) {
        requirePermission(currentUser, PERMISSION_AI_EMPLOYEE_STATUS);
    }

    private void requireEmployeeSkillsPermission(CurrentUser currentUser) {
        requirePermission(currentUser, PERMISSION_AI_EMPLOYEE_SKILLS);
    }

    private void requireLlmCreatePermission(CurrentUser currentUser) {
        requirePermission(currentUser, PERMISSION_AI_LLM_CREATE);
    }

    private void requireLlmUpdatePermission(CurrentUser currentUser) {
        requirePermission(currentUser, PERMISSION_AI_LLM_UPDATE);
    }

    private void requireLlmDeletePermission(CurrentUser currentUser) {
        requirePermission(currentUser, PERMISSION_AI_LLM_DELETE);
    }

    private void requireLlmStatusPermission(CurrentUser currentUser) {
        requirePermission(currentUser, PERMISSION_AI_LLM_STATUS);
    }

    private void requireLlmConfigurationPermission(CurrentUser currentUser) {
        requireAnyPermission(currentUser, PERMISSION_AI_LLM_CREATE, PERMISSION_AI_LLM_UPDATE);
    }

    private void requireAiWrite(int updated, String message) {
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, message);
        }
    }

    private void requirePermission(CurrentUser currentUser, String permissionKey) {
        CurrentUser runtimeUser = refreshTrustedCurrentUser(currentUser);
        requireLogin(runtimeUser);
        if (runtimeUser.getPermissions() == null
                || (!runtimeUser.getPermissions().contains("*") && !runtimeUser.getPermissions().contains(permissionKey))) {
            throw new BizException(ErrorCode.FORBIDDEN, "Permission denied");
        }
    }

    private void requireAnyPermission(CurrentUser currentUser, String... permissionKeys) {
        CurrentUser runtimeUser = refreshTrustedCurrentUser(currentUser);
        requireLogin(runtimeUser);
        Set<String> permissions = runtimeUser.getPermissions();
        if (permissions != null && permissions.contains("*")) {
            return;
        }
        if (permissions != null) {
            for (String permissionKey : permissionKeys) {
                if (permissions.contains(permissionKey)) {
                    return;
                }
            }
        }
        throw new BizException(ErrorCode.FORBIDDEN, "Permission denied");
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
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Trusted user identity is required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            String currentUserUuid = userSnapshot == null || !StringUtils.hasText(userSnapshot.userUuid())
                    ? null
                    : userSnapshot.userUuid().trim();
            if (userSnapshot == null
                    || userSnapshot.userId() == null
                    || !userId.equals(userSnapshot.userId())
                    || !StringUtils.hasText(currentUserUuid)
                    || !normalizedUserUuid.equals(currentUserUuid)
                    || !STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user is disabled or no longer active");
            }
            if (!StringUtils.hasText(userSnapshot.username())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user username is unavailable");
            }
            userId = userSnapshot.userId();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(currentUserUuid);
            currentUser.setUsername(userSnapshot.username().trim());
            normalizedUserUuid = currentUserUuid;
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
        currentUser.setSimulatedRoleId(simulatedRoleId);
        currentUser.setUserUuid(normalizedUserUuid);
        currentUser.setPermissions(snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()));
        currentUser.setRoleIds(snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()));
        currentUser.setPrimaryDeptId(snapshot.getPrimaryDeptId());
        currentUser.setDeptIds(snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()));
        currentUser.setDescendantDeptIds(snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()));
        currentUser.setDataScopes(snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes()));
        currentUser.setPermissionsVersion(snapshot.getVersion());
        currentUser.setDefaultHomePath(snapshot.getDefaultHomePath());
        return currentUser;
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        CurrentUser refreshedUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(refreshedUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Trusted user identity is required");
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

    private Long count(String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count == null ? 0L : count;
    }

    private Map<String, Object> querySingleRow(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Long longValue(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String cleanNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void invalidateGovernanceOverviewCache() {
        governanceOverviewCache.invalidate(GOVERNANCE_OVERVIEW_CACHE_KEY);
        governanceOverviewLoadInFlight.invalidate(GOVERNANCE_OVERVIEW_CACHE_KEY);
    }

    private AiVO.GovernanceOverviewVO copyGovernanceOverview(AiVO.GovernanceOverviewVO source) {
        if (source == null) {
            return null;
        }
        AiVO.GovernanceOverviewVO copy = new AiVO.GovernanceOverviewVO();
        copy.setEmployeeCount(source.getEmployeeCount());
        copy.setEnabledEmployeeCount(source.getEnabledEmployeeCount());
        copy.setLlmServiceCount(source.getLlmServiceCount());
        copy.setEnabledLlmServiceCount(source.getEnabledLlmServiceCount());
        copy.setMissingApiKeyServiceCount(source.getMissingApiKeyServiceCount());
        copy.setSkillCount(source.getSkillCount());
        copy.setHighRiskSkillCount(source.getHighRiskSkillCount());
        copy.setConfirmationRequiredSkillCount(source.getConfirmationRequiredSkillCount());
        copy.setHighRiskAllowedBindingCount(source.getHighRiskAllowedBindingCount());
        copy.setSampledAt(source.getSampledAt());
        return copy;
    }

    /*
    private AiLlmServiceConfig buildTestConfig(AiDTO.LlmServiceTestRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Please fill in the LLM service configuration before testing");
        }
        AiEntitiesHelper.LlmServiceRecord existing = request.getServiceId() == null ? null : requireLlmService(request.getServiceId());
        String provider = firstText(request.getProvider(), existing == null ? null : existing.getProvider());
        if (!StringUtils.hasText(provider)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Please select an LLM type");
        }
        boolean reusingStoredApiKey = reusingStoredApiKey(request, existing);
        if (reusingStoredApiKey) {
            rejectStoredSecretEndpointOverride(request, existing);
        }
        AiLlmServiceConfig config = new AiLlmServiceConfig();
        config.setId(request.getServiceId());
        config.setProvider(provider);
        config.setCode(firstText(request.getCode(), existing == null ? null : existing.getCode(), "llm-test"));
        config.setTitle(firstText(request.getTitle(), existing == null ? null : existing.getTitle(), "LLM connection test assistant"));
        config.setBaseUrl(firstText(request.getBaseUrl(), existing == null ? null : existing.getBaseUrl()));
        config.setDefaultModel(firstText(request.getDefaultModel(), existing == null ? null : existing.getDefaultModel()));
        config.setApiKey(resolveTestApiKey(request, existing));
        config.setTimeoutMs(request.getTimeoutMs() == null ? (existing == null ? 60000 : existing.getTimeoutMs()) : request.getTimeoutMs());
        config.setTemperature(request.getTemperature() == null ? (existing == null ? null : existing.getTemperature()) : request.getTemperature());
        config.setMaxTokens(request.getMaxTokens() == null ? (existing == null ? 64 : existing.getMaxTokens()) : request.getMaxTokens());
        return config;
    }
    */

    private AiLlmServiceConfig buildTestConfig(AiDTO.LlmServiceTestRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Please fill in the LLM service configuration before testing");
        }
        AiEntitiesHelper.LlmServiceRecord existing = request.getServiceId() == null ? null : requireLlmService(request.getServiceId());
        String provider = firstText(request.getProvider(), existing == null ? null : existing.getProvider());
        if (!StringUtils.hasText(provider)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Please select an LLM type");
        }
        boolean reusingStoredApiKey = reusingStoredApiKey(request, existing);
        if (reusingStoredApiKey) {
            rejectStoredSecretEndpointOverride(request, existing);
        }
        AiLlmServiceConfig config = new AiLlmServiceConfig();
        config.setId(request.getServiceId());
        config.setProvider(provider);
        config.setCode(firstText(request.getCode(), existing == null ? null : existing.getCode(), "llm-test"));
        config.setTitle(firstText(request.getTitle(), existing == null ? null : existing.getTitle(), "LLM Service Test"));
        config.setBaseUrl(firstText(request.getBaseUrl(), existing == null ? null : existing.getBaseUrl()));
        config.setDefaultModel(firstText(request.getDefaultModel(), existing == null ? null : existing.getDefaultModel()));
        config.setApiKey(resolveTestApiKey(request, existing));
        config.setTimeoutMs(request.getTimeoutMs() == null ? (existing == null ? 60000 : existing.getTimeoutMs()) : request.getTimeoutMs());
        config.setTemperature(request.getTemperature() == null ? (existing == null ? null : existing.getTemperature()) : request.getTemperature());
        config.setMaxTokens(request.getMaxTokens() == null ? (existing == null ? 64 : existing.getMaxTokens()) : request.getMaxTokens());
        return config;
    }

    private String resolveTestApiKey(AiDTO.LlmServiceTestRequest request, AiEntitiesHelper.LlmServiceRecord existing) {
        if (StringUtils.hasText(request.getApiKey())) {
            return request.getApiKey().trim();
        }
        if (existing == null || !StringUtils.hasText(existing.getApiKeyEncrypted())) {
            return null;
        }
        return aiSecretCryptoService.decrypt(existing.getApiKeyEncrypted());
    }

    private boolean reusingStoredApiKey(AiDTO.LlmServiceTestRequest request, AiEntitiesHelper.LlmServiceRecord existing) {
        return !StringUtils.hasText(request.getApiKey()) && existing != null && StringUtils.hasText(existing.getApiKeyEncrypted());
    }

    private void rejectStoredSecretEndpointOverride(AiDTO.LlmServiceTestRequest request, AiEntitiesHelper.LlmServiceRecord existing) {
        if (overridesText(request.getProvider(), existing.getProvider()) || overridesText(request.getBaseUrl(), existing.getBaseUrl())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Please re-enter the API key after changing the LLM type or Base URL before testing");
        }
    }

    private boolean overridesText(String requested, String existing) {
        if (!StringUtils.hasText(requested)) {
            return false;
        }
        return !normalizeComparableText(requested).equals(normalizeComparableText(existing));
    }

    private String normalizeComparableText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private String safeAuditLabel(AiLlmServiceConfig config) {
        return firstText(config.getCode(), config.getTitle(), config.getProvider(), "Unnamed service");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static final class AiEntitiesHelper {
        private AiEntitiesHelper() {
        }

        public static class LlmServiceRecord {
            private Long id;
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
