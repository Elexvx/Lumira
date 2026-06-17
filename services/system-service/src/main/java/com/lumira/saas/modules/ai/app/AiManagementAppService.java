package com.lumira.saas.modules.ai.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.infrastructure.AiSecretCryptoService;
import com.lumira.saas.modules.ai.vo.AiVO;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import org.springframework.dao.EmptyResultDataAccessException;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
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
    private static final Executor BLOCKING_IO_EXECUTOR = command -> Thread.ofVirtual().start(command);

    private static final String DEFAULT_SYSTEM_PROMPT_TEMPLATE = """
            你是一名企业级 SaaS 平台中的数字员工。
            你的目标是：基于当前平台的授权范围，稳妥、专业、清晰地完成用户交办的任务。
            你必须遵循以下要求：
            1. 先确认上下文，再执行任务。
            2. 遵守平台权限边界，不越权访问数据。
            3. 当任务涉及高风险操作时，先请求二次确认。
            4. 输出尽量简洁、结构清晰，优先给出可执行结论。
            """;

    private final MyBatisQueryOperations jdbcTemplate;
    private final OperationAuditService operationAuditService;
    private final AiSecretCryptoService aiSecretCryptoService;
    private final AiEmployeeRuntimeService aiEmployeeRuntimeService;
    private final AiChatModelFactory aiChatModelFactory;
    private final Cache<Long, AiVO.GovernanceOverviewVO> governanceOverviewCache;
    private final Cache<Long, CompletableFuture<AiVO.GovernanceOverviewVO>> governanceOverviewLoadInFlight;

    public AiManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            OperationAuditService operationAuditService,
            AiSecretCryptoService aiSecretCryptoService,
            AiEmployeeRuntimeService aiEmployeeRuntimeService,
            AiChatModelFactory aiChatModelFactory
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.operationAuditService = operationAuditService;
        this.aiSecretCryptoService = aiSecretCryptoService;
        this.aiEmployeeRuntimeService = aiEmployeeRuntimeService;
        this.aiChatModelFactory = aiChatModelFactory;
        this.governanceOverviewCache = CacheBuilder.newBuilder()
                .maximumSize(GOVERNANCE_OVERVIEW_CACHE_MAX_ENTRIES)
                .expireAfterWrite(GOVERNANCE_OVERVIEW_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
        this.governanceOverviewLoadInFlight = CacheBuilder.newBuilder()
                .maximumSize(GOVERNANCE_OVERVIEW_CACHE_MAX_ENTRIES)
                .expireAfterWrite(GOVERNANCE_OVERVIEW_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
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

    public AiVO.GovernanceOverviewVO governanceOverview(CurrentUser currentUser) {
        Long tenantId = currentTenantId(currentUser);
        AiVO.GovernanceOverviewVO cached = governanceOverviewCache.getIfPresent(tenantId);
        if (cached != null) {
            return copyGovernanceOverview(cached);
        }
        return loadGovernanceOverview(tenantId);
    }

    private AiVO.GovernanceOverviewVO loadGovernanceOverview(Long tenantId) {
        try {
            CompletableFuture<AiVO.GovernanceOverviewVO> future = governanceOverviewLoadInFlight.get(
                    tenantId,
                    () -> CompletableFuture.completedFuture(loadGovernanceOverviewFresh(tenantId))
            );
            AiVO.GovernanceOverviewVO overview = future.join();
            governanceOverviewLoadInFlight.invalidate(tenantId);
            return copyGovernanceOverview(overview);
        } catch (ExecutionException ex) {
            governanceOverviewLoadInFlight.invalidate(tenantId);
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to load governance overview", cause);
        } catch (RuntimeException ex) {
            governanceOverviewLoadInFlight.invalidate(tenantId);
            throw ex;
        }
    }

    private AiVO.GovernanceOverviewVO loadGovernanceOverviewFresh(Long tenantId) {
        CompletableFuture<Map<String, Object>> employeeStatsFuture = CompletableFuture.supplyAsync(() -> querySingleRow("""
                select count(1) as employeeCount,
                       coalesce(sum(case when enabled = 1 then 1 else 0 end), 0) as enabledEmployeeCount
                from ai_employee
                where tenant_id = ? and is_deleted = 0
                """, tenantId), BLOCKING_IO_EXECUTOR);
        CompletableFuture<Map<String, Object>> llmServiceStatsFuture = CompletableFuture.supplyAsync(() -> querySingleRow("""
                select count(1) as llmServiceCount,
                       coalesce(sum(case when enabled = 1 then 1 else 0 end), 0) as enabledLlmServiceCount,
                       coalesce(sum(case when api_key_encrypted is null or api_key_encrypted = '' then 1 else 0 end), 0) as missingApiKeyServiceCount
                from ai_llm_service
                where tenant_id = ? and is_deleted = 0
                """, tenantId), BLOCKING_IO_EXECUTOR);
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
                where es.tenant_id = ?
                  and es.is_deleted = 0
                  and es.permission_mode = 'allow'
                  and s.risk_level = 'HIGH'
                """, tenantId), BLOCKING_IO_EXECUTOR);

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
        governanceOverviewCache.put(tenantId, copyGovernanceOverview(overview));
        return overview;
    }

    public AiVO.EmployeeDetailVO getEmployee(CurrentUser currentUser, Long id) {
        Long tenantId = currentTenantId(currentUser);
        AiVO.EmployeeDetailVO employee = queryEmployeeDetail(tenantId, id);
        employee.setDefaultSystemPromptTemplate(DEFAULT_SYSTEM_PROMPT_TEMPLATE);
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
        Long employeeId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        invalidateGovernanceOverviewCache(tenantId);
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
        invalidateGovernanceOverviewCache(tenantId);
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
        invalidateGovernanceOverviewCache(tenantId);
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
        invalidateGovernanceOverviewCache(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "employee-enabled", "UPDATE", "SUCCESS", "更新数字员工状态: " + id + " -> " + enabled);
        return true;
    }

    public AiVO.PromptTemplateVO employeeTemplate(CurrentUser currentUser) {
        AiVO.PromptTemplateVO template = new AiVO.PromptTemplateVO();
        template.setDefaultSystemPromptTemplate(DEFAULT_SYSTEM_PROMPT_TEMPLATE);
        return template;
    }

    public List<AiVO.EmployeeCapabilityVO> getEmployeeCapabilities(CurrentUser currentUser, Long employeeId) {
        Long tenantId = currentTenantId(currentUser);
        requireEmployee(tenantId, employeeId);
        return listEmployeeCapabilities(tenantId, employeeId);
    }

    @Transactional
    public boolean updateEmployeeCapabilities(CurrentUser currentUser, Long employeeId, AiDTO.EmployeeCapabilitiesUpdateRequest request) {
        Long tenantId = currentTenantId(currentUser);
        requireEmployee(tenantId, employeeId);
        replaceEmployeeCapabilities(tenantId, employeeId, request == null ? List.of() : request.getCapabilities());
        invalidateGovernanceOverviewCache(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "employee-capabilities", "UPDATE", "SUCCESS", "更新数字员工能力边界: " + employeeId);
        return true;
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
        Long serviceId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        invalidateGovernanceOverviewCache(tenantId);
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
        invalidateGovernanceOverviewCache(tenantId);
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
        invalidateGovernanceOverviewCache(tenantId);
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
        invalidateGovernanceOverviewCache(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "llm-enabled", "UPDATE", "SUCCESS", "更新 LLM 服务状态: " + service.getCode() + " -> " + enabled);
        return true;
    }

    public AiVO.LlmServiceTestResultVO testLlmService(CurrentUser currentUser, AiDTO.LlmServiceTestRequest request) {
        Long tenantId = currentTenantId(currentUser);
        AiLlmServiceConfig config = buildTestConfig(tenantId, request);
        AiDTO.ChatRequest chatRequest = new AiDTO.ChatRequest();
        chatRequest.setMessage("请只回复 OK，用于验证当前 LLM 服务配置是否可用。");
        AiVO.EmployeeDetailVO testEmployee = new AiVO.EmployeeDetailVO();
        testEmployee.setId(0L);
        testEmployee.setNickname("LLM 服务测试");
        testEmployee.setSystemPrompt("你是一个连接测试助手。请按用户要求简短响应。");

        long startedAt = System.nanoTime();
        try {
            AiVO.ChatResponseVO response = aiChatModelFactory.create(config).chat(chatRequest, testEmployee, List.of());
            long latencyMs = elapsedMillis(startedAt);
            operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "llm-test", "TEST", "SUCCESS", "测试 LLM 服务: " + safeAuditLabel(config));
            AiVO.LlmServiceTestResultVO result = new AiVO.LlmServiceTestResultVO();
            result.setSuccess(true);
            result.setMessage("测试通过");
            result.setProvider(response.getProvider());
            result.setModel(response.getModel());
            result.setLatencyMs(latencyMs);
            result.setReplyText(truncate(response.getReplyText(), 240));
            return result;
        } catch (RuntimeException exception) {
            long latencyMs = elapsedMillis(startedAt);
            String errorMessage = resolveFailureMessage(exception);
            operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "llm-test", "TEST", "FAIL", "测试 LLM 服务失败: " + safeAuditLabel(config));
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
            return "LLM 服务测试失败";
        }
        if (exception instanceof BizException bizException) {
            String message = bizException.getMessage();
            if (StringUtils.hasText(message)) {
                return message;
            }
            String userMessage = bizException.getUserMessage();
            return StringUtils.hasText(userMessage) ? userMessage : "LLM 服务测试失败";
        }
        return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : "LLM 服务测试失败";
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
        if (employeeId != null) {
            requireEmployee(tenantId, employeeId);
        }
        return pageQuery(
                """
                        select c.id, c.tenant_id as tenantId, c.employee_id as employeeId,
                               c.owner_user_id as ownerUserId,
                               coalesce(e.nickname, e.username, 'AI 助手') as employeeName,
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
                          and c.owner_user_id = ?
                          and (? is null or c.employee_id = ?)
                          and c.is_deleted = 0
                        order by c.is_pinned desc, coalesce(c.latest_message_at, c.create_time) desc, c.id desc
                        """,
                """
                        select count(1)
                        from ai_conversation c
                        where c.tenant_id = ?
                          and c.owner_user_id = ?
                          and (? is null or c.employee_id = ?)
                          and c.is_deleted = 0
                        """,
                AiVO.ConversationVO.class,
                pageNo,
                pageSize,
                Arrays.asList(tenantId, currentUser.getUserId(), employeeId, employeeId)
        );
    }

    public List<AiVO.MessageVO> listConversationMessages(CurrentUser currentUser, Long conversationId) {
        Long tenantId = currentTenantId(currentUser);
        requireConversation(tenantId, currentUser.getUserId(), conversationId);
        CompletableFuture<List<AiVO.MessageVO>> messagesFuture = CompletableFuture.supplyAsync(() -> jdbcTemplate.query(
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
        ), BLOCKING_IO_EXECUTOR);
        CompletableFuture<Map<Long, List<AiVO.MessageAttachmentVO>>> attachmentFuture = CompletableFuture.supplyAsync(
                () -> loadMessageAttachments(tenantId, conversationId),
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
        Long tenantId = currentTenantId(currentUser);
        AiVO.ConversationVO conversation = requireConversation(tenantId, currentUser.getUserId(), conversationId);
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
        AiVO.ConversationVO conversation = requireConversation(tenantId, currentUser.getUserId(), conversationId);
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
        AiVO.ConversationVO conversation = requireConversation(tenantId, currentUser.getUserId(), conversationId);
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
        AiVO.ConversationVO conversation = requireConversation(tenantId, currentUser.getUserId(), share.getConversationId());
        AiVO.ConversationShareDetailVO detail = new AiVO.ConversationShareDetailVO();
        detail.setShare(share);
        detail.setConversation(conversation);
        detail.setMessages(listConversationMessages(currentUser, share.getConversationId()));
        return detail;
    }

    public AiVO.ConversationExportVO exportConversation(CurrentUser currentUser, Long conversationId, String format) {
        Long tenantId = currentTenantId(currentUser);
        AiVO.ConversationVO conversation = requireConversation(tenantId, currentUser.getUserId(), conversationId);
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

    public AiVO.ChatResponseVO chat(CurrentUser currentUser, AiDTO.ChatRequest request) {
        return aiEmployeeRuntimeService.chat(currentUser, request);
    }

    public AiVO.ChatResponseVO streamChat(CurrentUser currentUser, AiDTO.ChatRequest request, java.util.function.Consumer<AiVO.ChatStreamEventVO> onEvent) {
        return aiEmployeeRuntimeService.streamChat(currentUser, request, onEvent);
    }

    public AiVO.PromptTemplateVO defaultTemplate() {
        return employeeTemplate(null);
    }

    private List<AiVO.EmployeeCapabilityVO> listEmployeeCapabilities(Long tenantId, Long employeeId) {
        return jdbcTemplate.query(
                """
                        select k.skill_code as capabilityCode, k.skill_name as capabilityName, k.category, k.description,
                               k.risk_level as riskLevel, k.read_only as readOnly, k.need_confirm as needConfirm,
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
                new BeanPropertyRowMapper<>(AiVO.EmployeeCapabilityVO.class),
                tenantId,
                employeeId
        );
    }

    private void replaceEmployeeCapabilities(Long tenantId, Long employeeId, List<AiDTO.EmployeeCapabilityItem> items) {
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
                throw new BizException(ErrorCode.NOT_FOUND, "能力不存在: " + capabilityCode);
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
                    capabilityCode,
                    normalizeCapabilityMode(item.getPermissionMode(), readOnly),
                    now,
                    now
            );
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
        throw new BizException(ErrorCode.VALIDATION_ERROR, Boolean.TRUE.equals(readOnly) ? "只读能力仅支持访问或禁用" : "操作能力仅支持允许或禁用");
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

    private AiVO.ConversationVO requireConversation(Long tenantId, Long ownerUserId, Long conversationId) {
        AiVO.ConversationVO conversation = jdbcTemplate.query(
                """
                        select c.id,
                               c.tenant_id as tenantId,
                               c.employee_id as employeeId,
                               c.owner_user_id as ownerUserId,
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
                          and c.owner_user_id = ?
                          and c.id = ?
                          and c.is_deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(AiVO.ConversationVO.class),
                tenantId,
                ownerUserId,
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

    private void validateEmployeeUsernameAvailable(Long tenantId, String username, Long excludeId) {
        boolean exists = jdbcTemplate.exists(
                """
                        select 1
                        from ai_employee
                        where tenant_id = ?
                          and username = ?
                          and is_deleted = 0
                          and (? is null or id <> ?)
                        limit 1
                        """,
                tenantId,
                username,
                excludeId,
                excludeId
        );
        if (exists) {
            throw new BizException(ErrorCode.BIZ_ERROR, "用户名已存在");
        }
    }

    private void validateLlmServiceCodeAvailable(Long tenantId, String code, Long excludeId) {
        boolean exists = jdbcTemplate.exists(
                """
                        select 1
                        from ai_llm_service
                        where tenant_id = ?
                          and code = ?
                          and is_deleted = 0
                          and (? is null or id <> ?)
                        limit 1
                        """,
                tenantId,
                code,
                excludeId,
                excludeId
        );
        if (exists) {
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 服务标识已存在");
        }
    }

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser != null && currentUser.getCurrentTenantId() != null) {
            return currentUser.getCurrentTenantId();
        }
        return com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
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

    private void invalidateGovernanceOverviewCache(Long tenantId) {
        governanceOverviewCache.invalidate(tenantId);
        governanceOverviewLoadInFlight.invalidate(tenantId);
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

    private AiLlmServiceConfig buildTestConfig(Long tenantId, AiDTO.LlmServiceTestRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请填写 LLM 服务配置后再测试");
        }
        AiEntitiesHelper.LlmServiceRecord existing = request.getServiceId() == null ? null : requireLlmService(tenantId, request.getServiceId());
        String provider = firstText(request.getProvider(), existing == null ? null : existing.getProvider());
        if (!StringUtils.hasText(provider)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请选择 LLM 类型");
        }
        boolean reusingStoredApiKey = reusingStoredApiKey(request, existing);
        if (reusingStoredApiKey) {
            rejectStoredSecretEndpointOverride(request, existing);
        }
        AiLlmServiceConfig config = new AiLlmServiceConfig();
        config.setId(request.getServiceId());
        config.setProvider(provider);
        config.setCode(firstText(request.getCode(), existing == null ? null : existing.getCode(), "llm-test"));
        config.setTitle(firstText(request.getTitle(), existing == null ? null : existing.getTitle(), "LLM 服务测试"));
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
            throw new BizException(ErrorCode.BAD_REQUEST, "修改 LLM 类型或 Base URL 时请重新输入 API Key 后再测试");
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
        return firstText(config.getCode(), config.getTitle(), config.getProvider(), "未命名服务");
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
