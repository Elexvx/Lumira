package com.lumira.saas.modules.ai.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.vo.AiVO;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface AiNativeToolRuntimeService {

    List<AiVO.ToolVO> listTools(CurrentUser currentUser);

    AiVO.ToolExecuteResultVO execute(CurrentUser currentUser, AiDTO.ToolExecuteRequest request);
}

@Service
@Primary
class DefaultAiNativeToolRuntimeService implements AiNativeToolRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiNativeToolRuntimeService.class);
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final MyBatisQueryOperations jdbcTemplate;
    private final PermissionGuard permissionGuard;
    private final AiSkillPermissionChecker aiSkillPermissionChecker;
    private final ObjectMapper objectMapper;
    private final SystemManagementAppService systemManagementAppService;
    private final AiPlatformQueryFacade platformQueryFacade;
    private final AiIamQueryFacade iamQueryFacade;
    private final FileInternalApi fileInternalApi;
    private final Map<String, NativeTool> tools;

    @Autowired
    DefaultAiNativeToolRuntimeService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionGuard permissionGuard,
            AiSkillPermissionChecker aiSkillPermissionChecker,
            ObjectMapper objectMapper,
            AiPlatformQueryFacade platformQueryFacade,
            AiIamQueryFacade iamQueryFacade,
            SystemManagementAppService systemManagementAppService,
            FileInternalApi fileInternalApi
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionGuard = permissionGuard;
        this.aiSkillPermissionChecker = aiSkillPermissionChecker;
        this.objectMapper = objectMapper;
        this.platformQueryFacade = platformQueryFacade == null ? new DefaultAiPlatformQueryFacade(jdbcTemplate) : platformQueryFacade;
        this.iamQueryFacade = iamQueryFacade == null ? new DefaultAiIamQueryFacade(jdbcTemplate) : iamQueryFacade;
        this.systemManagementAppService = systemManagementAppService;
        this.fileInternalApi = fileInternalApi;
        this.tools = new LinkedHashMap<>(Map.of(
                "system.permission.snapshot", new NativeTool(
                        "system.permission.snapshot",
                        "读取当前权限上下文",
                        "system",
                        "返回当前登录用户、租户、角色、部门和权限集合，供 AI 判断可访问边界。",
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
                        this::readConfig
                ),
                "system.user.search", new NativeTool(
                        "system.user.search",
                        "检索系统用户",
                        "system",
                        "按关键词和状态检索当前租户用户，返回脱敏后的基础资料。",
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
                        "按关键词、类型和状态检索文件中心对象；普通用户仅返回本人上传文件，全站文件管理员可检索租户文件。",
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
        registerWriteTools();
    }

    private void registerWriteTools() {
        tools.put("system.user.create", new NativeTool(
                "system.user.create",
                "新增系统用户",
                "system",
                "在当前租户和当前账号权限范围内新增系统用户。",
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
                "在当前租户和当前账号权限范围内编辑用户基础信息、角色和部门。",
                "HIGH",
                false,
                true,
                "system:user:update",
                Map.of("type", "object", "required", List.of("userId"), "properties", Map.ofEntries(
                        Map.entry("userId", Map.of("type", "integer")),
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
                "在当前租户和当前账号权限范围内启用或禁用用户。",
                "HIGH",
                false,
                true,
                "system:user:status",
                Map.of("type", "object", "required", List.of("userId", "status"), "properties", Map.of(
                        "userId", Map.of("type", "integer"),
                        "status", Map.of("type", "string", "enum", List.of("ENABLED", "DISABLED"))
                )),
                this::updateUserStatus
        ));
        tools.put("system.user.delete", new NativeTool(
                "system.user.delete",
                "删除系统用户",
                "system",
                "在当前租户和当前账号权限范围内删除用户。",
                "HIGH",
                false,
                true,
                "system:user:delete",
                Map.of("type", "object", "required", List.of("userId"), "properties", Map.of(
                        "userId", Map.of("type", "integer")
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
        tools.put("system.role.create", writeTool("system.role.create", "新增角色", "在当前租户新增角色。", "system:role:create", this::createRole));
        tools.put("system.role.update", writeTool("system.role.update", "编辑角色", "在当前租户编辑角色基础信息。", "system:role:update", this::updateRole));
        tools.put("system.role.permissions", writeTool("system.role.permissions", "配置角色权限", "在当前租户更新角色权限集合。", "system:role:permissions", this::updateRolePermissions));
        tools.put("system.role.delete", writeTool("system.role.delete", "删除角色", "在当前租户删除角色。", "system:role:delete", this::deleteRole));
        tools.put("system.menu.create", writeTool("system.menu.create", "新增菜单", "新增当前租户自定义菜单。", "system:menu:create", this::createMenu));
        tools.put("system.menu.update", writeTool("system.menu.update", "编辑菜单", "编辑当前租户自定义菜单。", "system:menu:update", this::updateMenu));
        tools.put("system.menu.status", writeTool("system.menu.status", "启停菜单", "更新当前租户菜单状态。", "system:menu:status", this::updateMenuStatus));
        tools.put("system.menu.delete", writeTool("system.menu.delete", "删除菜单", "删除当前租户自定义菜单。", "system:menu:delete", this::deleteMenu));
        tools.put("system.dict_type.create", writeTool("system.dict_type.create", "新增字典类型", "新增当前租户字典类型。", "system:dict:create", this::createDictType));
        tools.put("system.dict_type.update", writeTool("system.dict_type.update", "编辑字典类型", "编辑当前租户字典类型。", "system:dict:update", this::updateDictType));
        tools.put("system.dict_type.delete", writeTool("system.dict_type.delete", "删除字典类型", "删除当前租户非系统字典类型。", "system:dict:delete", this::deleteDictType));
        tools.put("system.dict_item.create", writeTool("system.dict_item.create", "新增字典项", "新增当前租户字典项。", "system:dict:create", this::createDictItem));
        tools.put("system.dict_item.update", writeTool("system.dict_item.update", "编辑字典项", "编辑当前租户字典项。", "system:dict:update", this::updateDictItem));
        tools.put("system.dict_item.delete", writeTool("system.dict_item.delete", "删除字典项", "删除当前租户字典项。", "system:dict:delete", this::deleteDictItem));
        tools.put("system.config.create", writeTool("system.config.create", "新增系统配置", "新增非敏感平台或租户配置。", "system:config:update", this::createConfig));
        tools.put("system.config.update", writeTool("system.config.update", "编辑系统配置", "编辑非敏感平台或租户配置。", "system:config:update", this::updateConfig));
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
        return tools.values().stream()
                .sorted(Comparator.comparing(NativeTool::code))
                .map(NativeTool::toVO)
                .toList();
    }

    @Override
    public AiVO.ToolExecuteResultVO execute(CurrentUser currentUser, AiDTO.ToolExecuteRequest request) {
        if (request == null || !StringUtils.hasText(request.getToolCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "工具编码不能为空");
        }
        Long tenantId = currentTenantId(currentUser);
        String toolCode = request.getToolCode().trim();
        NativeTool tool = tools.get(toolCode);
        if (tool == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "AI 工具不存在: " + toolCode);
        }
        boolean confirmed = Boolean.TRUE.equals(request.getConfirmed());
        Map<String, Object> arguments = request.getArguments() == null ? Map.of() : request.getArguments();

        try {
            requireEmployee(tenantId, request.getEmployeeId());
            if (request.getEmployeeId() != null && request.getEmployeeId() > 0) {
                aiSkillPermissionChecker.verifyAllowed(tenantId, request.getEmployeeId(), List.of(toolCode), confirmed);
            }
            if (StringUtils.hasText(tool.requiredPermission())) {
                permissionGuard.requirePermission(currentUser, tool.requiredPermission());
            }
            Map<String, Object> data = tool.executor().execute(new ToolExecutionContext(currentUser, tenantId, arguments));
            AiVO.ToolExecuteResultVO result = new AiVO.ToolExecuteResultVO();
            result.setToolCode(toolCode);
            result.setResultStatus("SUCCESS");
            result.setMessage("工具调用成功");
            result.setData(data);
            result.setExecutedAt(LocalDateTime.now());
            recordToolAuditLog(tenantId, request, tool, confirmed, "allow", "SUCCESS", "AI 工具调用成功", data);
            return result;
        } catch (RuntimeException exception) {
            recordFailedToolAuditLog(tenantId, request, tool, confirmed, exception);
            throw exception;
        }
    }

    private Map<String, Object> permissionSnapshot(ToolExecutionContext context) {
        CurrentUser currentUser = context.currentUser();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", currentUser == null ? null : currentUser.getUserId());
        data.put("username", currentUser == null ? null : currentUser.getUsername());
        data.put("tenantId", context.tenantId());
        data.put("authenticated", currentUser != null && currentUser.isAuthenticated());
        data.put("permissions", currentUser == null ? List.of() : currentUser.getPermissions().stream().sorted().toList());
        data.put("roleIds", currentUser == null ? List.of() : currentUser.getRoleIds().stream().sorted().toList());
        data.put("primaryDeptId", currentUser == null ? null : currentUser.getPrimaryDeptId());
        data.put("deptIds", currentUser == null ? List.of() : currentUser.getDeptIds().stream().sorted().toList());
        data.put("descendantDeptIds", currentUser == null ? List.of() : currentUser.getDescendantDeptIds().stream().sorted().toList());
        return data;
    }

    private Map<String, Object> listMenus(ToolExecutionContext context) {
        String status = stringArg(context.arguments(), "status", "ENABLED");
        int limit = limitArg(context.arguments());
        List<Map<String, Object>> menus = platformQueryFacade.listMenus(context.tenantId(), status, limit);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", menus);
        data.put("limit", limit);
        data.put("count", menus.size());
        return data;
    }

    private Map<String, Object> readConfig(ToolExecutionContext context) {
        String configKey = stringArg(context.arguments(), "configKey", null);
        if (!StringUtils.hasText(configKey)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "configKey 不能为空");
        }
        if (looksSensitive(configKey)) {
            throw new BizException(ErrorCode.FORBIDDEN, "敏感配置不允许通过 AI 工具读取: " + configKey);
        }
        Map<String, Object> config = platformQueryFacade.readConfig(context.tenantId(), configKey.trim());
        if (config == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "配置不存在: " + configKey);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("config", config);
        return data;
    }

    private Map<String, Object> searchUsers(ToolExecutionContext context) {
        String keyword = stringArg(context.arguments(), "keyword", null);
        String status = stringArg(context.arguments(), "status", null);
        int limit = limitArg(context.arguments());
        AiIamQueryFacade.UserSearchResult searchResult = iamQueryFacade.searchUsers(context.tenantId(), keyword, status, limit);
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
        if (userId.equals(context.currentUser().getUserId()) && "DISABLED".equalsIgnoreCase(status)) {
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
        if (userId.equals(context.currentUser().getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "不允许通过 AI 删除当前登录账号");
        }
        if (Long.valueOf(1001L).equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "不允许通过 AI 删除默认管理员账户");
        }
        boolean deleted = systemManagementAppService.deleteUser(context.currentUser(), userId);
        return Map.of("deleted", deleted, "userId", userId);
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
                context.tenantId(),
                currentUser == null ? null : currentUser.getUserId(),
                currentUser == null ? null : currentUser.getUsername(),
                hasPermission(context.currentUser(), "system:file:manage"),
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
        ensureNonSensitiveConfig(context.arguments());
        SystemDTO.ConfigUpsertRequest request = objectMapper.convertValue(context.arguments(), SystemDTO.ConfigUpsertRequest.class);
        if (!StringUtils.hasText(request.getConfigScope())) {
            request.setConfigScope("TENANT");
        }
        SystemVO.ConfigVO config = systemManagementAppService.createConfig(context.currentUser(), request);
        return Map.of("config", config);
    }

    private Map<String, Object> updateConfig(ToolExecutionContext context) {
        Long configId = requireLong(context.arguments(), "configId");
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
                        context.tenantId(),
                        currentUser == null ? null : currentUser.getUserId(),
                        currentUser == null ? null : currentUser.getUsername(),
                        keyword,
                        contentType,
                        status,
                        hasPermission(context.currentUser(), "system:file:manage"),
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
        args.add(context.tenantId());
        StringBuilder sql = new StringBuilder("""
                select id, tenant_id as tenantId, conversation_id as conversationId, employee_id as employeeId,
                       skill_code as skillCode, tool_name as toolName, permission_mode as permissionMode,
                       confirm_required as confirmRequired, confirm_result as confirmResult,
                       result_status as resultStatus, detail_message as detailMessage,
                       create_time as createdAt
                from ai_tool_audit_log
                where is_deleted = 0
                  and tenant_id = ?
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

    private void requireEmployee(Long tenantId, Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            return;
        }
        boolean exists = jdbcTemplate.exists(
                "select 1 from ai_employee where tenant_id = ? and id = ? and is_deleted = 0 and enabled = 1 limit 1",
                tenantId,
                employeeId
        );
        if (!exists) {
            throw new BizException(ErrorCode.NOT_FOUND, "数字员工不存在或已禁用");
        }
    }

    private void recordToolAuditLog(
            Long tenantId,
            AiDTO.ToolExecuteRequest request,
            NativeTool tool,
            boolean confirmed,
            String permissionMode,
            String resultStatus,
            String detailMessage,
            Map<String, Object> responsePayload
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
                request.getConversationId(),
                request.getEmployeeId(),
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
    }

    private void recordFailedToolAuditLog(
            Long tenantId,
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
                    tenantId,
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
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("credential")
                || normalized.contains("private")
                || normalized.endsWith(".key")
                || normalized.contains("app-secret");
    }

    private boolean hasPermission(CurrentUser currentUser, String permissionKey) {
        return currentUser != null
                && currentUser.getPermissions() != null
                && (currentUser.getPermissions().contains("*") || currentUser.getPermissions().contains(permissionKey));
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

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser != null && currentUser.getCurrentTenantId() != null) {
            return currentUser.getCurrentTenantId();
        }
        return PlatformConstants.PLATFORM_TENANT_ID;
    }

    private record ToolExecutionContext(CurrentUser currentUser, Long tenantId, Map<String, Object> arguments) {
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
