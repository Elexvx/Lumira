package com.lumira.saas.modules.ai.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AiConfigAccessPolicy;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.authorization.AuthorizationDecision;
import com.lumira.common.security.authorization.AuthorizationRequest;
import com.lumira.common.security.authorization.AuthorizationService;
import com.lumira.common.security.authorization.AuthorizationVerdict;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.vo.AiVO;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public interface AiNativeToolRuntimeService {

    List<AiVO.ToolVO> listTools(CurrentUser currentUser);

    List<AiVO.ToolVO> listTools(CurrentUser currentUser, Long employeeId);

    AiVO.ToolExecuteResultVO execute(CurrentUser currentUser, AiDTO.ToolExecuteRequest request);

    AiVO.ToolExecuteResultVO executeTrustedPlan(CurrentUser currentUser, AiDTO.ToolExecuteRequest request, boolean approvalGranted);

    default boolean isDirectExecutable(CurrentUser currentUser, String toolCode) {
        if (!StringUtils.hasText(toolCode)) {
            return false;
        }
        return listTools(currentUser).stream()
                .filter(tool -> toolCode.trim().equals(tool.getToolCode()))
                .findFirst()
                .map(tool -> Boolean.TRUE.equals(tool.getReadOnly()) && "LOW".equalsIgnoreCase(tool.getRiskLevel()))
                .orElse(false);
    }

    default boolean isDirectExecutable(CurrentUser currentUser, Long employeeId, String toolCode) {
        if (!StringUtils.hasText(toolCode)) {
            return false;
        }
        return listTools(currentUser, employeeId).stream()
                .filter(tool -> toolCode.trim().equals(tool.getToolCode()))
                .findFirst()
                .map(tool -> Boolean.TRUE.equals(tool.getReadOnly()) && "LOW".equalsIgnoreCase(tool.getRiskLevel()))
                .orElse(false);
    }
}

@Service
@Primary
@ConditionalOnLumiraControlPlaneEnabled
class DefaultAiNativeToolRuntimeService implements AiNativeToolRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiNativeToolRuntimeService.class);
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final String STATUS_ENABLED = "ENABLED";

    private final MyBatisQueryOperations jdbcTemplate;
    private final PermissionGuard permissionGuard;
    private final AuthorizationService authorizationService;
    private final AiSkillPermissionChecker aiSkillPermissionChecker;
    private final ObjectMapper objectMapper;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final SystemManagementAppService systemManagementAppService;
    private final AiPlatformQueryFacade platformQueryFacade;
    private final AiIamQueryFacade iamQueryFacade;
    private final FileInternalApi fileInternalApi;
    private final Map<String, NativeTool> tools;
    private final boolean writeToolsEnabled;

    DefaultAiNativeToolRuntimeService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionGuard permissionGuard,
            AuthorizationService authorizationService,
            AiSkillPermissionChecker aiSkillPermissionChecker,
            ObjectMapper objectMapper,
            AiPlatformQueryFacade platformQueryFacade,
            AiIamQueryFacade iamQueryFacade,
            PermissionSnapshotService permissionSnapshotService,
            SystemManagementAppService systemManagementAppService,
            FileInternalApi fileInternalApi,
            @Value("${saas.ai.native-tools.write-enabled:false}") boolean writeToolsEnabled
    ) {
        this(
                jdbcTemplate,
                permissionGuard,
                authorizationService,
                aiSkillPermissionChecker,
                objectMapper,
                platformQueryFacade,
                iamQueryFacade,
                permissionSnapshotService,
                null,
                null,
                systemManagementAppService,
                fileInternalApi,
                writeToolsEnabled
        );
    }

    @Autowired
    DefaultAiNativeToolRuntimeService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionGuard permissionGuard,
            AuthorizationService authorizationService,
            AiSkillPermissionChecker aiSkillPermissionChecker,
            ObjectMapper objectMapper,
            AiPlatformQueryFacade platformQueryFacade,
            AiIamQueryFacade iamQueryFacade,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService,
            SystemManagementAppService systemManagementAppService,
            FileInternalApi fileInternalApi,
            @Value("${saas.ai.native-tools.write-enabled:false}") boolean writeToolsEnabled
    ) {
        this(
                jdbcTemplate,
                permissionGuard,
                authorizationService,
                aiSkillPermissionChecker,
                objectMapper,
                platformQueryFacade,
                iamQueryFacade,
                permissionSnapshotService,
                null,
                sessionAuthenticationService,
                systemManagementAppService,
                fileInternalApi,
                writeToolsEnabled
        );
    }

    DefaultAiNativeToolRuntimeService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionGuard permissionGuard,
            AuthorizationService authorizationService,
            AiSkillPermissionChecker aiSkillPermissionChecker,
            ObjectMapper objectMapper,
            AiPlatformQueryFacade platformQueryFacade,
            AiIamQueryFacade iamQueryFacade,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            SystemManagementAppService systemManagementAppService,
            FileInternalApi fileInternalApi,
            boolean writeToolsEnabled
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionGuard = permissionGuard;
        this.authorizationService = authorizationService;
        this.aiSkillPermissionChecker = aiSkillPermissionChecker;
        this.objectMapper = objectMapper;
        this.platformQueryFacade = platformQueryFacade == null ? new DefaultAiPlatformQueryFacade(jdbcTemplate) : platformQueryFacade;
        this.iamQueryFacade = iamQueryFacade == null ? new DefaultAiIamQueryFacade(jdbcTemplate) : iamQueryFacade;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.systemManagementAppService = systemManagementAppService;
        this.fileInternalApi = fileInternalApi;
        this.writeToolsEnabled = writeToolsEnabled;
        this.tools = new LinkedHashMap<>(Map.of(
                "system.permission.snapshot", new NativeTool(
                        "system.permission.snapshot",
                        "读取当前权限上下文",
                        "system",
                        "返回当前登录用户、角色、部门和权限集合，供 AI 判断可访问边界。",
                        "LOW",
                        true,
                        false,
                        null,
                        Map.of("type", "object", "properties", Map.of()),
                        this::permissionSnapshot
                ),
                "system.menu.list", new NativeTool(
                        "system.menu.list",
                        "读取系统菜单与模块入口",
                        "system",
                        "按当前账号权限读取系统菜单、路由、权限键和状态，供 AI 理解平台能力地图。",
                        "LOW",
                        true,
                        false,
                        "system:menu:view",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "status", Map.of("type", "string", "description", "菜单状态，例如 ENABLED"),
                                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", MAX_LIMIT)
                                )
                        ),
                        this::listMenus
                ),
                "system.config.read", new NativeTool(
                        "system.config.read",
                        "读取非敏感系统配置",
                        "system",
                        "按配置键读取非敏感平台配置。涉及 password、secret、token、key 等敏感字段会被拒绝。",
                        "MEDIUM",
                        true,
                        false,
                        "system:config:view",
                        Map.of(
                                "type", "object",
                                "required", List.of("configKey"),
                                "properties", Map.of(
                                        "configKey", Map.of("type", "string", "description", "配置键")
                                )
                        ),
                        this::readScopedConfig
                ),
                "system.user.search", new NativeTool(
                        "system.user.search",
                        "检索系统用户",
                        "system",
                        "按关键词和状态检索平台用户，返回脱敏后的基础资料。",
                        "MEDIUM",
                        true,
                        false,
                        "system:user:view",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "keyword", Map.of("type", "string", "description", "用户名、昵称、姓名、手机号或邮箱关键词"),
                                        "status", Map.of("type", "string", "description", "用户状态，例如 ENABLED"),
                                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", MAX_LIMIT)
                                )
                        ),
                        this::searchUsers
                ),
                "file.object.search", new NativeTool(
                        "file.object.search",
                        "检索文件对象",
                        "file",
                        "按关键词、类型和状态检索文件中心对象；普通用户仅返回本人上传文件，全站文件管理员可检索平台文件。",
                        "MEDIUM",
                        true,
                        false,
                        "system:file:view",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "keyword", Map.of("type", "string", "description", "文件名、分类或标签关键词"),
                                        "contentType", Map.of("type", "string", "description", "MIME 类型前缀或完整类型"),
                                        "status", Map.of("type", "string", "description", "文件状态，例如 ENABLED"),
                                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", MAX_LIMIT)
                                )
                        ),
                        this::searchFiles
                ),
                "audit.ai_call.search", new NativeTool(
                        "audit.ai_call.search",
                        "检索 AI 工具审计",
                        "audit",
                        "按数字员工、技能编码和结果状态检索 AI 调用审计日志。",
                        "MEDIUM",
                        true,
                        false,
                        "audit:view",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "employeeId", Map.of("type", "integer", "description", "数字员工 ID"),
                                        "skillCode", Map.of("type", "string", "description", "技能或工具编码关键词"),
                                        "resultStatus", Map.of("type", "string", "description", "SUCCESS、FAIL 或 ERROR"),
                                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", MAX_LIMIT)
                                )
                        ),
                        this::searchAiCallAuditLogs
                )
        ));
        if (writeToolsEnabled) {
            registerWriteTools();
        }
    }

    private void registerWriteTools() {
        tools.put("system.user.create", new NativeTool(
                "system.user.create",
                "新增系统用户",
                "system",
                "在当前账号权限范围内新增系统用户。",
                "HIGH",
                false,
                true,
                "system:user:create",
                Map.of("type", "object", "required", List.of("username", "password", "status"), "properties", Map.of(
                        "username", Map.of("type", "string"),
                        "password", Map.of("type", "string"),
                        "nickname", Map.of("type", "string"),
                        "realName", Map.of("type", "string"),
                        "mobile", Map.of("type", "string"),
                        "email", Map.of("type", "string"),
                        "status", Map.of("type", "string", "enum", List.of("ENABLED", "DISABLED")),
                        "roleIds", Map.of("type", "array", "items", Map.of("type", "integer")),
                        "deptIds", Map.of("type", "array", "items", Map.of("type", "integer")),
                        "primaryDeptId", Map.of("type", "integer")
                )),
                this::createUser
        ));
        tools.put("system.user.update", new NativeTool(
                "system.user.update",
                "编辑系统用户",
                "system",
                "在当前账号权限范围内编辑用户基础信息、角色和部门。",
                "HIGH",
                false,
                true,
                "system:user:update",
                Map.of("type", "object", "required", List.of("userId", "userUuid"), "properties", Map.ofEntries(
                        Map.entry("userId", Map.of("type", "integer")),
                        Map.entry("userUuid", Map.of("type", "string")),
                        Map.entry("username", Map.of("type", "string")),
                        Map.entry("nickname", Map.of("type", "string")),
                        Map.entry("realName", Map.of("type", "string")),
                        Map.entry("mobile", Map.of("type", "string")),
                        Map.entry("email", Map.of("type", "string")),
                        Map.entry("avatarUrl", Map.of("type", "string")),
                        Map.entry("status", Map.of("type", "string", "enum", List.of("ENABLED", "DISABLED"))),
                        Map.entry("roleIds", Map.of("type", "array", "items", Map.of("type", "integer"))),
                        Map.entry("deptIds", Map.of("type", "array", "items", Map.of("type", "integer"))),
                        Map.entry("primaryDeptId", Map.of("type", "integer"))
                )),
                this::updateUser
        ));
        tools.put("system.user.status", new NativeTool(
                "system.user.status",
                "启停系统用户",
                "system",
                "在当前账号权限范围内启用或禁用用户。",
                "HIGH",
                false,
                true,
                "system:user:status",
                Map.of("type", "object", "required", List.of("userId", "userUuid", "status"), "properties", Map.of(
                        "userId", Map.of("type", "integer"),
                        "userUuid", Map.of("type", "string"),
                        "status", Map.of("type", "string", "enum", List.of("ENABLED", "DISABLED"))
                )),
                this::updateUserStatus
        ));
        tools.put("system.user.delete", new NativeTool(
                "system.user.delete",
                "删除系统用户",
                "system",
                "在当前账号权限范围内删除用户。",
                "HIGH",
                false,
                true,
                "system:user:delete",
                Map.of("type", "object", "required", List.of("userId", "userUuid"), "properties", Map.of(
                        "userId", Map.of("type", "integer"),
                        "userUuid", Map.of("type", "string")
                )),
                this::deleteUser
        ));
        tools.put("profile.avatar.update", new NativeTool(
                "profile.avatar.update",
                "修改当前用户头像",
                "profile",
                "仅修改当前登录用户自己的头像。可传 avatarUrl，或传已上传文件 fileId。",
                "MEDIUM",
                false,
                true,
                "profile:view",
                Map.of("type", "object", "properties", Map.of(
                        "avatarUrl", Map.of("type", "string"),
                        "fileId", Map.of("type", "integer")
                )),
                this::updateCurrentAvatar
        ));
        registerSystemManagementTools();
    }

    private void registerSystemManagementTools() {
        tools.put("system.role.create", writeTool("system.role.create", "新增角色", "新增平台角色。", "system:role:create", this::createRole));
        tools.put("system.role.update", writeTool("system.role.update", "编辑角色", "编辑平台角色基础信息。", "system:role:update", this::updateRole));
        tools.put("system.role.permissions", writeTool("system.role.permissions", "配置角色权限", "更新平台角色权限集合。", "system:role:grant", this::updateRolePermissions));
        tools.put("system.role.delete", writeTool("system.role.delete", "删除角色", "删除平台角色。", "system:role:delete", this::deleteRole));
        tools.put("system.menu.create", writeTool("system.menu.create", "新增菜单", "新增平台自定义菜单。", "system:menu:create", this::createMenu));
        tools.put("system.menu.update", writeTool("system.menu.update", "编辑菜单", "编辑平台自定义菜单。", "system:menu:update", this::updateMenu));
        tools.put("system.menu.status", writeTool("system.menu.status", "启停菜单", "更新平台菜单状态。", "system:menu:status", this::updateMenuStatus));
        tools.put("system.menu.delete", writeTool("system.menu.delete", "删除菜单", "删除平台自定义菜单。", "system:menu:delete", this::deleteMenu));
        tools.put("system.dict_type.create", writeTool("system.dict_type.create", "新增字典类型", "新增平台字典类型。", "system:dict:create", this::createDictType));
        tools.put("system.dict_type.update", writeTool("system.dict_type.update", "编辑字典类型", "编辑平台字典类型。", "system:dict:update", this::updateDictType));
        tools.put("system.dict_type.delete", writeTool("system.dict_type.delete", "删除字典类型", "删除平台非系统字典类型。", "system:dict:delete", this::deleteDictType));
        tools.put("system.dict_item.create", writeTool("system.dict_item.create", "新增字典项", "新增平台字典项。", "system:dict:create", this::createDictItem));
        tools.put("system.dict_item.update", writeTool("system.dict_item.update", "编辑字典项", "编辑平台字典项。", "system:dict:update", this::updateDictItem));
        tools.put("system.dict_item.delete", writeTool("system.dict_item.delete", "删除字典项", "删除平台字典项。", "system:dict:delete", this::deleteDictItem));
        tools.put("system.config.create", writeTool("system.config.create", "新增系统配置", "新增非敏感平台配置。", "system:config:update", this::createConfig));
        tools.put("system.config.update", writeTool("system.config.update", "编辑系统配置", "编辑非敏感平台配置。", "system:config:update", this::updateConfig));
        tools.put("platform.branding.update", writeTool("platform.branding.update", "更新品牌设置", "更新网站名称、Logo、页脚等品牌设置。", "system:config:update", this::updateBrandingSettings));
        tools.put("platform.agreement.update", writeTool("platform.agreement.update", "更新协议设置", "更新用户协议与隐私协议设置。", "system:config:update", this::updateAgreementSettings));
        tools.put("platform.watermark.update", writeTool("platform.watermark.update", "更新水印设置", "更新平台水印设置。", "system:config:update", this::updateWatermarkSettings));
        tools.put("platform.floating_window.update", writeTool("platform.floating_window.update", "更新浮窗设置", "更新全局浮窗设置。", "system:config:update", this::updateFloatingWindowSettings));
    }

    private NativeTool writeTool(String code, String name, String description, String requiredPermission, ToolExecutor executor) {
        return new NativeTool(
                code,
                name,
                "system",
                description,
                "HIGH",
                false,
                true,
                requiredPermission,
                Map.of("type", "object", "properties", Map.of()),
                executor
        );
    }

    @Override
    public List<AiVO.ToolVO> listTools(CurrentUser currentUser) {
        return listTools(currentUser, null);
    }

    @Override
    public List<AiVO.ToolVO> listTools(CurrentUser currentUser, Long employeeId) {
        CurrentUser runtimeUser = refreshTrustedCurrentUserIfAvailable(currentUser);
        return tools.values().stream()
                .sorted(Comparator.comparing(NativeTool::code))
                .filter(tool -> visible(runtimeUser, employeeId, tool))
                .map(NativeTool::toVO)
                .toList();
    }

    private boolean visible(CurrentUser currentUser, Long employeeId, NativeTool tool) {
        if (employeeId != null && employeeId > 0) {
            AuthorizationDecision decision = authorizationService.evaluate(AuthorizationRequest.aiToolAccess(
                    currentUser,
                    employeeId,
                    tool.code(),
                    tool.requiredPermission(),
                    tool.riskLevel(),
                    tool.readOnly() ? "view" : "execute",
                    Map.of("readOnly", tool.readOnly(), "permissionMode", tool.readOnly() ? "VIEW" : "EXECUTE")
            ));
            return decision.allowed() || decision.verdict() == AuthorizationVerdict.REQUIRE_CONFIRM;
        }
        if (!tool.readOnly() || !"LOW".equalsIgnoreCase(tool.riskLevel())) {
            return false;
        }
        return !StringUtils.hasText(tool.requiredPermission()) || hasPermission(currentUser, tool.requiredPermission());
    }

    @Override
    public AiVO.ToolExecuteResultVO execute(CurrentUser currentUser, AiDTO.ToolExecuteRequest request) {
        return executeInternal(currentUser, request, false);
    }

    @Override
    public AiVO.ToolExecuteResultVO executeTrustedPlan(CurrentUser currentUser, AiDTO.ToolExecuteRequest request, boolean approvalGranted) {
        return executeInternal(currentUser, request, approvalGranted);
    }

    private AiVO.ToolExecuteResultVO executeInternal(CurrentUser currentUser, AiDTO.ToolExecuteRequest request, boolean approvalGranted) {
        if (request == null || !StringUtils.hasText(request.getToolCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "工具编码不能为空");
        }
        CurrentUser runtimeUser = refreshTrustedCurrentUser(currentUser);
        Long actorUserId = requireLogin(runtimeUser);
        String actorUsername = trustedUsername(runtimeUser);
        String toolCode = request.getToolCode().trim();
        NativeTool tool = tools.get(toolCode);
        if (tool == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "AI 工具不存在: " + toolCode);
        }
        boolean confirmed = Boolean.TRUE.equals(request.getConfirmed());
        Map<String, Object> arguments = request.getArguments() == null ? Map.of() : request.getArguments();
        Map<String, Object> executionArguments = stripInternalAuthorizationArguments(arguments);

        try {
            requireEmployee(request.getEmployeeId());
            authorizationService.require(AuthorizationRequest.aiToolAction(
                    runtimeUser,
                    request.getEmployeeId(),
                    toolCode,
                    tool.requiredPermission(),
                    tool.riskLevel(),
                    tool.readOnly() ? "view" : "execute",
                    confirmed,
                    approvalGranted,
                    executionArguments
            ));
            aiSkillPermissionChecker.verifyToolAllowed(request.getEmployeeId(),
                    toolCode,
                    tool.requiredPermission(),
                    tool.riskLevel(),
                    tool.readOnly(),
                    confirmed
            );
            if (StringUtils.hasText(tool.requiredPermission())) {
                permissionGuard.requirePermission(runtimeUser, tool.requiredPermission());
            }
            Map<String, Object> data = tool.executor().execute(new ToolExecutionContext(
                    runtimeUser,
                    executionArguments,
                    actorUserId,
                    runtimeUser.getUserUuid(),
                    actorUsername
            ));
            AiVO.ToolExecuteResultVO result = new AiVO.ToolExecuteResultVO();
            result.setToolCode(toolCode);
            result.setResultStatus("SUCCESS");
            result.setMessage("工具调用成功");
            result.setData(data);
            result.setExecutedAt(LocalDateTime.now());
            recordToolAuditLog(currentUser, request, tool, confirmed, "allow", "SUCCESS", "AI 工具调用成功", data);
            return result;
        } catch (RuntimeException exception) {
            recordFailedToolAuditLog(currentUser, request, tool, confirmed, exception);
            throw exception;
        }
    }

    private Map<String, Object> stripInternalAuthorizationArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty() || !arguments.containsKey("_authorizationApprovalGranted")) {
            return arguments == null ? Map.of() : arguments;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>(arguments);
        sanitized.remove("_authorizationApprovalGranted");
        return sanitized;
    }

    private Map<String, Object> permissionSnapshot(ToolExecutionContext context) {
        CurrentUser currentUser = context.currentUser();
        boolean trusted = isTrustedCurrentUser(currentUser);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", trusted ? currentUser.getUserId() : null);
        data.put("userUuid", trusted ? currentUser.getUserUuid() : null);
        data.put("username", trusted ? currentUser.getUsername() : null);
        data.put("authenticated", trusted);
        data.put("permissions", trusted && currentUser.getPermissions() != null ? currentUser.getPermissions().stream().sorted().toList() : List.of());
        data.put("roleIds", trusted && currentUser.getRoleIds() != null ? currentUser.getRoleIds().stream().sorted().toList() : List.of());
        data.put("primaryDeptId", trusted ? currentUser.getPrimaryDeptId() : null);
        data.put("deptIds", trusted && currentUser.getDeptIds() != null ? currentUser.getDeptIds().stream().sorted().toList() : List.of());
        data.put("descendantDeptIds", trusted && currentUser.getDescendantDeptIds() != null ? currentUser.getDescendantDeptIds().stream().sorted().toList() : List.of());
        return data;
    }

    private Map<String, Object> listMenus(ToolExecutionContext context) {
        String status = stringArg(context.arguments(), "status", "ENABLED");
        int limit = limitArg(context.arguments());
        List<Map<String, Object>> menus = filterVisibleMenus(
                platformQueryFacade.listMenus(status, limit),
                trustedPermissions(context.currentUser())
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", menus);
        data.put("limit", limit);
        data.put("count", menus.size());
        return data;
    }

    private Map<String, Object> readScopedConfig(ToolExecutionContext context) {
        ensureAiConfigKeyAllowed(stringArg(context.arguments(), "configKey", null), "鏁忔劅閰嶇疆涓嶅厑璁搁€氳繃 AI 宸ュ叿璇诲彇: ");
        return readConfig(context);
    }

    private Map<String, Object> readConfig(ToolExecutionContext context) {
        String configKey = stringArg(context.arguments(), "configKey", null);
        if (!StringUtils.hasText(configKey)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "configKey 不能为空");
        }
        if (looksSensitive(configKey)) {
            throw new BizException(ErrorCode.FORBIDDEN, "敏感配置不允许通过 AI 工具读取: " + configKey);
        }
        Map<String, Object> config = platformQueryFacade.readConfig(configKey.trim());
        if (config == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "配置不存在: " + configKey);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("config", config);
        return data;
    }

    private List<Map<String, Object>> filterVisibleMenus(List<Map<String, Object>> menus, Set<String> permissions) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        Set<String> trustedPermissions = permissions == null ? Set.of() : permissions;
        return menus.stream()
                .filter(menu -> isVisibleMenu(menu, trustedPermissions))
                .toList();
    }

    private boolean isVisibleMenu(Map<String, Object> menu, Set<String> permissions) {
        Object permissionKey = menu == null ? null : menu.get("permissionKey");
        if (!(permissionKey instanceof String text) || !StringUtils.hasText(text)) {
            return true;
        }
        String normalized = text.trim();
        return permissions.contains("*") || permissions.contains(normalized);
    }

    private Map<String, Object> searchUsers(ToolExecutionContext context) {
        String keyword = stringArg(context.arguments(), "keyword", null);
        String status = stringArg(context.arguments(), "status", null);
        int limit = limitArg(context.arguments());
        AiIamQueryFacade.UserSearchResult searchResult = iamQueryFacade.searchUsers(keyword, status, limit);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", searchResult.items());
        data.put("limit", limit);
        data.put("count", searchResult.items().size());
        data.put("total", searchResult.total());
        return data;
    }

    private Map<String, Object> createUser(ToolExecutionContext context) {
        SystemDTO.UserUpsertRequest request = objectMapper.convertValue(context.arguments(), SystemDTO.UserUpsertRequest.class);
        if (!StringUtils.hasText(request.getStatus())) {
            request.setStatus("ENABLED");
        }
        SystemVO.UserDetailVO user = systemManagementAppService.createUser(context.currentUser(), request);
        return Map.of("user", user);
    }

    private Map<String, Object> updateUser(ToolExecutionContext context) {
        Long userId = longArg(context.arguments(), "userId");
        if (userId == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "userId 不能为空");
        }
        SystemVO.UserDetailVO existing = systemManagementAppService.getUser(context.currentUser(), userId);
        requireTargetUserUuid(context, existing);
        SystemDTO.UserUpsertRequest request = mergeUserRequest(existing, context.arguments());
        SystemVO.UserDetailVO user = systemManagementAppService.updateUser(context.currentUser(), userId, request);
        return Map.of("user", user);
    }

    private Map<String, Object> updateUserStatus(ToolExecutionContext context) {
        Long userId = longArg(context.arguments(), "userId");
        String status = stringArg(context.arguments(), "status", null);
        if (userId == null || !StringUtils.hasText(status)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "userId 和 status 不能为空");
        }
        SystemVO.UserDetailVO existing = systemManagementAppService.getUser(context.currentUser(), userId);
        requireTargetUserUuid(context, existing);
        if (isActorUser(context, userId) && "DISABLED".equalsIgnoreCase(status)) {
            throw new BizException(ErrorCode.FORBIDDEN, "不允许通过 AI 禁用当前登录账号");
        }
        if (Long.valueOf(1001L).equals(userId) && "DISABLED".equalsIgnoreCase(status)) {
            throw new BizException(ErrorCode.FORBIDDEN, "不允许通过 AI 禁用默认管理员账户");
        }
        boolean updated = systemManagementAppService.updateUserStatus(context.currentUser(), userId, status);
        return Map.of("updated", updated, "userId", userId, "status", status.toUpperCase(Locale.ROOT));
    }

    private Map<String, Object> deleteUser(ToolExecutionContext context) {
        Long userId = longArg(context.arguments(), "userId");
        if (userId == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "userId 不能为空");
        }
        SystemVO.UserDetailVO existing = systemManagementAppService.getUser(context.currentUser(), userId);
        requireTargetUserUuid(context, existing);
        if (isActorUser(context, userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "不允许通过 AI 删除当前登录账号");
        }
        if (Long.valueOf(1001L).equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "不允许通过 AI 删除默认管理员账户");
        }
        boolean deleted = systemManagementAppService.deleteUser(context.currentUser(), userId);
        return Map.of("deleted", deleted, "userId", userId);
    }

    private String requireTargetUserUuid(ToolExecutionContext context, SystemVO.UserDetailVO existing) {
        String expectedUserUuid = stringArg(context.arguments(), "userUuid", null);
        if (!StringUtils.hasText(expectedUserUuid)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "userUuid must not be blank");
        }
        if (existing == null || !StringUtils.hasText(existing.getUserUuid())) {
            throw new BizException(ErrorCode.FORBIDDEN, "Target user identity cannot be verified");
        }
        String actualUserUuid = existing.getUserUuid().trim();
        if (!expectedUserUuid.trim().equals(actualUserUuid)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Target user identity mismatch");
        }
        return actualUserUuid;
    }

    private Map<String, Object> updateCurrentAvatar(ToolExecutionContext context) {
        String avatarUrl = stringArg(context.arguments(), "avatarUrl", null);
        if (!StringUtils.hasText(avatarUrl)) {
            Long fileId = longArg(context.arguments(), "fileId");
            avatarUrl = resolveAvatarUrlFromFile(context, fileId);
        }
        var user = systemManagementAppService.updateCurrentUserAvatar(context.currentUser(), avatarUrl);
        return Map.of("currentUser", user);
    }

    private String resolveAvatarUrlFromFile(ToolExecutionContext context, Long fileId) {
        if (fileId == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "avatarUrl 或 fileId 不能为空");
        }
        if (fileInternalApi == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件服务不可用");
        }
        CurrentUser currentUser = context.currentUser();
        FileObjectDTO file = fileInternalApi.getFileForUser(
                fileId,
                context.actorUserId(),
                context.actorUserUuid(),
                context.actorUsername(),
                false,
                false
        );
        if (file == null || !StringUtils.hasText(file.publicUrl())) {
            throw new BizException(ErrorCode.NOT_FOUND, "头像文件不存在或无权使用");
        }
        return file.publicUrl();
    }

    private SystemDTO.UserUpsertRequest mergeUserRequest(SystemVO.UserDetailVO existing, Map<String, Object> arguments) {
        SystemDTO.UserUpsertRequest request = new SystemDTO.UserUpsertRequest();
        request.setUsername(stringArg(arguments, "username", existing.getUsername()));
        request.setMobile(stringArg(arguments, "mobile", existing.getMobile()));
        request.setNickname(stringArg(arguments, "nickname", existing.getNickname()));
        request.setRealName(stringArg(arguments, "realName", existing.getRealName()));
        request.setAvatarUrl(stringArg(arguments, "avatarUrl", existing.getAvatarUrl()));
        request.setEmail(stringArg(arguments, "email", existing.getEmail()));
        request.setBirthMonth(stringArg(arguments, "birthMonth", existing.getBirthMonth()));
        request.setGender(stringArg(arguments, "gender", existing.getGender()));
        request.setRegion(stringArg(arguments, "region", existing.getRegion()));
        request.setAvailableTime(stringArg(arguments, "availableTime", existing.getAvailableTime()));
        request.setIdCardNumber(stringArg(arguments, "idCardNumber", existing.getIdCardNumber()));
        request.setStatus(stringArg(arguments, "status", existing.getStatus()));
        request.setRoleIds(existing.getRoleIds());
        request.setDeptIds(existing.getDeptIds());
        request.setPrimaryDeptId(existing.getPrimaryDeptId());
        if (arguments != null && arguments.containsKey("roleIds")) {
            request.setRoleIds(longListArg(arguments.get("roleIds")));
        }
        if (arguments != null && arguments.containsKey("deptIds")) {
            request.setDeptIds(longListArg(arguments.get("deptIds")));
        }
        if (arguments != null && arguments.containsKey("primaryDeptId")) {
            request.setPrimaryDeptId(longValue(arguments.get("primaryDeptId"), "primaryDeptId"));
        }
        return request;
    }

    private Map<String, Object> createRole(ToolExecutionContext context) {
        SystemDTO.RoleUpsertRequest request = objectMapper.convertValue(withoutKeys(context.arguments(), "roleId"), SystemDTO.RoleUpsertRequest.class);
        if (!StringUtils.hasText(request.getRoleType())) {
            request.setRoleType("BUSINESS");
        }
        SystemVO.RoleDetailVO role = systemManagementAppService.createRole(context.currentUser(), request);
        return Map.of("role", role);
    }

    private Map<String, Object> updateRole(ToolExecutionContext context) {
        Long roleId = requireLong(context.arguments(), "roleId");
        SystemDTO.RoleUpsertRequest request = objectMapper.convertValue(withoutKeys(context.arguments(), "roleId"), SystemDTO.RoleUpsertRequest.class);
        SystemVO.RoleDetailVO role = systemManagementAppService.updateRole(context.currentUser(), roleId, request);
        return Map.of("role", role);
    }

    private Map<String, Object> updateRolePermissions(ToolExecutionContext context) {
        Long roleId = requireLong(context.arguments(), "roleId");
        List<String> permissionKeys = stringListArg(context.arguments().get("permissionKeys"));
        boolean updated = systemManagementAppService.updateRolePermissions(context.currentUser(), roleId, permissionKeys);
        return Map.of("updated", updated, "roleId", roleId, "permissionKeys", permissionKeys);
    }

    private Map<String, Object> deleteRole(ToolExecutionContext context) {
        Long roleId = requireLong(context.arguments(), "roleId");
        boolean deleted = systemManagementAppService.deleteRole(context.currentUser(), roleId);
        return Map.of("deleted", deleted, "roleId", roleId);
    }

    private Map<String, Object> createMenu(ToolExecutionContext context) {
        SystemDTO.MenuUpsertRequest request = objectMapper.convertValue(context.arguments(), SystemDTO.MenuUpsertRequest.class);
        defaultMenuFields(request);
        SystemVO.MenuVO menu = systemManagementAppService.createMenu(context.currentUser(), request);
        return Map.of("menu", menu);
    }

    private Map<String, Object> updateMenu(ToolExecutionContext context) {
        Long menuId = requireLong(context.arguments(), "menuId");
        SystemDTO.MenuUpsertRequest request = objectMapper.convertValue(withoutKeys(context.arguments(), "menuId"), SystemDTO.MenuUpsertRequest.class);
        defaultMenuFields(request);
        SystemVO.MenuVO menu = systemManagementAppService.updateMenu(context.currentUser(), menuId, request);
        return Map.of("menu", menu);
    }

    private Map<String, Object> updateMenuStatus(ToolExecutionContext context) {
        Long menuId = requireLong(context.arguments(), "menuId");
        String status = requiredString(context.arguments(), "status");
        boolean updated = systemManagementAppService.updateMenuStatus(context.currentUser(), menuId, status);
        return Map.of("updated", updated, "menuId", menuId, "status", status);
    }

    private Map<String, Object> deleteMenu(ToolExecutionContext context) {
        Long menuId = requireLong(context.arguments(), "menuId");
        boolean deleted = systemManagementAppService.deleteMenu(context.currentUser(), menuId);
        return Map.of("deleted", deleted, "menuId", menuId);
    }

    private Map<String, Object> createDictType(ToolExecutionContext context) {
        SystemDTO.DictTypeUpsertRequest request = objectMapper.convertValue(context.arguments(), SystemDTO.DictTypeUpsertRequest.class);
        if (!StringUtils.hasText(request.getStatus())) {
            request.setStatus("ENABLED");
        }
        SystemVO.DictTypeVO dictType = systemManagementAppService.createDictType(context.currentUser(), request);
        return Map.of("dictType", dictType);
    }

    private Map<String, Object> updateDictType(ToolExecutionContext context) {
        Long dictTypeId = requireLong(context.arguments(), "dictTypeId");
        SystemDTO.DictTypeUpsertRequest request = objectMapper.convertValue(withoutKeys(context.arguments(), "dictTypeId"), SystemDTO.DictTypeUpsertRequest.class);
        SystemVO.DictTypeVO dictType = systemManagementAppService.updateDictType(context.currentUser(), dictTypeId, request);
        return Map.of("dictType", dictType);
    }

    private Map<String, Object> deleteDictType(ToolExecutionContext context) {
        Long dictTypeId = requireLong(context.arguments(), "dictTypeId");
        boolean deleted = systemManagementAppService.deleteDictType(context.currentUser(), dictTypeId);
        return Map.of("deleted", deleted, "dictTypeId", dictTypeId);
    }

    private Map<String, Object> createDictItem(ToolExecutionContext context) {
        Long dictTypeId = requireLong(context.arguments(), "dictTypeId");
        SystemDTO.DictItemUpsertRequest request = objectMapper.convertValue(withoutKeys(context.arguments(), "dictTypeId"), SystemDTO.DictItemUpsertRequest.class);
        if (request.getSortNo() == null) {
            request.setSortNo(0);
        }
        if (!StringUtils.hasText(request.getStatus())) {
            request.setStatus("ENABLED");
        }
        SystemVO.DictItemVO item = systemManagementAppService.createDictItem(context.currentUser(), dictTypeId, request);
        return Map.of("dictItem", item);
    }

    private Map<String, Object> updateDictItem(ToolExecutionContext context) {
        Long dictTypeId = requireLong(context.arguments(), "dictTypeId");
        Long itemId = requireLong(context.arguments(), "itemId");
        SystemDTO.DictItemUpsertRequest request = objectMapper.convertValue(withoutKeys(context.arguments(), "dictTypeId", "itemId"), SystemDTO.DictItemUpsertRequest.class);
        SystemVO.DictItemVO item = systemManagementAppService.updateDictItem(context.currentUser(), dictTypeId, itemId, request);
        return Map.of("dictItem", item);
    }

    private Map<String, Object> deleteDictItem(ToolExecutionContext context) {
        Long dictTypeId = requireLong(context.arguments(), "dictTypeId");
        Long itemId = requireLong(context.arguments(), "itemId");
        boolean deleted = systemManagementAppService.deleteDictItem(context.currentUser(), dictTypeId, itemId);
        return Map.of("deleted", deleted, "dictTypeId", dictTypeId, "itemId", itemId);
    }

    private Map<String, Object> createConfig(ToolExecutionContext context) {
        ensureAiConfigKeyAllowed(stringArg(context.arguments(), "configKey", null), "鏁忔劅閰嶇疆涓嶅厑璁搁€氳繃 AI 宸ュ叿淇敼: ");
        ensureNonSensitiveConfig(context.arguments());
        SystemDTO.ConfigUpsertRequest request = objectMapper.convertValue(context.arguments(), SystemDTO.ConfigUpsertRequest.class);
        SystemVO.ConfigVO config = systemManagementAppService.createConfig(context.currentUser(), request);
        return Map.of("config", config);
    }

    private Map<String, Object> updateConfig(ToolExecutionContext context) {
        Long configId = requireLong(context.arguments(), "configId");
        ensureAiConfigUpdateAllowed(context, configId);
        ensureAiConfigKeyAllowed(stringArg(context.arguments(), "configKey", null), "鏁忔劅閰嶇疆涓嶅厑璁搁€氳繃 AI 宸ュ叿淇敼: ");
        ensureNonSensitiveConfig(context.arguments());
        SystemDTO.ConfigUpsertRequest request = objectMapper.convertValue(withoutKeys(context.arguments(), "configId"), SystemDTO.ConfigUpsertRequest.class);
        SystemVO.ConfigVO config = systemManagementAppService.updateConfig(context.currentUser(), configId, request);
        return Map.of("config", config);
    }

    private Map<String, Object> updateBrandingSettings(ToolExecutionContext context) {
        SystemDTO.BrandingSettingsRequest request = objectMapper.convertValue(context.arguments(), SystemDTO.BrandingSettingsRequest.class);
        return Map.of("brandingSettings", systemManagementAppService.updateBrandingSettings(context.currentUser(), request));
    }

    private Map<String, Object> updateAgreementSettings(ToolExecutionContext context) {
        SystemDTO.AgreementSettingsRequest request = objectMapper.convertValue(context.arguments(), SystemDTO.AgreementSettingsRequest.class);
        return Map.of("agreementSettings", systemManagementAppService.updateAgreementSettings(context.currentUser(), request));
    }

    private Map<String, Object> updateWatermarkSettings(ToolExecutionContext context) {
        SystemDTO.WatermarkSettingsRequest request = objectMapper.convertValue(context.arguments(), SystemDTO.WatermarkSettingsRequest.class);
        return Map.of("watermarkSettings", systemManagementAppService.updateWatermarkSettings(context.currentUser(), request));
    }

    private Map<String, Object> updateFloatingWindowSettings(ToolExecutionContext context) {
        SystemDTO.FloatingWindowSettingsRequest request = objectMapper.convertValue(context.arguments(), SystemDTO.FloatingWindowSettingsRequest.class);
        return Map.of("floatingWindowSettings", systemManagementAppService.updateFloatingWindowSettings(context.currentUser(), request));
    }

    private void defaultMenuFields(SystemDTO.MenuUpsertRequest request) {
        if (request.getParentId() == null) {
            request.setParentId(0L);
        }
        if (!StringUtils.hasText(request.getMenuType())) {
            request.setMenuType("MENU");
        }
        if (request.getSortNo() == null) {
            request.setSortNo(0);
        }
        if (!StringUtils.hasText(request.getStatus())) {
            request.setStatus("ENABLED");
        }
    }

    private void ensureAiConfigUpdateAllowed(ToolExecutionContext context, Long configId) {
        if (configId == null) {
            return;
        }
        SystemVO.ConfigVO existing = requireSystemManagementAppService().getConfig(context.currentUser(), configId);
        ensureAiConfigKeyAllowed(existing == null ? null : existing.getConfigKey(), "鏁忔劅閰嶇疆涓嶅厑璁搁€氳繃 AI 宸ュ叿淇敼: ");
    }

    private void ensureAiConfigKeyAllowed(String configKey, String messagePrefix) {
        if (!StringUtils.hasText(configKey)) {
            return;
        }
        if (!isAiManageableConfigKey(configKey)) {
            throw new BizException(ErrorCode.FORBIDDEN, messagePrefix + configKey);
        }
    }

    private boolean isAiManageableConfigKey(String configKey) {
        return AiConfigAccessPolicy.isAiManageableConfigKey(configKey);
    }

    private SystemManagementAppService requireSystemManagementAppService() {
        if (systemManagementAppService == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "System management service is not available");
        }
        return systemManagementAppService;
    }

    private void ensureNonSensitiveConfig(Map<String, Object> arguments) {
        String configKey = stringArg(arguments, "configKey", null);
        if (StringUtils.hasText(configKey) && looksSensitive(configKey)) {
            throw new BizException(ErrorCode.FORBIDDEN, "敏感配置不允许通过 AI 工具修改: " + configKey);
        }
        String configValue = stringArg(arguments, "configValue", null);
        if (StringUtils.hasText(configValue) && looksSensitive(configValue)) {
            throw new BizException(ErrorCode.FORBIDDEN, "疑似敏感配置值不允许通过 AI 工具修改");
        }
    }

    private Map<String, Object> withoutKeys(Map<String, Object> arguments, String... keys) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>(arguments);
        for (String key : keys) {
            copy.remove(key);
        }
        return copy;
    }

    private Map<String, Object> searchFiles(ToolExecutionContext context) {
        String keyword = stringArg(context.arguments(), "keyword", null);
        String contentType = stringArg(context.arguments(), "contentType", null);
        String status = stringArg(context.arguments(), "status", "ENABLED");
        int limit = limitArg(context.arguments());
        if (fileInternalApi == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件服务不可用");
        }
        CurrentUser currentUser = context.currentUser();
        List<Map<String, Object>> files = fileInternalApi.searchFilesForUser(
                        context.actorUserId(),
                        context.actorUserUuid(),
                        context.actorUsername(),
                        keyword,
                        contentType,
                        status,
                        false,
                        limit
                )
                .stream()
                .map(this::toFileToolItem)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", files);
        data.put("limit", limit);
        data.put("count", files.size());
        return data;
    }

    private Map<String, Object> toFileToolItem(FileObjectDTO file) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", file.id());
        item.put("originalFileName", file.originalFileName());
        item.put("fileExtension", file.fileExtension());
        item.put("contentType", file.mimeType());
        item.put("fileSizeBytes", file.fileSizeBytes());
        item.put("uploadedBy", file.uploadedBy());
        item.put("uploadedByName", file.uploadedByName());
        item.put("category", file.category());
        item.put("tags", file.tags());
        item.put("status", file.status());
        item.put("previewMode", file.previewMode());
        item.put("createdAt", file.createdAt());
        item.put("updatedAt", file.updatedAt());
        return item;
    }

    private Map<String, Object> searchAiCallAuditLogs(ToolExecutionContext context) {
        Long employeeId = longArg(context.arguments(), "employeeId");
        String skillCode = stringArg(context.arguments(), "skillCode", null);
        String resultStatus = stringArg(context.arguments(), "resultStatus", null);
        int limit = limitArg(context.arguments());
        List<Object> args = new java.util.ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select id, conversation_id as conversationId, employee_id as employeeId,
                       skill_code as skillCode, tool_name as toolName, permission_mode as permissionMode,
                       confirm_required as confirmRequired, confirm_result as confirmResult,
                       result_status as resultStatus, detail_message as detailMessage,
                       create_time as createdAt
                from ai_tool_audit_log
                where is_deleted = 0
                """);
        if (employeeId != null) {
            sql.append(" and employee_id = ?");
            args.add(employeeId);
        }
        if (StringUtils.hasText(skillCode)) {
            sql.append(" and skill_code like ?");
            args.add(like(skillCode));
        }
        if (StringUtils.hasText(resultStatus)) {
            sql.append(" and result_status = ?");
            args.add(resultStatus.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" order by id desc limit ?");
        args.add(limit);
        List<Map<String, Object>> logs = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", logs);
        data.put("limit", limit);
        data.put("count", logs.size());
        return data;
    }

    private void requireEmployee(Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "AI tool execution requires a valid digital employee");
        }
        boolean exists = jdbcTemplate.exists(
                "select 1 from ai_employee where id = ? and is_deleted = 0 and enabled = 1 limit 1",
                employeeId
        );
        if (!exists) {
            throw new BizException(ErrorCode.NOT_FOUND, "数字员工不存在或已禁用");
        }
    }

    private void recordToolAuditLog(
            CurrentUser currentUser,
            AiDTO.ToolExecuteRequest request,
            NativeTool tool,
            boolean confirmed,
            String permissionMode,
            String resultStatus,
            String detailMessage,
            Map<String, Object> responsePayload
    ) {
        int inserted = jdbcTemplate.update(
                """
                        insert into ai_tool_audit_log (
                            conversation_id, employee_id, owner_user_id, owner_user_uuid, skill_code, tool_name, permission_mode,
                            confirm_required, confirm_result, result_status, detail_message,
                            request_payload_json, response_payload_json, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                request.getConversationId(),
                request.getEmployeeId(),
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                tool.code(),
                tool.code(),
                permissionMode,
                tool.needConfirm() ? 1 : 0,
                confirmed ? 1 : 0,
                resultStatus,
                truncate(detailMessage, 512),
                toJson(Map.of("toolCode", tool.code(), "arguments", safeMap(request.getArguments()))),
                toJson(responsePayload == null ? Map.of() : responsePayload),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        if (inserted != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "AI tool audit changed, please retry");
        }
    }

    private void recordFailedToolAuditLog(
            CurrentUser currentUser,
            AiDTO.ToolExecuteRequest request,
            NativeTool tool,
            boolean confirmed,
            RuntimeException exception
    ) {
        try {
            String resultStatus = exception instanceof BizException ? "FAIL" : "ERROR";
            String permissionMode = exception instanceof BizException bizException && ErrorCode.FORBIDDEN.equals(bizException.getErrorCode())
                    ? "deny"
                    : "allow";
            recordToolAuditLog(
                    currentUser,
                    request,
                    tool,
                    confirmed,
                    permissionMode,
                    resultStatus,
                    defaultErrorMessage(exception),
                    Map.of("error", defaultErrorMessage(exception))
            );
        } catch (RuntimeException auditException) {
            log.warn("Failed to record AI native tool audit toolCode={} employeeId={}", tool.code(), request.getEmployeeId(), auditException);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private String defaultErrorMessage(RuntimeException exception) {
        if (exception instanceof BizException bizException && StringUtils.hasText(bizException.getUserMessage())) {
            return bizException.getUserMessage();
        }
        return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : "AI 工具调用失败";
    }

    private boolean looksSensitive(String value) {
        return AiConfigAccessPolicy.looksSensitive(value);
    }

    private boolean hasPermission(CurrentUser currentUser, String permissionKey) {
        if (!isTrustedCurrentUser(currentUser)) {
            return false;
        }
        Set<String> permissions = trustedPermissions(currentUser);
        return permissions.contains("*")
                || permissions.contains(permissionKey)
                || permissions.stream()
                .filter(permission -> StringUtils.hasText(permission) && permission.endsWith("*"))
                .anyMatch(permission -> permissionKey.startsWith(permission.substring(0, permission.length() - 1)));
    }

    private Set<String> trustedPermissions(CurrentUser currentUser) {
        if (!isTrustedCurrentUser(currentUser)) {
            return Set.of();
        }
        return currentUser.getPermissions() == null ? Set.of() : currentUser.getPermissions();
    }

    private CurrentUser refreshTrustedCurrentUserIfAvailable(CurrentUser currentUser) {
        if (!isTrustedCurrentUser(currentUser)) {
            return currentUser;
        }
        if (sessionAuthenticationService == null && permissionSnapshotService == null) {
            return currentUser;
        }
        return refreshTrustedCurrentUser(currentUser);
    }

    private CurrentUser refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!isTrustedCurrentUser(currentUser)) {
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
                    ),
                    ErrorCode.UNAUTHORIZED,
                    "Trusted user context is invalid"
            );
            copyTrustedCurrentUser(currentUser, refreshedUser);
            return currentUser;
        }
        if (permissionSnapshotService == null) {
            return currentUser;
        }
        Long userId = currentUser.getUserId();
        String userUuid = currentUser.getUserUuid();
        if (userId == null || userId <= 0 || !StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user context is invalid");
        }
        String normalizedUserUuid = userUuid.trim();
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user context is invalid");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid()) || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user context is invalid");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(userSnapshot.username());
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        PermissionSnapshotService.PermissionSnapshot snapshot = currentUser.getSimulatedRoleId() != null
                ? permissionSnapshotService.loadRoleSnapshot(currentUser.getSimulatedRoleId())
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        CurrentUser refreshed = new CurrentUser(
                currentUser.getUserId(),
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
        refreshed.setSimulatedRoleId(currentUser.getSimulatedRoleId());
        refreshed.setLoginType(currentUser.getLoginType());
        copyTrustedCurrentUser(currentUser, refreshed);
        return currentUser;
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(
            SessionAuthenticationService.AuthenticatedAccess authenticatedAccess,
            ErrorCode errorCode,
            String message
    ) {
        CurrentUser refreshedUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!isTrustedCurrentUser(refreshedUser)) {
            throw new BizException(errorCode, message);
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
        target.setSimulatedRoleId(source.getSimulatedRoleId());
        target.setLoginType(source.getLoginType());
    }

    private boolean isTrustedCurrentUser(CurrentUser currentUser) {
        return AuthenticationTrustSupport.isTrustedCurrentUser(currentUser);
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    private Long longArg(Map<String, Object> arguments, String key) {
        Object value = arguments == null ? null : arguments.get(key);
        return longValue(value, key);
    }

    private Long requireLong(Map<String, Object> arguments, String key) {
        Long value = longArg(arguments, key);
        if (value == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, key + " 不能为空");
        }
        return value;
    }

    private Long longValue(Object value, String key) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, key + " 必须是数字");
        }
    }

    private List<Long> longListArg(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> values) {
            return values.stream()
                    .map(item -> longValue(item, "列表项"))
                    .filter(item -> item != null && item > 0)
                    .toList();
        }
        throw new BizException(ErrorCode.VALIDATION_ERROR, "参数必须是数字数组");
    }

    private List<String> stringListArg(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> values) {
            return values.stream()
                    .map(item -> item == null ? null : String.valueOf(item).trim())
                    .filter(StringUtils::hasText)
                    .toList();
        }
        throw new BizException(ErrorCode.VALIDATION_ERROR, "参数必须是字符串数组");
    }

    private String maskMobile(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.length() < 7) {
            return "***";
        }
        return text.substring(0, 3) + "****" + text.substring(text.length() - 4);
    }

    private String maskEmail(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        int at = text.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return text.charAt(0) + "***" + text.substring(at);
    }

    private String stringArg(Map<String, Object> arguments, String key, String defaultValue) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private String requiredString(Map<String, Object> arguments, String key) {
        String value = stringArg(arguments, key, null);
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, key + " 不能为空");
        }
        return value;
    }

    private int limitArg(Map<String, Object> arguments) {
        Object value = arguments == null ? null : arguments.get("limit");
        if (value == null) {
            return DEFAULT_LIMIT;
        }
        int parsed;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException exception) {
                parsed = DEFAULT_LIMIT;
            }
        }
        return Math.max(1, Math.min(MAX_LIMIT, parsed));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Long requireLogin(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Login required");
        }
        return currentUser.getUserId();
    }

    private String trustedUsername(CurrentUser currentUser) {
        requireLogin(currentUser);
        return currentUser.getUsername();
    }

    private boolean isActorUser(ToolExecutionContext context, Long userId) {
        return context != null
                && userId != null
                && userId.equals(context.actorUserId())
                && StringUtils.hasText(context.actorUserUuid())
                && context.currentUser() != null
                && StringUtils.hasText(context.currentUser().getUserUuid())
                && context.actorUserUuid().trim().equals(context.currentUser().getUserUuid().trim());
    }

    private record ToolExecutionContext(CurrentUser currentUser, Map<String, Object> arguments, Long actorUserId, String actorUserUuid, String actorUsername) {
        private ToolExecutionContext(CurrentUser currentUser, Map<String, Object> arguments) {
            this(
                    currentUser,
                    arguments,
                    isTrustedContextUser(currentUser) ? currentUser.getUserId() : null,
                    isTrustedContextUser(currentUser) ? currentUser.getUserUuid() : null,
                    isTrustedContextUser(currentUser) ? currentUser.getUsername() : null
            );
        }

        private static boolean isTrustedContextUser(CurrentUser currentUser) {
            return AuthenticationTrustSupport.isTrustedCurrentUser(currentUser);
        }
    }

    @FunctionalInterface
    private interface ToolExecutor {
        Map<String, Object> execute(ToolExecutionContext context);
    }

    private record NativeTool(
            String code,
            String name,
            String category,
            String description,
            String riskLevel,
            boolean readOnly,
            boolean needConfirm,
            String requiredPermission,
            Map<String, Object> inputSchema,
            ToolExecutor executor
    ) {
        AiVO.ToolVO toVO() {
            AiVO.ToolVO vo = new AiVO.ToolVO();
            vo.setToolCode(code);
            vo.setToolName(name);
            vo.setCategory(category);
            vo.setDescription(description);
            vo.setRiskLevel(riskLevel);
            vo.setReadOnly(readOnly);
            vo.setNeedConfirm(needConfirm);
            vo.setRequiredPermission(requiredPermission);
            vo.setInputSchema(inputSchema);
            return vo;
        }
    }
}
