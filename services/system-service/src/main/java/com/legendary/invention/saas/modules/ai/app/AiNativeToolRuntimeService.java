package com.legendary.invention.saas.modules.ai.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.common.constant.PlatformConstants;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.ai.dto.AiDTO;
import com.legendary.invention.saas.modules.ai.vo.AiVO;
import com.legendary.invention.saas.modules.iam.service.PermissionGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final Map<String, NativeTool> tools;

    DefaultAiNativeToolRuntimeService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionGuard permissionGuard,
            AiSkillPermissionChecker aiSkillPermissionChecker,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionGuard = permissionGuard;
        this.aiSkillPermissionChecker = aiSkillPermissionChecker;
        this.objectMapper = objectMapper;
        this.tools = Map.of(
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
            aiSkillPermissionChecker.verifyAllowed(tenantId, request.getEmployeeId(), List.of(toolCode), confirmed);
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
        List<Map<String, Object>> menus = jdbcTemplate.queryForList(
                """
                        select id, parent_id as parentId, menu_code as menuCode, menu_name as menuName,
                               menu_type as menuType, path, component, permission_key as permissionKey,
                               status, sort_no as sortNo
                        from sys_menu
                        where tenant_id = ?
                          and deleted = 0
                          and (? is null or status = ?)
                        order by sort_no asc, id asc
                        limit ?
                        """,
                context.tenantId(),
                StringUtils.hasText(status) ? status : null,
                StringUtils.hasText(status) ? status : null,
                limit
        );
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
        Map<String, Object> config = jdbcTemplate.queryForList(
                """
                        select config_key as configKey, config_name as configName, config_value as configValue,
                               config_scope as configScope, is_system as system
                        from sys_config
                        where tenant_id = ?
                          and config_key = ?
                          and deleted = 0
                        limit 1
                        """,
                context.tenantId(),
                configKey.trim()
        ).stream().findFirst().orElse(null);
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
        List<Object> args = new java.util.ArrayList<>();
        args.add(context.tenantId());
        StringBuilder sql = new StringBuilder("""
                select u.id, u.username, u.nickname, u.real_name as realName, u.mobile, u.email,
                       u.status, u.created_at as createdAt, u.updated_at as updatedAt
                from sys_user u
                join sys_user_tenant ut
                  on ut.user_id = u.id
                 and ut.tenant_id = ?
                 and ut.deleted = 0
                where u.deleted = 0
                """);
        if (StringUtils.hasText(keyword)) {
            sql.append("""
                     and (
                       u.username like ? or u.nickname like ? or u.real_name like ?
                       or u.mobile like ? or u.email like ?
                     )
                    """);
            String pattern = like(keyword);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        if (StringUtils.hasText(status)) {
            sql.append(" and u.status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" order by u.id desc limit ?");
        args.add(limit);
        List<Map<String, Object>> users = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> user : users) {
            user.put("mobile", maskMobile(user.get("mobile")));
            user.put("email", maskEmail(user.get("email")));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", users);
        data.put("limit", limit);
        data.put("count", users.size());
        return data;
    }

    private Map<String, Object> searchFiles(ToolExecutionContext context) {
        String keyword = stringArg(context.arguments(), "keyword", null);
        String contentType = stringArg(context.arguments(), "contentType", null);
        String status = stringArg(context.arguments(), "status", "ENABLED");
        int limit = limitArg(context.arguments());
        List<Object> args = new java.util.ArrayList<>();
        args.add(context.tenantId());
        StringBuilder sql = new StringBuilder("""
                select id, original_filename as originalFileName, file_extension as fileExtension,
                       content_type as contentType, file_size as fileSizeBytes, uploaded_by as uploadedBy,
                       uploaded_by_name as uploadedByName, category, tags, status, preview_mode as previewMode,
                       created_at as createdAt, updated_at as updatedAt
                from file_object
                where tenant_id = ?
                  and deleted = 0
                """);
        if (!hasPermission(context.currentUser(), "system:file:manage")) {
            sql.append(" and uploaded_by = ?");
            args.add(context.currentUser() == null ? null : context.currentUser().getUserId());
        }
        if (StringUtils.hasText(keyword)) {
            sql.append(" and (original_filename like ? or category like ? or tags like ?)");
            String pattern = like(keyword);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        if (StringUtils.hasText(contentType)) {
            sql.append(" and content_type like ?");
            args.add(contentType.trim().endsWith("%") ? contentType.trim() : contentType.trim() + "%");
        }
        if (StringUtils.hasText(status)) {
            sql.append(" and status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" order by id desc limit ?");
        args.add(limit);
        List<Map<String, Object>> files = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", files);
        data.put("limit", limit);
        data.put("count", files.size());
        return data;
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
        if (employeeId == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "数字员工不能为空");
        }
        Long count = jdbcTemplate.queryForObject(
                "select count(1) from ai_employee where tenant_id = ? and id = ? and is_deleted = 0 and enabled = 1",
                Long.class,
                tenantId,
                employeeId
        );
        if (count == null || count == 0) {
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
