package com.lumira.saas.modules.ai.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.authorization.AuthorizationDecision;
import com.lumira.common.security.authorization.AuthorizationRequest;
import com.lumira.common.security.authorization.AuthorizationService;
import com.lumira.common.security.authorization.AuthorizationVerdict;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.vo.AiVO;
import com.lumira.common.security.PermissionGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public interface AiToolOrchestrationService {

    Optional<AiVO.ToolPlanVO> tryPropose(CurrentUser currentUser, AiDTO.ToolProposeRequest request);

    AiVO.ToolPlanVO propose(CurrentUser currentUser, AiDTO.ToolProposeRequest request);

    AiVO.ToolExecuteResultVO confirm(CurrentUser currentUser, AiDTO.ToolConfirmRequest request);
}

@Service
@Primary
class DefaultAiToolOrchestrationService implements AiToolOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiToolOrchestrationService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final MyBatisQueryOperations jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiNativeToolRuntimeService aiNativeToolRuntimeService;
    private final AiToolPolicyService aiToolPolicyService;
    private final AiLlmServiceConfigProvider aiLlmServiceConfigProvider;
    private final AiChatModelFactory aiChatModelFactory;
    private final PermissionGuard permissionGuard;
    private final AuthorizationService authorizationService;

    @Autowired
    DefaultAiToolOrchestrationService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            AiNativeToolRuntimeService aiNativeToolRuntimeService,
            AiToolPolicyService aiToolPolicyService,
            AiLlmServiceConfigProvider aiLlmServiceConfigProvider,
            AiChatModelFactory aiChatModelFactory,
            PermissionGuard permissionGuard,
            AuthorizationService authorizationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.aiNativeToolRuntimeService = aiNativeToolRuntimeService;
        this.aiToolPolicyService = aiToolPolicyService;
        this.aiLlmServiceConfigProvider = aiLlmServiceConfigProvider;
        this.aiChatModelFactory = aiChatModelFactory;
        this.permissionGuard = permissionGuard;
        this.authorizationService = authorizationService;
    }

    @Override
    public Optional<AiVO.ToolPlanVO> tryPropose(CurrentUser currentUser, AiDTO.ToolProposeRequest request) {
        ToolIntent intent = resolveIntent(request);
        if (intent == null) {
            return Optional.empty();
        }
        return Optional.of(createPlan(currentUser, request, intent));
    }

    @Override
    public AiVO.ToolPlanVO propose(CurrentUser currentUser, AiDTO.ToolProposeRequest request) {
        ToolIntent intent = resolveIntent(request);
        if (intent == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "未识别到可执行的 AI 工具意图");
        }
        return createPlan(currentUser, request, intent);
    }

    @Override
    @Transactional
    public AiVO.ToolExecuteResultVO confirm(CurrentUser currentUser, AiDTO.ToolConfirmRequest request) {
        Long tenantId = currentTenantId(currentUser);
        AiVO.ToolPlanVO plan = requirePlan(tenantId, currentUser.getUserId(), request.getPendingToolCallId());
        if (!"PENDING".equalsIgnoreCase(plan.getStatus())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "该 AI 工具计划已处理，不能重复确认");
        }
        if (plan.getExpiresAt() != null && plan.getExpiresAt().isBefore(LocalDateTime.now())) {
            updatePlanStatus(tenantId, plan.getId(), "EXPIRED", currentUser.getUserId());
            throw new BizException(ErrorCode.BIZ_ERROR, "该 AI 工具计划已过期，请重新发起");
        }
        if ("DENY".equalsIgnoreCase(plan.getPolicyVerdict()) || "DENY".equalsIgnoreCase(plan.getSupervisorVerdict())) {
            updatePlanStatus(tenantId, plan.getId(), "BLOCKED", currentUser.getUserId());
            throw new BizException(ErrorCode.FORBIDDEN, firstText(plan.getPolicyMessage(), plan.getSupervisorMessage(), "该操作未通过 AI 防护审查"));
        }

        verifyArgumentsHash(tenantId, plan, currentUser.getUserId());
        AuthorizationDecision authorizationDecision = authorizationService.evaluate(AuthorizationRequest.aiTool(
                currentUser,
                plan.getEmployeeId(),
                plan.getToolCode(),
                plan.getPermissionKey(),
                plan.getRiskLevel(),
                true,
                !Boolean.TRUE.equals(plan.getApprovalRequired()) || plan.getApprovedAt() != null,
                plan.getArguments()
        ));
        if (!authorizationDecision.allowed()) {
            updatePlanStatus(tenantId, plan.getId(), "BLOCKED", currentUser.getUserId());
            throw new BizException(ErrorCode.FORBIDDEN, authorizationDecision.message());
        }

        AiDTO.ToolExecuteRequest executeRequest = new AiDTO.ToolExecuteRequest();
        if (plan.getEmployeeId() == null || plan.getEmployeeId() <= 0) {
            updatePlanStatus(tenantId, plan.getId(), "BLOCKED", currentUser.getUserId());
            throw new BizException(ErrorCode.FORBIDDEN, "AI tool confirmation requires the original digital employee");
        }
        if (!claimPendingPlan(tenantId, plan.getId(), currentUser.getUserId())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "该 AI 工具计划已处理，不能重复确认");
        }
        executeRequest.setEmployeeId(plan.getEmployeeId());
        executeRequest.setConversationId(plan.getConversationId());
        executeRequest.setToolCode(plan.getToolCode());
        executeRequest.setArguments(executionArguments(plan));
        executeRequest.setConfirmed(true);
        AiVO.ToolExecuteResultVO result = aiNativeToolRuntimeService.execute(currentUser, executeRequest);
        updatePlanStatus(tenantId, plan.getId(), "EXECUTED", currentUser.getUserId());
        enrichLatestAudit(tenantId, plan, currentUser.getUserId());
        return result;
    }

    private AiVO.ToolPlanVO createPlan(CurrentUser currentUser, AiDTO.ToolProposeRequest request, ToolIntent intent) {
        Long tenantId = currentTenantId(currentUser);
        AiVO.ToolVO tool = requireTool(currentUser, request.getEmployeeId(), intent.toolCode());
        permissionGuard.requirePermission(currentUser, tool.getRequiredPermission());
        String actionType = actionType(intent.toolCode());
        String riskLevel = firstText(tool.getRiskLevel(), "MEDIUM").toUpperCase(Locale.ROOT);
        boolean readOnly = Boolean.TRUE.equals(tool.getReadOnly());
        boolean requiresConfirm = !readOnly && (Boolean.TRUE.equals(tool.getNeedConfirm()) || !"LOW".equals(riskLevel));
        AuthorizationDecision authorizationDecision = authorizationService.evaluate(AuthorizationRequest.aiTool(
                currentUser,
                request.getEmployeeId(),
                intent.toolCode(),
                tool.getRequiredPermission(),
                riskLevel,
                false,
                false,
                intent.arguments()
        ));
        AiToolPolicyService.PolicyDecision policyDecision = aiToolPolicyService.evaluate(
                tenantId,
                intent.toolCode(),
                actionType,
                riskLevel,
                request.getMessage(),
                intent.arguments()
        );
        SupervisorDecision supervisorDecision = supervise(currentUser, tool, intent, policyDecision, requiresConfirm);
        String status = policyDecision.denied() || "DENY".equals(supervisorDecision.verdict())
                || authorizationDecision.verdict() == AuthorizationVerdict.DENY ? "BLOCKED" : "PENDING";
        AiVO.ToolPlanVO plan = new AiVO.ToolPlanVO();
        plan.setTenantId(tenantId);
        plan.setConversationId(request.getConversationId());
        plan.setEmployeeId(request.getEmployeeId());
        plan.setToolCode(intent.toolCode());
        plan.setToolName(tool.getToolName());
        plan.setActionType(actionType);
        plan.setRiskLevel(riskLevel);
        plan.setSummary(buildSummary(tool, intent.arguments()));
        plan.setPermissionKey(tool.getRequiredPermission());
        plan.setRequiresConfirm(requiresConfirm);
        plan.setSupervisorVerdict(supervisorDecision.verdict());
        plan.setSupervisorMessage(supervisorDecision.message());
        plan.setPolicyVerdict(policyDecision.verdict());
        plan.setPolicyMessage(policyDecision.message());
        plan.setStatus(status);
        plan.setArguments(intent.arguments());
        plan.setArgumentsHash(sha256(stableJson(intent.arguments())));
        plan.setApprovalRequired(authorizationDecision.verdict() == AuthorizationVerdict.REQUIRE_APPROVAL);
        plan.setAuthorizationSnapshotJson(authorizationSnapshot(tenantId, currentUser, request, tool, riskLevel, policyDecision, supervisorDecision, authorizationDecision));
        plan.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        plan.setCreateTime(LocalDateTime.now());
        Long planId = insertPlan(currentUser, plan, policyDecision.matches());
        return requirePlan(tenantId, currentUser.getUserId(), planId);
    }

    private ToolIntent resolveIntent(AiDTO.ToolProposeRequest request) {
        if (StringUtils.hasText(request.getToolCode())) {
            return new ToolIntent(request.getToolCode().trim(), request.getArguments() == null ? Map.of() : request.getArguments());
        }
        String message = request.getMessage();
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> arguments = new LinkedHashMap<>();
        if (containsAny(normalized, "头像", "avatar")) {
            String avatarUrl = firstUrl(message);
            if (StringUtils.hasText(avatarUrl)) {
                arguments.put("avatarUrl", avatarUrl);
            } else if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
                arguments.put("fileId", request.getAttachments().get(request.getAttachments().size() - 1).getFileId());
            }
            return arguments.isEmpty() ? null : new ToolIntent("profile.avatar.update", arguments);
        }
        if (containsAny(normalized, "新增用户", "创建用户", "新建用户", "添加用户")) {
            putIfText(arguments, "username", captureAfter(message, "用户名", "账号", "用户"));
            putIfText(arguments, "nickname", captureAfter(message, "昵称", "姓名", "叫"));
            putIfText(arguments, "realName", captureAfter(message, "真实姓名", "姓名"));
            putIfText(arguments, "mobile", firstMatch(message, "1[3-9]\\d{9}"));
            putIfText(arguments, "email", firstMatch(message, "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"));
            putIfText(arguments, "password", captureAfter(message, "密码", "初始密码"));
            arguments.putIfAbsent("status", normalized.contains("禁用") ? "DISABLED" : "ENABLED");
            return new ToolIntent("system.user.create", arguments);
        }
        Long userId = firstLongAfter(message, "用户ID", "用户id", "userId", "ID");
        if (containsAny(normalized, "禁用用户", "停用用户", "启用用户")) {
            if (userId != null) {
                arguments.put("userId", userId);
            }
            arguments.put("status", containsAny(normalized, "禁用", "停用") ? "DISABLED" : "ENABLED");
            return new ToolIntent("system.user.status", arguments);
        }
        if (containsAny(normalized, "删除用户", "移除用户")) {
            if (userId != null) {
                arguments.put("userId", userId);
            }
            return new ToolIntent("system.user.delete", arguments);
        }
        if (containsAny(normalized, "查询用户", "查看用户", "检索用户", "用户列表", "用户数量", "多少用户", "几个用户", "有多少个用户", "有几个用户")) {
            putIfText(arguments, "keyword", captureAfter(message, "关键词", "关键字", "用户名", "账号", "用户"));
            if (containsAny(normalized, "启用", "正常")) {
                arguments.put("status", "ENABLED");
            } else if (containsAny(normalized, "禁用", "停用")) {
                arguments.put("status", "DISABLED");
            }
            if (containsAny(normalized, "数量", "多少", "几个")) {
                arguments.put("limit", 1);
            }
            return new ToolIntent("system.user.search", arguments);
        }
        if (containsAny(normalized, "新增角色", "创建角色", "新建角色")) {
            putIfText(arguments, "roleCode", captureAfter(message, "角色编码", "roleCode", "编码"));
            putIfText(arguments, "roleName", captureAfter(message, "角色名称", "角色名", "名称", "角色"));
            putIfText(arguments, "roleType", captureAfter(message, "角色类型", "roleType", "类型"));
            arguments.putIfAbsent("roleType", "BUSINESS");
            return new ToolIntent("system.role.create", arguments);
        }
        Long roleId = firstLongAfter(message, "角色ID", "角色id", "roleId");
        if (containsAny(normalized, "删除角色")) {
            putIfNotNull(arguments, "roleId", roleId);
            return new ToolIntent("system.role.delete", arguments);
        }
        if (containsAny(normalized, "新增菜单", "创建菜单", "新建菜单")) {
            putIfText(arguments, "menuCode", captureAfter(message, "菜单编码", "menuCode", "编码"));
            putIfText(arguments, "menuName", captureAfter(message, "菜单名称", "菜单名", "名称", "菜单"));
            putIfText(arguments, "path", captureAfter(message, "路径", "path"));
            putIfText(arguments, "component", captureAfter(message, "组件", "component"));
            putIfText(arguments, "permissionKey", captureAfter(message, "权限", "权限键", "permissionKey"));
            arguments.put("status", "ENABLED");
            arguments.put("menuType", "MENU");
            return new ToolIntent("system.menu.create", arguments);
        }
        Long menuId = firstLongAfter(message, "菜单ID", "菜单id", "menuId");
        if (containsAny(normalized, "禁用菜单", "停用菜单", "启用菜单")) {
            putIfNotNull(arguments, "menuId", menuId);
            arguments.put("status", containsAny(normalized, "禁用", "停用") ? "DISABLED" : "ENABLED");
            return new ToolIntent("system.menu.status", arguments);
        }
        if (containsAny(normalized, "删除菜单")) {
            putIfNotNull(arguments, "menuId", menuId);
            return new ToolIntent("system.menu.delete", arguments);
        }
        if (containsAny(normalized, "新增字典类型", "创建字典类型", "新建字典类型")) {
            putIfText(arguments, "dictCode", captureAfter(message, "字典编码", "dictCode", "编码"));
            putIfText(arguments, "dictName", captureAfter(message, "字典名称", "字典名", "名称"));
            arguments.put("status", "ENABLED");
            return new ToolIntent("system.dict_type.create", arguments);
        }
        Long dictTypeId = firstLongAfter(message, "字典类型ID", "字典ID", "dictTypeId");
        if (containsAny(normalized, "删除字典类型")) {
            putIfNotNull(arguments, "dictTypeId", dictTypeId);
            return new ToolIntent("system.dict_type.delete", arguments);
        }
        if (containsAny(normalized, "新增字典项", "创建字典项", "新建字典项")) {
            putIfNotNull(arguments, "dictTypeId", dictTypeId);
            putIfText(arguments, "itemLabel", captureAfter(message, "标签", "名称", "字典项"));
            putIfText(arguments, "itemValue", captureAfter(message, "值", "value", "编码"));
            arguments.put("sortNo", 0);
            arguments.put("status", "ENABLED");
            return new ToolIntent("system.dict_item.create", arguments);
        }
        Long itemId = firstLongAfter(message, "字典项ID", "itemId");
        if (containsAny(normalized, "删除字典项")) {
            putIfNotNull(arguments, "dictTypeId", dictTypeId);
            putIfNotNull(arguments, "itemId", itemId);
            return new ToolIntent("system.dict_item.delete", arguments);
        }
        if (containsAny(normalized, "新增配置", "创建配置", "新建配置")) {
            putIfText(arguments, "configKey", captureAfter(message, "配置键", "configKey", "键"));
            putIfText(arguments, "configName", captureAfter(message, "配置名称", "配置名", "名称"));
            putIfText(arguments, "configValue", captureAfter(message, "配置值", "值", "value"));
            arguments.put("configScope", containsAny(normalized, "平台") ? "PLATFORM" : "TENANT");
            return new ToolIntent("system.config.create", arguments);
        }
        Long configId = firstLongAfter(message, "配置ID", "配置id", "configId");
        if (containsAny(normalized, "修改配置", "更新配置", "编辑配置")) {
            putIfNotNull(arguments, "configId", configId);
            putIfText(arguments, "configKey", captureAfter(message, "配置键", "configKey", "键"));
            putIfText(arguments, "configName", captureAfter(message, "配置名称", "配置名", "名称"));
            putIfText(arguments, "configValue", captureAfter(message, "配置值", "值", "value"));
            arguments.put("configScope", containsAny(normalized, "平台") ? "PLATFORM" : "TENANT");
            return new ToolIntent("system.config.update", arguments);
        }
        return null;
    }

    private SupervisorDecision supervise(
            CurrentUser currentUser,
            AiVO.ToolVO tool,
            ToolIntent intent,
            AiToolPolicyService.PolicyDecision policyDecision,
            boolean requiresConfirm
    ) {
        if (policyDecision.denied()) {
            return new SupervisorDecision("DENY", policyDecision.message());
        }
        Optional<AiLlmServiceConfig> supervisorConfig = aiLlmServiceConfigProvider.findSupervisor(currentTenantId(currentUser));
        if (supervisorConfig.isEmpty()) {
            return new SupervisorDecision(requiresConfirm ? "REQUIRE_CONFIRM" : "ALLOW", "平台规则审查通过；未配置独立监督模型，按本地防护规则处理。");
        }
        try {
            AiDTO.ChatRequest supervisorRequest = new AiDTO.ChatRequest();
            supervisorRequest.setMessage("""
                    你是平台 AI 工具监督模型。请只输出 JSON：{"verdict":"ALLOW|REQUIRE_CONFIRM|DENY","message":"简短中文原因"}。
                    待审查工具：%s
                    工具名称：%s
                    风险等级：%s
                    权限键：%s
                    当前用户：userId=%s, tenantId=%s, permissions=%s
                    参数：%s
                    平台规则结论：%s / %s
                    """.formatted(
                    intent.toolCode(),
                    tool.getToolName(),
                    tool.getRiskLevel(),
                    tool.getRequiredPermission(),
                    currentUser.getUserId(),
                    currentTenantId(currentUser),
                    currentUser.getPermissions(),
                    toJson(intent.arguments()),
                    policyDecision.verdict(),
                    policyDecision.message()
            ));
            AiVO.EmployeeDetailVO supervisorEmployee = new AiVO.EmployeeDetailVO();
            supervisorEmployee.setId(0L);
            supervisorEmployee.setUsername("ai-supervisor");
            supervisorEmployee.setNickname("AI 监督模型");
            supervisorEmployee.setSystemPrompt("你只负责审查平台 AI 工具调用风险，输出严格 JSON，不要执行任何业务动作。");
            String reply = aiChatModelFactory.create(supervisorConfig.get()).chat(supervisorRequest, supervisorEmployee, List.of()).getReplyText();
            Map<String, Object> parsed = objectMapper.readValue(extractJson(reply), MAP_TYPE);
            String verdict = String.valueOf(parsed.getOrDefault("verdict", requiresConfirm ? "REQUIRE_CONFIRM" : "ALLOW")).toUpperCase(Locale.ROOT);
            if (!List.of("ALLOW", "REQUIRE_CONFIRM", "DENY").contains(verdict)) {
                verdict = requiresConfirm ? "REQUIRE_CONFIRM" : "ALLOW";
            }
            return new SupervisorDecision(verdict, String.valueOf(parsed.getOrDefault("message", "监督模型审查完成")));
        } catch (Exception exception) {
            log.warn("AI supervisor review failed, falling back to local rules toolCode={}", intent.toolCode(), exception);
            return new SupervisorDecision(requiresConfirm ? "REQUIRE_CONFIRM" : "ALLOW", "监督模型暂不可用，已按平台本地防护规则处理。");
        }
    }

    private AiVO.ToolVO requireTool(CurrentUser currentUser, Long employeeId, String toolCode) {
        return aiNativeToolRuntimeService.listTools(currentUser, employeeId).stream()
                .filter(tool -> toolCode.equals(tool.getToolCode()))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "AI 工具不存在: " + toolCode));
    }

    private Long insertPlan(CurrentUser currentUser, AiVO.ToolPlanVO plan, List<String> policyMatches) {
        jdbcTemplate.update(
                """
                        insert into ai_tool_call_plan (
                            tenant_id, conversation_id, employee_id, owner_user_id, tool_code, tool_name, action_type,
                            risk_level, summary, permission_key, requires_confirm, supervisor_verdict,
                            supervisor_message, policy_verdict, policy_message, arguments_json,
                            arguments_hash, authorization_snapshot_json, approval_required, status,
                            expires_at, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                plan.getTenantId(),
                plan.getConversationId(),
                plan.getEmployeeId(),
                currentUser.getUserId(),
                plan.getToolCode(),
                plan.getToolName(),
                plan.getActionType(),
                plan.getRiskLevel(),
                plan.getSummary(),
                plan.getPermissionKey(),
                Boolean.TRUE.equals(plan.getRequiresConfirm()) ? 1 : 0,
                plan.getSupervisorVerdict(),
                plan.getSupervisorMessage(),
                plan.getPolicyVerdict(),
                firstText(plan.getPolicyMessage(), String.join(",", policyMatches)),
                toJson(plan.getArguments()),
                plan.getArgumentsHash(),
                plan.getAuthorizationSnapshotJson(),
                Boolean.TRUE.equals(plan.getApprovalRequired()) ? 1 : 0,
                plan.getStatus(),
                plan.getExpiresAt(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        return jdbcTemplate.queryForObject(
                """
                        select id from ai_tool_call_plan
                        where tenant_id = ? and owner_user_id = ?
                        order by id desc limit 1
                        """,
                Long.class,
                plan.getTenantId(),
                currentUser.getUserId()
        );
    }

    private AiVO.ToolPlanVO requirePlan(Long tenantId, Long ownerUserId, Long planId) {
        if (planId == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "待确认工具计划不能为空");
        }
        AiVO.ToolPlanVO plan = jdbcTemplate.query(
                """
                        select id, tenant_id as tenantId, conversation_id as conversationId, employee_id as employeeId,
                               tool_code as toolCode, tool_name as toolName, action_type as actionType,
                               risk_level as riskLevel, summary, permission_key as permissionKey,
                               requires_confirm as requiresConfirm, supervisor_verdict as supervisorVerdict,
                               supervisor_message as supervisorMessage, policy_verdict as policyVerdict,
                               policy_message as policyMessage, arguments_json as argumentsJson,
                               arguments_hash as argumentsHash,
                               authorization_snapshot_json as authorizationSnapshotJson,
                               approval_required as approvalRequired, approved_at as approvedAt,
                               status, expires_at as expiresAt, create_time as createTime
                        from ai_tool_call_plan
                        where tenant_id = ?
                          and owner_user_id = ?
                          and id = ?
                          and is_deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> {
                    AiVO.ToolPlanVO vo = new AiVO.ToolPlanVO();
                    vo.setId(rs.getLong("id"));
                    vo.setTenantId(rs.getLong("tenantId"));
                    vo.setConversationId(rs.getObject("conversationId") == null ? null : rs.getLong("conversationId"));
                    vo.setEmployeeId(rs.getObject("employeeId") == null ? null : rs.getLong("employeeId"));
                    vo.setToolCode(rs.getString("toolCode"));
                    vo.setToolName(rs.getString("toolName"));
                    vo.setActionType(rs.getString("actionType"));
                    vo.setRiskLevel(rs.getString("riskLevel"));
                    vo.setSummary(rs.getString("summary"));
                    vo.setPermissionKey(rs.getString("permissionKey"));
                    vo.setRequiresConfirm(rs.getInt("requiresConfirm") == 1);
                    vo.setSupervisorVerdict(rs.getString("supervisorVerdict"));
                    vo.setSupervisorMessage(rs.getString("supervisorMessage"));
                    vo.setPolicyVerdict(rs.getString("policyVerdict"));
                    vo.setPolicyMessage(rs.getString("policyMessage"));
                    vo.setArguments(parseJsonMap(rs.getString("argumentsJson")));
                    vo.setArgumentsHash(rs.getString("argumentsHash"));
                    vo.setAuthorizationSnapshotJson(rs.getString("authorizationSnapshotJson"));
                    vo.setApprovalRequired(rs.getInt("approvalRequired") == 1);
                    vo.setApprovedAt(rs.getTimestamp("approvedAt") == null ? null : rs.getTimestamp("approvedAt").toLocalDateTime());
                    vo.setStatus(rs.getString("status"));
                    vo.setExpiresAt(rs.getTimestamp("expiresAt").toLocalDateTime());
                    vo.setCreateTime(rs.getTimestamp("createTime").toLocalDateTime());
                    return vo;
                },
                tenantId,
                ownerUserId,
                planId
        ).stream().findFirst().orElse(null);
        if (plan == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "待确认工具计划不存在");
        }
        return plan;
    }

    private void updatePlanStatus(Long tenantId, Long planId, String status, Long confirmedBy) {
        jdbcTemplate.update(
                """
                        update ai_tool_call_plan
                        set status = ?, confirmed_by = ?, confirmed_at = ?, update_time = ?
                        where tenant_id = ? and id = ? and is_deleted = 0
                        """,
                status,
                confirmedBy,
                LocalDateTime.now(),
                LocalDateTime.now(),
                tenantId,
                planId
        );
    }

    private boolean claimPendingPlan(Long tenantId, Long planId, Long confirmedBy) {
        return jdbcTemplate.update(
                """
                        update ai_tool_call_plan
                        set status = 'EXECUTING', confirmed_by = ?, confirmed_at = ?, update_time = ?
                        where tenant_id = ? and id = ? and status = 'PENDING' and is_deleted = 0
                        """,
                confirmedBy,
                LocalDateTime.now(),
                LocalDateTime.now(),
                tenantId,
                planId
        ) == 1;
    }

    private void verifyArgumentsHash(Long tenantId, AiVO.ToolPlanVO plan, Long userId) {
        String actualHash = sha256(stableJson(plan.getArguments()));
        if (!StringUtils.hasText(plan.getArgumentsHash()) || !plan.getArgumentsHash().equals(actualHash)) {
            updatePlanStatus(tenantId, plan.getId(), "BLOCKED", userId);
            log.warn("AI tool plan arguments hash mismatch planId={} toolCode={}", plan.getId(), plan.getToolCode());
            throw new BizException(ErrorCode.FORBIDDEN, "AI tool plan arguments were modified");
        }
    }

    private Map<String, Object> executionArguments(AiVO.ToolPlanVO plan) {
        Map<String, Object> arguments = new LinkedHashMap<>(plan.getArguments() == null ? Map.of() : plan.getArguments());
        arguments.put("_authorizationApprovalGranted", !Boolean.TRUE.equals(plan.getApprovalRequired()) || plan.getApprovedAt() != null);
        return arguments;
    }

    private void enrichLatestAudit(Long tenantId, AiVO.ToolPlanVO plan, Long confirmedBy) {
        jdbcTemplate.update(
                """
                        update ai_tool_audit_log
                        set supervisor_verdict = ?, supervisor_message = ?, policy_match = ?,
                            confirmed_by = ?, confirmed_at = ?
                        where tenant_id = ?
                          and skill_code = ?
                          and is_deleted = 0
                        order by id desc
                        limit 1
                        """,
                plan.getSupervisorVerdict(),
                plan.getSupervisorMessage(),
                plan.getPolicyMessage(),
                confirmedBy,
                LocalDateTime.now(),
                tenantId,
                plan.getToolCode()
        );
    }

    private String actionType(String toolCode) {
        if (!StringUtils.hasText(toolCode) || !toolCode.contains(".")) {
            return "execute";
        }
        return toolCode.substring(toolCode.lastIndexOf('.') + 1);
    }

    private String buildSummary(AiVO.ToolVO tool, Map<String, Object> arguments) {
        return "%s：%s".formatted(tool.getToolName(), arguments.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .limit(8)
                .toList());
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String authorizationSnapshot(
            Long tenantId,
            CurrentUser currentUser,
            AiDTO.ToolProposeRequest request,
            AiVO.ToolVO tool,
            String riskLevel,
            AiToolPolicyService.PolicyDecision policyDecision,
            SupervisorDecision supervisorDecision,
            AuthorizationDecision authorizationDecision
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("tenantId", tenantId);
        snapshot.put("ownerUserId", currentUser.getUserId());
        snapshot.put("employeeId", request.getEmployeeId());
        snapshot.put("toolCode", tool.getToolCode());
        snapshot.put("permissionKey", tool.getRequiredPermission());
        snapshot.put("riskLevel", riskLevel);
        snapshot.put("policyVerdict", policyDecision.verdict());
        snapshot.put("supervisorVerdict", supervisorDecision.verdict());
        snapshot.put("authorizationVerdict", authorizationDecision.verdict().name());
        snapshot.put("matchedPolicies", authorizationDecision.matchedPolicies());
        snapshot.put("createdAt", LocalDateTime.now().toString());
        return toJson(snapshot);
    }

    private String stableJson(Object value) {
        try {
            return objectMapper.writeValueAsString(stableValue(value));
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Failed to serialize AI tool arguments");
        }
    }

    private Object stableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new java.util.TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    sorted.put(String.valueOf(entry.getKey()), stableValue(entry.getValue()));
                }
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                normalized.add(stableValue(item));
            }
            return normalized;
        }
        return value;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String extractJson(String text) {
        if (!StringUtils.hasText(text)) {
            return "{}";
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "{}";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void putIfText(Map<String, Object> arguments, String key, String value) {
        if (StringUtils.hasText(value)) {
            arguments.put(key, value.trim());
        }
    }

    private void putIfNotNull(Map<String, Object> arguments, String key, Object value) {
        if (value != null) {
            arguments.put(key, value);
        }
    }

    private String captureAfter(String message, String... labels) {
        for (String label : labels) {
            String match = firstMatch(message, Pattern.quote(label) + "[:：是为]?\\s*([\\w\\u4e00-\\u9fa5@.\\-]{2,64})");
            if (StringUtils.hasText(match)) {
                return match;
            }
        }
        return null;
    }

    private String firstMatch(String message, String regex) {
        var matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(message);
        if (!matcher.find()) {
            return null;
        }
        return matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
    }

    private String firstUrl(String message) {
        return firstMatch(message, "https?://[^\\s，。]+");
    }

    private Long firstLongAfter(String message, String... labels) {
        for (String label : labels) {
            String value = firstMatch(message, Pattern.quote(label) + "[:：#\\s]*([0-9]+)");
            if (StringUtils.hasText(value)) {
                return Long.parseLong(value);
            }
        }
        String value = firstMatch(message, "#([0-9]+)");
        return StringUtils.hasText(value) ? Long.parseLong(value) : null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser != null && currentUser.getCurrentTenantId() != null) {
            return currentUser.getCurrentTenantId();
        }
        throw new BizException(ErrorCode.FORBIDDEN, "Tenant context is required");
    }

    private record ToolIntent(String toolCode, Map<String, Object> arguments) {
    }

    private record SupervisorDecision(String verdict, String message) {
    }
}
