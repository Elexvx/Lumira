package com.lumira.saas.modules.ai.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.authorization.AuthorizationDecision;
import com.lumira.common.security.authorization.AuthorizationRequest;
import com.lumira.common.security.authorization.AuthorizationService;
import com.lumira.common.security.authorization.AuthorizationVerdict;
import com.lumira.saas.modules.ai.integration.AiTrustedSessionResolver;
import com.lumira.saas.modules.ai.integration.AiPermissionSnapshotResolver;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.repository.AiToolPlanRepository;
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
import java.util.Set;
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
    private static final String STATUS_ENABLED = "ENABLED";

    private final AiToolPlanRepository toolPlanRepository;
    private final ObjectMapper objectMapper;
    private final AiNativeToolRuntimeService aiNativeToolRuntimeService;
    private final AiToolPolicyService aiToolPolicyService;
    private final AiLlmServiceConfigProvider aiLlmServiceConfigProvider;
    private final AiChatModelFactory aiChatModelFactory;
    private final PermissionGuard permissionGuard;
    private final AuthorizationService authorizationService;
    private final AiPermissionSnapshotResolver permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final AiTrustedSessionResolver sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    DefaultAiToolOrchestrationService(
            AiToolPlanRepository toolPlanRepository,
            ObjectMapper objectMapper,
            AiNativeToolRuntimeService aiNativeToolRuntimeService,
            AiToolPolicyService aiToolPolicyService,
            AiLlmServiceConfigProvider aiLlmServiceConfigProvider,
            AiChatModelFactory aiChatModelFactory,
            PermissionGuard permissionGuard,
            AuthorizationService authorizationService,
            AiPermissionSnapshotResolver permissionSnapshotService
    ) {
        this(
                toolPlanRepository,
                objectMapper,
                aiNativeToolRuntimeService,
                aiToolPolicyService,
                aiLlmServiceConfigProvider,
                aiChatModelFactory,
                permissionGuard,
                authorizationService,
                permissionSnapshotService,
                null,
                null,
                false
        );
    }

    @Autowired
    DefaultAiToolOrchestrationService(
            AiToolPlanRepository toolPlanRepository,
            ObjectMapper objectMapper,
            AiNativeToolRuntimeService aiNativeToolRuntimeService,
            AiToolPolicyService aiToolPolicyService,
            AiLlmServiceConfigProvider aiLlmServiceConfigProvider,
            AiChatModelFactory aiChatModelFactory,
            PermissionGuard permissionGuard,
            AuthorizationService authorizationService,
            AiPermissionSnapshotResolver permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            AiTrustedSessionResolver sessionAuthenticationService
    ) {
        this(
                toolPlanRepository,
                objectMapper,
                aiNativeToolRuntimeService,
                aiToolPolicyService,
                aiLlmServiceConfigProvider,
                aiChatModelFactory,
                permissionGuard,
                authorizationService,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                true
        );
    }

    DefaultAiToolOrchestrationService(
            AiToolPlanRepository toolPlanRepository,
            ObjectMapper objectMapper,
            AiNativeToolRuntimeService aiNativeToolRuntimeService,
            AiToolPolicyService aiToolPolicyService,
            AiLlmServiceConfigProvider aiLlmServiceConfigProvider,
            AiChatModelFactory aiChatModelFactory,
            PermissionGuard permissionGuard,
            AuthorizationService authorizationService,
            AiPermissionSnapshotResolver permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            AiTrustedSessionResolver sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.toolPlanRepository = toolPlanRepository;
        this.objectMapper = objectMapper;
        this.aiNativeToolRuntimeService = aiNativeToolRuntimeService;
        this.aiToolPolicyService = aiToolPolicyService;
        this.aiLlmServiceConfigProvider = aiLlmServiceConfigProvider;
        this.aiChatModelFactory = aiChatModelFactory;
        this.permissionGuard = permissionGuard;
        this.authorizationService = authorizationService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    DefaultAiToolOrchestrationService(
            AiToolPlanRepository toolPlanRepository,
            ObjectMapper objectMapper,
            AiNativeToolRuntimeService aiNativeToolRuntimeService,
            AiToolPolicyService aiToolPolicyService,
            AiLlmServiceConfigProvider aiLlmServiceConfigProvider,
            AiChatModelFactory aiChatModelFactory,
            PermissionGuard permissionGuard,
            AuthorizationService authorizationService,
            AiPermissionSnapshotResolver permissionSnapshotService,
            AiTrustedSessionResolver sessionAuthenticationService
    ) {
        this(
                toolPlanRepository,
                objectMapper,
                aiNativeToolRuntimeService,
                aiToolPolicyService,
                aiLlmServiceConfigProvider,
                aiChatModelFactory,
                permissionGuard,
                authorizationService,
                permissionSnapshotService,
                null,
                sessionAuthenticationService,
                false
        );
    }

    DefaultAiToolOrchestrationService(
            AiToolPlanRepository toolPlanRepository,
            ObjectMapper objectMapper,
            AiNativeToolRuntimeService aiNativeToolRuntimeService,
            AiToolPolicyService aiToolPolicyService,
            AiLlmServiceConfigProvider aiLlmServiceConfigProvider,
            AiChatModelFactory aiChatModelFactory,
            PermissionGuard permissionGuard,
            AuthorizationService authorizationService
    ) {
        this(toolPlanRepository,
                objectMapper,
                aiNativeToolRuntimeService,
                aiToolPolicyService,
                aiLlmServiceConfigProvider,
                aiChatModelFactory,
                permissionGuard,
                authorizationService,
                null);
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
        CurrentUser runtimeUser = refreshTrustedCurrentUser(currentUser);
        Long operatorId = requireLogin(runtimeUser);
        String operatorUuid = runtimeUser.getUserUuid();
        AiVO.ToolPlanVO plan = requirePlan(operatorId, operatorUuid, request.getPendingToolCallId());
        if (!"PENDING".equalsIgnoreCase(plan.getStatus())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "该 AI 工具计划已处理，不能重复确认");
        }
        if (plan.getExpiresAt() != null && plan.getExpiresAt().isBefore(LocalDateTime.now())) {
            updatePlanStatus(plan, "EXPIRED", operatorId, operatorUuid);
            throw new BizException(ErrorCode.BIZ_ERROR, "该 AI 工具计划已过期，请重新发起");
        }
        if ("DENY".equalsIgnoreCase(plan.getPolicyVerdict()) || "DENY".equalsIgnoreCase(plan.getSupervisorVerdict())) {
            updatePlanStatus(plan, "BLOCKED", operatorId, operatorUuid);
            throw new BizException(ErrorCode.FORBIDDEN, firstText(plan.getPolicyMessage(), plan.getSupervisorMessage(), "该操作未通过 AI 防护审查"));
        }

        verifyAuthorizationSnapshot(plan, runtimeUser);
        verifyArgumentsHash(plan, operatorId, operatorUuid);
        AiVO.ToolVO tool = requireTool(runtimeUser, plan.getEmployeeId(), plan.getToolCode());
        AuthorizationDecision authorizationDecision = authorizationService.evaluate(AuthorizationRequest.aiToolAction(
                runtimeUser,
                plan.getEmployeeId(),
                plan.getToolCode(),
                plan.getPermissionKey(),
                plan.getRiskLevel(),
                Boolean.TRUE.equals(tool.getReadOnly()) ? "view" : "execute",
                true,
                !Boolean.TRUE.equals(plan.getApprovalRequired()) || plan.getApprovedAt() != null,
                plan.getArguments()
        ));
        if (!authorizationDecision.allowed()) {
            updatePlanStatus(plan, "BLOCKED", operatorId, operatorUuid);
            throw new BizException(ErrorCode.FORBIDDEN, authorizationDecision.message());
        }

        AiDTO.ToolExecuteRequest executeRequest = new AiDTO.ToolExecuteRequest();
        if (plan.getEmployeeId() == null || plan.getEmployeeId() <= 0) {
            updatePlanStatus(plan, "BLOCKED", operatorId, operatorUuid);
            throw new BizException(ErrorCode.FORBIDDEN, "AI tool confirmation requires the original digital employee");
        }
        if (!claimPendingPlan(plan.getId(), operatorId, operatorUuid)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "该 AI 工具计划已处理，不能重复确认");
        }
        // The final compare-and-set must observe the state claimed above.
        // Keeping the stale PENDING value here would make a real JDBC transition
        // fail after a successful claim.
        plan.setStatus("EXECUTING");
        executeRequest.setEmployeeId(plan.getEmployeeId());
        executeRequest.setConversationId(plan.getConversationId());
        executeRequest.setToolCode(plan.getToolCode());
        executeRequest.setArguments(executionArguments(plan));
        executeRequest.setConfirmed(true);
        AiVO.ToolExecuteResultVO result = aiNativeToolRuntimeService.executeTrustedPlan(
                runtimeUser,
                executeRequest,
                !Boolean.TRUE.equals(plan.getApprovalRequired()) || plan.getApprovedAt() != null
        );
        updatePlanStatus(plan, "EXECUTED", operatorId, operatorUuid);
        enrichLatestAudit(plan, operatorId, operatorUuid);
        return result;
    }

    private AiVO.ToolPlanVO createPlan(CurrentUser currentUser, AiDTO.ToolProposeRequest request, ToolIntent intent) {
        CurrentUser runtimeUser = refreshTrustedCurrentUser(currentUser);
        Long operatorId = requireLogin(runtimeUser);
        String operatorUuid = runtimeUser.getUserUuid();
        AiVO.ToolVO tool = requireTool(runtimeUser, request.getEmployeeId(), intent.toolCode());
        permissionGuard.requirePermission(runtimeUser, tool.getRequiredPermission());
        String actionType = actionType(intent.toolCode());
        String riskLevel = firstText(tool.getRiskLevel(), "MEDIUM").toUpperCase(Locale.ROOT);
        boolean readOnly = Boolean.TRUE.equals(tool.getReadOnly());
        boolean requiresConfirm = !readOnly && (Boolean.TRUE.equals(tool.getNeedConfirm()) || !"LOW".equals(riskLevel));
        AuthorizationDecision authorizationDecision = authorizationService.evaluate(AuthorizationRequest.aiToolAction(
                runtimeUser,
                request.getEmployeeId(),
                intent.toolCode(),
                tool.getRequiredPermission(),
                riskLevel,
                readOnly ? "view" : "execute",
                false,
                false,
                intent.arguments()
        ));
        AiToolPolicyService.PolicyDecision policyDecision = aiToolPolicyService.evaluate(intent.toolCode(),
                actionType,
                riskLevel,
                request.getMessage(),
                intent.arguments()
        );
        SupervisorDecision supervisorDecision = supervise(runtimeUser, operatorId, tool, intent, policyDecision, requiresConfirm);
        String status = policyDecision.denied() || "DENY".equals(supervisorDecision.verdict())
                || authorizationDecision.verdict() == AuthorizationVerdict.DENY ? "BLOCKED" : "PENDING";
        AiVO.ToolPlanVO plan = new AiVO.ToolPlanVO();
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
        plan.setAuthorizationSnapshotJson(authorizationSnapshot(
                operatorId,
                operatorUuid,
                runtimeUser.getSimulatedRoleId(),
                request,
                tool,
                riskLevel,
                policyDecision,
                supervisorDecision,
                authorizationDecision
        ));
        plan.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        plan.setCreateTime(LocalDateTime.now());
        Long planId = insertPlan(operatorId, operatorUuid, plan, policyDecision.matches());
        return requirePlan(operatorId, operatorUuid, planId);
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
            return new ToolIntent("system.config.create", arguments);
        }
        Long configId = firstLongAfter(message, "配置ID", "配置id", "configId");
        if (containsAny(normalized, "修改配置", "更新配置", "编辑配置")) {
            putIfNotNull(arguments, "configId", configId);
            putIfText(arguments, "configKey", captureAfter(message, "配置键", "configKey", "键"));
            putIfText(arguments, "configName", captureAfter(message, "配置名称", "配置名", "名称"));
            putIfText(arguments, "configValue", captureAfter(message, "配置值", "值", "value"));
            return new ToolIntent("system.config.update", arguments);
        }
        return null;
    }

    private SupervisorDecision supervise(
            CurrentUser currentUser,
            Long ownerUserId,
            AiVO.ToolVO tool,
            ToolIntent intent,
            AiToolPolicyService.PolicyDecision policyDecision,
            boolean requiresConfirm
    ) {
        if (policyDecision.denied()) {
            return new SupervisorDecision("DENY", policyDecision.message());
        }
        Optional<AiLlmServiceConfig> supervisorConfig = aiLlmServiceConfigProvider.findSupervisor();
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
                    当前用户：userId=%s, permissions=%s
                    参数：%s
                    平台规则结论：%s / %s
                    """.formatted(
                    intent.toolCode(),
                    tool.getToolName(),
                    tool.getRiskLevel(),
                    tool.getRequiredPermission(),
                    ownerUserId,
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

    private Long insertPlan(Long ownerUserId, String ownerUserUuid, AiVO.ToolPlanVO plan, List<String> policyMatches) {
        Long planId = toolPlanRepository.create(
                ownerUserId,
                ownerUserUuid,
                plan,
                firstText(plan.getPolicyMessage(), String.join(",", policyMatches)),
                toJson(plan.getArguments()),
                LocalDateTime.now()
        );
        if (planId == null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "AI tool plan changed, please retry");
        }
        return planId;
    }

    private AiVO.ToolPlanVO requirePlan(Long ownerUserId, String ownerUserUuid, Long planId) {
        if (planId == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "待确认工具计划不能为空");
        }
        AiVO.ToolPlanVO plan = toolPlanRepository.findOwned(ownerUserId, ownerUserUuid, planId)
                .map(this::toToolPlan)
                .orElse(null);
        if (plan == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "待确认工具计划不存在");
        }
        return plan;
    }

    private AiVO.ToolPlanVO toToolPlan(AiToolPlanRepository.ToolPlanRecord record) {
        AiVO.ToolPlanVO plan = new AiVO.ToolPlanVO();
        plan.setId(record.id());
        plan.setConversationId(record.conversationId());
        plan.setEmployeeId(record.employeeId());
        plan.setToolCode(record.toolCode());
        plan.setToolName(record.toolName());
        plan.setActionType(record.actionType());
        plan.setRiskLevel(record.riskLevel());
        plan.setSummary(record.summary());
        plan.setPermissionKey(record.permissionKey());
        plan.setRequiresConfirm(record.requiresConfirm());
        plan.setSupervisorVerdict(record.supervisorVerdict());
        plan.setSupervisorMessage(record.supervisorMessage());
        plan.setPolicyVerdict(record.policyVerdict());
        plan.setPolicyMessage(record.policyMessage());
        plan.setArguments(parseJsonMap(record.argumentsJson()));
        plan.setArgumentsHash(record.argumentsHash());
        plan.setAuthorizationSnapshotJson(record.authorizationSnapshotJson());
        plan.setApprovalRequired(record.approvalRequired());
        plan.setApprovedAt(record.approvedAt());
        plan.setStatus(record.status());
        plan.setExpiresAt(record.expiresAt());
        plan.setCreateTime(record.createTime());
        return plan;
    }

    private void updatePlanStatus(AiVO.ToolPlanVO plan, String status, Long confirmedBy, String confirmedByUuid) {
        int updated = toolPlanRepository.transition(
                plan.getId(),
                confirmedBy,
                confirmedByUuid,
                plan.getStatus(),
                plan.getArgumentsHash(),
                status,
                LocalDateTime.now()
        );
        if (updated != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "AI tool plan changed, please retry");
        }
    }

    private boolean claimPendingPlan(Long planId, Long confirmedBy, String confirmedByUuid) {
        return toolPlanRepository.claimPending(planId, confirmedBy, confirmedByUuid, LocalDateTime.now());
    }

    private void verifyArgumentsHash(AiVO.ToolPlanVO plan, Long userId, String userUuid) {
        String actualHash = sha256(stableJson(plan.getArguments()));
        if (!StringUtils.hasText(plan.getArgumentsHash()) || !plan.getArgumentsHash().equals(actualHash)) {
            updatePlanStatus(plan, "BLOCKED", userId, userUuid);
            log.warn("AI tool plan arguments hash mismatch planId={} toolCode={}", plan.getId(), plan.getToolCode());
            throw new BizException(ErrorCode.FORBIDDEN, "AI tool plan arguments were modified");
        }
    }

    private void verifyAuthorizationSnapshot(AiVO.ToolPlanVO plan, CurrentUser currentUser) {
        Long userId = currentUser == null ? null : currentUser.getUserId();
        String userUuid = currentUser == null ? null : currentUser.getUserUuid();
        Map<String, Object> snapshot = parseJsonMap(plan.getAuthorizationSnapshotJson());
        boolean trusted = userId != null
                && userId.equals(snapshotLong(snapshot, "ownerUserId"))
                && equalsText(userUuid, snapshotText(snapshot, "ownerUserUuid"))
                && equalsNullable(normalizeSimulatedRoleId(currentUser == null ? null : currentUser.getSimulatedRoleId()),
                snapshotLong(snapshot, "simulatedRoleId"))
                && equalsNullable(plan.getEmployeeId(), snapshotLong(snapshot, "employeeId"))
                && equalsText(plan.getToolCode(), snapshotText(snapshot, "toolCode"))
                && equalsText(plan.getPermissionKey(), snapshotText(snapshot, "permissionKey"))
                && equalsText(plan.getRiskLevel(), snapshotText(snapshot, "riskLevel"))
                && equalsText(plan.getPolicyVerdict(), snapshotText(snapshot, "policyVerdict"))
                && equalsText(plan.getSupervisorVerdict(), snapshotText(snapshot, "supervisorVerdict"))
                && StringUtils.hasText(snapshotText(snapshot, "authorizationVerdict"));
        if (!trusted) {
            updatePlanStatus(plan, "BLOCKED", userId, userUuid);
            log.warn("AI tool plan authorization snapshot mismatch planId={} toolCode={}", plan.getId(), plan.getToolCode());
            throw new BizException(ErrorCode.FORBIDDEN, "AI tool plan authorization snapshot is invalid");
        }
    }

    private Map<String, Object> executionArguments(AiVO.ToolPlanVO plan) {
        Map<String, Object> arguments = new LinkedHashMap<>(plan.getArguments() == null ? Map.of() : plan.getArguments());
        arguments.put("_authorizationApprovalGranted", !Boolean.TRUE.equals(plan.getApprovalRequired()) || plan.getApprovedAt() != null);
        return arguments;
    }

    private void enrichLatestAudit(AiVO.ToolPlanVO plan, Long confirmedBy, String confirmedByUuid) {
        toolPlanRepository.enrichLatestAudit(plan, confirmedBy, confirmedByUuid, LocalDateTime.now());
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
            Long ownerUserId,
            String ownerUserUuid,
            Long simulatedRoleId,
            AiDTO.ToolProposeRequest request,
            AiVO.ToolVO tool,
            String riskLevel,
            AiToolPolicyService.PolicyDecision policyDecision,
            SupervisorDecision supervisorDecision,
            AuthorizationDecision authorizationDecision
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ownerUserId", ownerUserId);
        snapshot.put("ownerUserUuid", ownerUserUuid);
        snapshot.put("simulatedRoleId", normalizeSimulatedRoleId(simulatedRoleId));
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

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
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

    private Long snapshotLong(Map<String, Object> snapshot, String key) {
        Object value = snapshot == null ? null : snapshot.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String snapshotText(Map<String, Object> snapshot, String key) {
        Object value = snapshot == null ? null : snapshot.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private boolean equalsNullable(Long left, Long right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean equalsText(String left, String right) {
        String normalizedLeft = StringUtils.hasText(left) ? left.trim() : "";
        String normalizedRight = StringUtils.hasText(right) ? right.trim() : "";
        return normalizedLeft.equals(normalizedRight);
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

    private Long requireLogin(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Login required");
        }
        return currentUser.getUserId();
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
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
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
        AiPermissionSnapshotResolver.PermissionSnapshot snapshot = simulatedRoleId != null
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

    private CurrentUser requireTrustedAuthenticatedCurrentUser(AiTrustedSessionResolver.AuthenticatedAccess authenticatedAccess) {
        CurrentUser refreshedUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(refreshedUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Trusted user identity is required");
        }
        return refreshedUser;
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

    private record ToolIntent(String toolCode, Map<String, Object> arguments) {
    }

    private record SupervisorDecision(String verdict, String message) {
    }
}
