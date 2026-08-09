package com.lumira.saas.modules.ai.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.ai.AiSystemManagementToolPort;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AiConfigAccessPolicy;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.authorization.AuthorizationDecision;
import com.lumira.common.security.authorization.AuthorizationRequest;
import com.lumira.common.security.authorization.AuthorizationService;
import com.lumira.common.security.authorization.AuthorizationVerdict;
import com.lumira.saas.modules.ai.integration.AiTrustedSessionResolver;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.repository.AiNativeToolRuntimeRepository;
import com.lumira.saas.modules.ai.vo.AiVO;
import com.lumira.saas.modules.ai.integration.AiPermissionSnapshotResolver;
import com.lumira.common.security.PermissionGuard;
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

    private final AiNativeToolRuntimeRepository nativeToolRepository;
    private final PermissionGuard permissionGuard;
    private final AuthorizationService authorizationService;
    private final AiSkillPermissionChecker aiSkillPermissionChecker;
    private final ObjectMapper objectMapper;
    private final AiPermissionSnapshotResolver permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final AiTrustedSessionResolver sessionAuthenticationService;
    private final AiSystemManagementToolPort systemManagementToolPort;
    private final AiPlatformQueryFacade platformQueryFacade;
    private final AiIamQueryFacade iamQueryFacade;
    private final FileInternalApi fileInternalApi;
    private final Map<String, NativeTool> tools;
    private final boolean writeToolsEnabled;
    private final boolean enforceTrustedUserResolution;

    DefaultAiNativeToolRuntimeService(
            AiNativeToolRuntimeRepository nativeToolRepository,
            PermissionGuard permissionGuard,
            AuthorizationService authorizationService,
            AiSkillPermissionChecker aiSkillPermissionChecker,
            ObjectMapper objectMapper,
            AiPlatformQueryFacade platformQueryFacade,
            AiIamQueryFacade iamQueryFacade,
            AiPermissionSnapshotResolver permissionSnapshotService,
            AiSystemManagementToolPort systemManagementToolPort,
            FileInternalApi fileInternalApi,
            @Value("${saas.ai.native-tools.write-enabled:false}") boolean writeToolsEnabled
    ) {
        this(
                nativeToolRepository,
                permissionGuard,
                authorizationService,
                aiSkillPermissionChecker,
                objectMapper,
                platformQueryFacade,
                iamQueryFacade,
                permissionSnapshotService,
                null,
                null,
                systemManagementToolPort,
                fileInternalApi,
                writeToolsEnabled,
                false
        );
    }

    @Autowired
    DefaultAiNativeToolRuntimeService(
            AiNativeToolRuntimeRepository nativeToolRepository,
            PermissionGuard permissionGuard,
            AuthorizationService authorizationService,
            AiSkillPermissionChecker aiSkillPermissionChecker,
            ObjectMapper objectMapper,
            AiPlatformQueryFacade platformQueryFacade,
            AiIamQueryFacade iamQueryFacade,
            AiPermissionSnapshotResolver permissionSnapshotService,
            AiTrustedSessionResolver sessionAuthenticationService,
            AiSystemManagementToolPort systemManagementToolPort,
            FileInternalApi fileInternalApi,
            @Value("${saas.ai.native-tools.write-enabled:false}") boolean writeToolsEnabled
    ) {
        this(
                nativeToolRepository,
                permissionGuard,
                authorizationService,
                aiSkillPermissionChecker,
                objectMapper,
                platformQueryFacade,
                iamQueryFacade,
                permissionSnapshotService,
                null,
                sessionAuthenticationService,
                systemManagementToolPort,
                fileInternalApi,
                writeToolsEnabled,
                true
        );
    }

    DefaultAiNativeToolRuntimeService(
            AiNativeToolRuntimeRepository nativeToolRepository,
            PermissionGuard permissionGuard,
            AuthorizationService authorizationService,
            AiSkillPermissionChecker aiSkillPermissionChecker,
            ObjectMapper objectMapper,
            AiPlatformQueryFacade platformQueryFacade,
            AiIamQueryFacade iamQueryFacade,
            AiPermissionSnapshotResolver permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            AiTrustedSessionResolver sessionAuthenticationService,
            AiSystemManagementToolPort systemManagementToolPort,
            FileInternalApi fileInternalApi,
            boolean writeToolsEnabled
    ) {
        this(
                nativeToolRepository,
                permissionGuard,
                authorizationService,
                aiSkillPermissionChecker,
                objectMapper,
                platformQueryFacade,
                iamQueryFacade,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                systemManagementToolPort,
                fileInternalApi,
                writeToolsEnabled,
                false
        );
    }

    private DefaultAiNativeToolRuntimeService(
            AiNativeToolRuntimeRepository nativeToolRepository,
            PermissionGuard permissionGuard,
            AuthorizationService authorizationService,
            AiSkillPermissionChecker aiSkillPermissionChecker,
            ObjectMapper objectMapper,
            AiPlatformQueryFacade platformQueryFacade,
            AiIamQueryFacade iamQueryFacade,
            AiPermissionSnapshotResolver permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            AiTrustedSessionResolver sessionAuthenticationService,
            AiSystemManagementToolPort systemManagementToolPort,
            FileInternalApi fileInternalApi,
            boolean writeToolsEnabled,
            boolean enforceTrustedUserResolution
    ) {
        this.nativeToolRepository = nativeToolRepository;
        this.permissionGuard = permissionGuard;
        this.authorizationService = authorizationService;
        this.aiSkillPermissionChecker = aiSkillPermissionChecker;
        this.objectMapper = objectMapper;
        this.platformQueryFacade = platformQueryFacade;
        this.iamQueryFacade = iamQueryFacade;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.systemManagementToolPort = systemManagementToolPort;
        this.fileInternalApi = fileInternalApi;
        this.writeToolsEnabled = writeToolsEnabled;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
        this.tools = new LinkedHashMap<>(Map.of(
                "system.permission.snapshot", new NativeTool(
                        "system.permission.snapshot",
                        "Read current permission context",
                        "system",
                        "Returns the current logged-in user, roles, departments, and permissions for AI boundary checks.",
                        "LOW",
                        true,
                        false,
                        null,
                        Map.of("type", "object", "properties", Map.of()),
                        this::permissionSnapshot
                ),
                "system.menu.list", new NativeTool(
                        "system.menu.list",
                        "Read system menus and module entry points",
                        "system",
                        "Reads system menus, routes, permission keys, and status for the current account.",
                        "LOW",
                        true,
                        false,
                        "system:menu:view",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "status", Map.of("type", "string", "description", "Menu status, for example ENABLED"),
                                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", MAX_LIMIT)
                                )
                        ),
                        this::listMenus
                ),
                "system.config.read", new NativeTool(
                        "system.config.read",
                        "Read non-sensitive system configuration",
                        "system",
                        "Reads non-sensitive platform configuration by key. Sensitive keys such as password, secret, token, and key are rejected.",
                        "MEDIUM",
                        true,
                        false,
                        "system:config:view",
                        Map.of(
                                "type", "object",
                                "required", List.of("configKey"),
                                "properties", Map.of(
                                        "configKey", Map.of("type", "string", "description", "Configuration key")
                                )
                        ),
                        this::readScopedConfig
                ),
                "system.user.search", new NativeTool(
                        "system.user.search",
                        "Search system users",
                        "system",
                        "Searches platform users by keyword and status and returns masked basic information.",
                        "MEDIUM",
                        true,
                        false,
                        "system:user:view",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "keyword", Map.of("type", "string", "description", "Username, nickname, real name, phone, or email keyword"),
                                        "status", Map.of("type", "string", "description", "User status, for example ENABLED"),
                                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", MAX_LIMIT)
                                )
                        ),
                        this::searchUsers
                ),
                "file.object.search", new NativeTool(
                        "file.object.search",
                        "Search file objects",
                        "file",
                        "Searches file-center objects by keyword, type, and status. Regular users only see their own uploads; file admins can search platform files.",
                        "MEDIUM",
                        true,
                        false,
                        "system:file:view",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "keyword", Map.of("type", "string", "description", "File name, category, or tag keyword"),
                                        "contentType", Map.of("type", "string", "description", "MIME type prefix or full type"),
                                        "status", Map.of("type", "string", "description", "File status, for example ENABLED"),
                                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", MAX_LIMIT)
                                )
                        ),
                        this::searchFiles
                ),
                "audit.ai_call.search", new NativeTool(
                        "audit.ai_call.search",
                        "Search AI tool audit logs",
                        "audit",
                        "Searches AI call audit logs by employee, skill code, and result status.",
                        "MEDIUM",
                        true,
                        false,
                        "audit:view",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "employeeId", Map.of("type", "integer", "description", "AI employee ID"),
                                        "skillCode", Map.of("type", "string", "description", "Skill or tool code keyword"),
                                        "resultStatus", Map.of("type", "string", "description", "SUCCESS, FAIL, or ERROR"),
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
                "Create system user",
                "system",
                "Creates a system user within the current account's permission scope.",
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
                "Update system user",
                "system",
                "Updates user profile, roles, and departments within the current account's permission scope.",
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
                "Enable or disable system user",
                "system",
                "Enables or disables a user within the current account's permission scope.",
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
                "Delete system user",
                "system",
                "Deletes a user within the current account's permission scope.",
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
                "Update current user avatar",
                "profile",
                "Updates only the current logged-in user's avatar. Accepts avatarUrl or an uploaded fileId.",
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
        tools.put("system.role.create", writeTool("system.role.create", "Create role", "Creates a platform role.", "system:role:create", this::createRole));
        tools.put("system.role.update", writeTool("system.role.update", "Update role", "Updates platform role basics.", "system:role:update", this::updateRole));
        tools.put("system.role.permissions", writeTool("system.role.permissions", "Update role permissions", "Updates the permission set of a platform role.", "system:role:grant", this::updateRolePermissions));
        tools.put("system.role.delete", writeTool("system.role.delete", "Delete role", "Deletes a platform role.", "system:role:delete", this::deleteRole));
        tools.put("system.menu.create", writeTool("system.menu.create", "Create menu", "Creates a custom platform menu.", "system:menu:create", this::createMenu));
        tools.put("system.menu.update", writeTool("system.menu.update", "Update menu", "Updates a custom platform menu.", "system:menu:update", this::updateMenu));
        tools.put("system.menu.status", writeTool("system.menu.status", "Update menu status", "Updates platform menu status.", "system:menu:status", this::updateMenuStatus));
        tools.put("system.menu.delete", writeTool("system.menu.delete", "Delete menu", "Deletes a custom platform menu.", "system:menu:delete", this::deleteMenu));
        tools.put("system.dict_type.create", writeTool("system.dict_type.create", "Create dictionary type", "Creates a platform dictionary type.", "system:dict:create", this::createDictType));
        tools.put("system.dict_type.update", writeTool("system.dict_type.update", "Update dictionary type", "Updates a platform dictionary type.", "system:dict:update", this::updateDictType));
        tools.put("system.dict_type.delete", writeTool("system.dict_type.delete", "Delete dictionary type", "Deletes a non-system platform dictionary type.", "system:dict:delete", this::deleteDictType));
        tools.put("system.dict_item.create", writeTool("system.dict_item.create", "Create dictionary item", "Creates a platform dictionary item.", "system:dict:create", this::createDictItem));
        tools.put("system.dict_item.update", writeTool("system.dict_item.update", "Update dictionary item", "Updates a platform dictionary item.", "system:dict:update", this::updateDictItem));
        tools.put("system.dict_item.delete", writeTool("system.dict_item.delete", "Delete dictionary item", "Deletes a platform dictionary item.", "system:dict:delete", this::deleteDictItem));
        tools.put("system.config.create", writeTool("system.config.create", "Create system config", "Creates a non-sensitive platform configuration.", "system:config:update", this::createConfig));
        tools.put("system.config.update", writeTool("system.config.update", "Update system config", "Updates a non-sensitive platform configuration.", "system:config:update", this::updateConfig));
        tools.put("platform.branding.update", writeTool("platform.branding.update", "Update branding settings", "Updates site name, logo, footer, and other branding settings.", "system:config:update", this::updateBrandingSettings));
        tools.put("platform.agreement.update", writeTool("platform.agreement.update", "Update agreement settings", "Updates user agreement and privacy agreement settings.", "system:config:update", this::updateAgreementSettings));
        tools.put("platform.watermark.update", writeTool("platform.watermark.update", "Update watermark settings", "Updates platform watermark settings.", "system:config:update", this::updateWatermarkSettings));
        tools.put("platform.floating_window.update", writeTool("platform.floating_window.update", "Update floating window settings", "Updates global floating window settings.", "system:config:update", this::updateFloatingWindowSettings));
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
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Tool code cannot be blank");
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
            result.setMessage("Tool call succeeded");
            result.setData(data);
            result.setExecutedAt(LocalDateTime.now());
            recordToolAuditLog(currentUser, request, tool, confirmed, "allow", "SUCCESS", "AI tool call succeeded", data);
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
                platformQueryFacade.listMenus(context.currentUser(), status, limit),
                trustedPermissions(context.currentUser())
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", menus);
        data.put("limit", limit);
        data.put("count", menus.size());
        return data;
    }

    private Map<String, Object> readScopedConfig(ToolExecutionContext context) {
        ensureAiConfigKeyAllowed(stringArg(context.arguments(), "configKey", null), "该配置不在 AI 工具允许读取的范围内: ");
        return readConfig(context);
    }

    private Map<String, Object> readConfig(ToolExecutionContext context) {
        String configKey = stringArg(context.arguments(), "configKey", null);
        if (!StringUtils.hasText(configKey)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "configKey cannot be blank");
        }
        if (looksSensitive(configKey)) {
            throw new BizException(ErrorCode.FORBIDDEN, "敏感配置不允许通过 AI 工具读取: " + configKey);
        }
        Map<String, Object> config = platformQueryFacade.readConfig(context.currentUser(), configKey.trim());
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
        AiIamQueryFacade.UserSearchResult searchResult = iamQueryFacade.searchUsers(context.currentUser(), keyword, status, limit);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", searchResult.items());
        data.put("limit", limit);
        data.put("count", searchResult.items().size());
        data.put("total", searchResult.total());
        return data;
    }

    private Map<String, Object> createUser(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.CREATE_USER);
    }

    private Map<String, Object> updateUser(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.UPDATE_USER);
    }

    private Map<String, Object> updateUserStatus(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.UPDATE_USER_STATUS);
    }

    private Map<String, Object> deleteUser(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.DELETE_USER);
    }

    private Map<String, Object> updateCurrentAvatar(ToolExecutionContext context) {
        String avatarUrl = stringArg(context.arguments(), "avatarUrl", null);
        if (!StringUtils.hasText(avatarUrl)) {
            Long fileId = longArg(context.arguments(), "fileId");
            avatarUrl = resolveAvatarUrlFromFile(context, fileId);
        }
        return executeSystemManagement(
                new ToolExecutionContext(context.currentUser(), Map.of("avatarUrl", avatarUrl)),
                AiSystemManagementToolPort.Action.UPDATE_CURRENT_AVATAR
        );
    }

    private String resolveAvatarUrlFromFile(ToolExecutionContext context, Long fileId) {
        if (fileId == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "avatarUrl or fileId cannot be blank");
        }
        if (fileInternalApi == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "File service is unavailable");
        }
        CurrentUser currentUser = context.currentUser();
        FileObjectDTO file = fileInternalApi.getFileForUser(
                fileId,
                context.actorUserId(),
                context.actorUserUuid(),
                context.actorUsername(),
                false,
                false,
                currentUser == null ? null : currentUser.getSimulatedRoleId()
        );
        if (file == null || !StringUtils.hasText(file.publicUrl())) {
            throw new BizException(ErrorCode.NOT_FOUND, "Avatar file does not exist or cannot be used");
        }
        return file.publicUrl();
    }

    private Map<String, Object> createRole(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.CREATE_ROLE);
    }

    private Map<String, Object> updateRole(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.UPDATE_ROLE);
    }

    private Map<String, Object> updateRolePermissions(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.UPDATE_ROLE_PERMISSIONS);
    }

    private Map<String, Object> deleteRole(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.DELETE_ROLE);
    }

    private Map<String, Object> createMenu(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.CREATE_MENU);
    }

    private Map<String, Object> updateMenu(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.UPDATE_MENU);
    }

    private Map<String, Object> updateMenuStatus(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.UPDATE_MENU_STATUS);
    }

    private Map<String, Object> deleteMenu(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.DELETE_MENU);
    }

    private Map<String, Object> createDictType(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.CREATE_DICT_TYPE);
    }

    private Map<String, Object> updateDictType(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.UPDATE_DICT_TYPE);
    }

    private Map<String, Object> deleteDictType(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.DELETE_DICT_TYPE);
    }

    private Map<String, Object> createDictItem(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.CREATE_DICT_ITEM);
    }

    private Map<String, Object> updateDictItem(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.UPDATE_DICT_ITEM);
    }

    private Map<String, Object> deleteDictItem(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.DELETE_DICT_ITEM);
    }

    private Map<String, Object> createConfig(ToolExecutionContext context) {
        ensureAiConfigKeyAllowed(stringArg(context.arguments(), "configKey", null), "该配置不在 AI 工具允许管理的范围内: ");
        ensureNonSensitiveConfig(context.arguments());
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.CREATE_CONFIG);
    }

    private Map<String, Object> updateConfig(ToolExecutionContext context) {
        Long configId = requireLong(context.arguments(), "configId");
        ensureAiConfigUpdateAllowed(context, configId);
        ensureAiConfigKeyAllowed(stringArg(context.arguments(), "configKey", null), "该配置不在 AI 工具允许管理的范围内: ");
        ensureNonSensitiveConfig(context.arguments());
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.UPDATE_CONFIG);
    }

    private Map<String, Object> updateBrandingSettings(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.UPDATE_BRANDING);
    }

    private Map<String, Object> updateAgreementSettings(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.UPDATE_AGREEMENT);
    }

    private Map<String, Object> updateWatermarkSettings(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.UPDATE_WATERMARK);
    }

    private Map<String, Object> updateFloatingWindowSettings(ToolExecutionContext context) {
        return executeSystemManagement(context, AiSystemManagementToolPort.Action.UPDATE_FLOATING_WINDOW);
    }

    private void ensureAiConfigUpdateAllowed(ToolExecutionContext context, Long configId) {
        if (configId == null) {
            return;
        }
        AiManagedConfigKey existing = new AiManagedConfigKey(
                requireSystemManagementToolPort().findConfigKeyForAiUpdate(context.currentUser(), configId)
        );
        ensureAiConfigKeyAllowed(existing == null ? null : existing.getConfigKey(), "该配置不在 AI 工具允许管理的范围内: ");
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

    private AiSystemManagementToolPort requireSystemManagementToolPort() {
        if (systemManagementToolPort == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "System management tool port is not available");
        }
        return systemManagementToolPort;
    }

    private Map<String, Object> executeSystemManagement(
            ToolExecutionContext context,
            AiSystemManagementToolPort.Action action
    ) {
        return requireSystemManagementToolPort().execute(context.currentUser(), action, context.arguments());
    }

    private void ensureNonSensitiveConfig(Map<String, Object> arguments) {
        String configKey = stringArg(arguments, "configKey", null);
        if (StringUtils.hasText(configKey) && looksSensitive(configKey)) {
            throw new BizException(ErrorCode.FORBIDDEN, "敏感配置不允许通过 AI 工具修改: " + configKey);
        }
        String configValue = stringArg(arguments, "configValue", null);
        if (StringUtils.hasText(configValue) && looksSensitive(configValue)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Sensitive configuration values cannot be modified via AI tools");
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
            throw new BizException(ErrorCode.SYSTEM_ERROR, "File service is unavailable");
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
                        limit,
                        currentUser == null ? null : currentUser.getSimulatedRoleId()
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
        List<Map<String, Object>> logs = nativeToolRepository.findAuditLogs(employeeId, skillCode, resultStatus, limit);
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
        boolean exists = nativeToolRepository.existsEnabledEmployee(employeeId);
        if (!exists) {
            throw new BizException(ErrorCode.NOT_FOUND, "AI employee does not exist or has been disabled");
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
        int inserted = nativeToolRepository.appendAuditLog(new AiNativeToolRuntimeRepository.ToolAuditLog(
                request.getConversationId(),
                request.getEmployeeId(),
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                tool.code(),
                tool.code(),
                permissionMode,
                tool.needConfirm(),
                confirmed,
                resultStatus,
                truncate(detailMessage, 512),
                toJson(Map.of("toolCode", tool.code(), "arguments", safeMap(request.getArguments()))),
                toJson(responsePayload == null ? Map.of() : responsePayload)
        ), LocalDateTime.now());
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
        return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : "AI tool execution failed";
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
        if (!enforceTrustedUserResolution && sessionAuthenticationService == null && permissionSnapshotService == null) {
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
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
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
            if (!StringUtils.hasText(userSnapshot.username())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(userSnapshot.username().trim());
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        AiPermissionSnapshotResolver.PermissionSnapshot snapshot = simulatedRoleId != null
                ? permissionSnapshotService.loadGrantedRoleSnapshot(
                userId,
                normalizedUserUuid,
                simulatedRoleId
        )
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        if (snapshot == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
            }
            return currentUser;
        }
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
        refreshed.setSimulatedRoleId(simulatedRoleId);
        refreshed.setLoginType(currentUser.getLoginType());
        copyTrustedCurrentUser(currentUser, refreshed);
        return currentUser;
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(
            AiTrustedSessionResolver.AuthenticatedAccess authenticatedAccess,
            ErrorCode errorCode,
            String message
    ) {
        CurrentUser refreshedUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!isTrustedCurrentUser(refreshedUser)) {
            throw new BizException(errorCode, message);
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
            throw new BizException(ErrorCode.VALIDATION_ERROR, key + " cannot be blank");
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
            throw new BizException(ErrorCode.VALIDATION_ERROR, key + " must be a number");
        }
    }

    private List<Long> longListArg(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> values) {
            return values.stream()
                    .map(item -> longValue(item, "list item"))
                    .filter(item -> item != null && item > 0)
                    .toList();
        }
        throw new BizException(ErrorCode.VALIDATION_ERROR, "Parameter must be a numeric array");
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
        throw new BizException(ErrorCode.VALIDATION_ERROR, "Parameter must be a string array");
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
            throw new BizException(ErrorCode.VALIDATION_ERROR, key + " cannot be blank");
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

    private record AiManagedConfigKey(String configKey) {
        private String getConfigKey() {
            return configKey;
        }
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
