package com.lumira.saas.modules.system.role.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.iam.domain.model.IamDomainModels.RoleAggregate;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.role.dto.RoleDataScopeRequest;
import com.lumira.saas.modules.system.role.vo.RoleDataScopeVO;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class SystemRoleManagementAppService {

    private static final Long DEFAULT_PUBLIC_TENANT_ID = com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
    private static final String DEFAULT_REGISTRATION_ROLE_CODE_KEY = "auth.default-registration-role-code";
    private static final String DEFAULT_REGISTRATION_ROLE_CODE = "commonuser";
    private static final String DEFAULT_HOME_PATH = "/dashboard/home";
    private static final long MAX_PAGE_SIZE = 100L;
    private static final java.util.concurrent.Executor BLOCKING_IO_EXECUTOR = command -> Thread.ofVirtual().start(command);
    private static final Set<String> ADMIN_ONLY_ROLE_PERMISSION_PREFIXES = Set.of(
            "ai:employee:",
            "ai:llm:",
            "ai:tool:",
            "audit:",
            "localization:",
            "plugin:management:",
            "system:config:",
            "system:dict:",
            "system:file:manage",
            "system:menu:",
            "system:monitor:",
            "system:notification:",
            "system:profile-field:",
            "system:profile_field:",
            "system:security:",
            "system:update:",
            "system:verification:"
    );
    private static final Set<String> ADMIN_ONLY_ROLE_PERMISSION_KEYS = Set.of(
            "plugin:management:view",
            "audit:view",
            "localization:view",
            "system:file:manage",
            "system:monitor:view"
    );

    private final MyBatisQueryOperations jdbcTemplate;
    private final PermissionSnapshotService permissionSnapshotService;
    private final OperationAuditService operationAuditService;
    private final DomainEventPublisher domainEventPublisher;

    @Autowired
    public SystemRoleManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService
    ) {
        this(jdbcTemplate, permissionSnapshotService, operationAuditService, event -> {
        });
    }

    public SystemRoleManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService,
            @Qualifier("systemDomainEventPublisher") DomainEventPublisher domainEventPublisher
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionSnapshotService = permissionSnapshotService;
        this.operationAuditService = operationAuditService;
        this.domainEventPublisher = domainEventPublisher;
    }

    public PageResponse<SystemVO.RoleVO> listRoles(CurrentUser currentUser, String roleCode, String roleName, String roleType, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        String baseSql = """
                from sys_role r
                where r.tenant_id = ? and r.deleted = 0
                """;
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (StringUtils.hasText(roleCode)) {
            baseSql += " and r.role_code like ?";
            params.add(like(roleCode));
        }
        if (StringUtils.hasText(roleName)) {
            baseSql += " and r.role_name like ?";
            params.add(like(roleName));
        }
        if (StringUtils.hasText(roleType)) {
            baseSql += " and r.role_type = ?";
            params.add(roleType);
        }
        String selectSql = """
                select r.id, r.tenant_id as tenantId, r.role_code as roleCode, r.role_name as roleName,
                       r.role_type as roleType, r.default_home_path as defaultHomePath, r.created_at as createdAt, r.updated_at as updatedAt
                """ + baseSql + " order by r.id desc";
        PageResponse<SystemVO.RoleVO> page = pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.RoleVO.class, pageNo, pageSize, params);
        String defaultRegistrationRoleCode = resolveDefaultRegistrationRoleCode(tenantId);
        CompletableFuture<Map<Long, Integer>> permissionCountsFuture = CompletableFuture.supplyAsync(
                () -> countRolePermissions(page.getRecords().stream().map(SystemVO.RoleVO::getId).toList(), tenantId),
                BLOCKING_IO_EXECUTOR
        );
        CompletableFuture<Map<Long, Integer>> userCountsFuture = CompletableFuture.supplyAsync(
                () -> countRoleUsers(page.getRecords().stream().map(SystemVO.RoleVO::getId).toList(), tenantId),
                BLOCKING_IO_EXECUTOR
        );
        Map<Long, Integer> permissionCounts = permissionCountsFuture.join();
        Map<Long, Integer> userCounts = userCountsFuture.join();
        page.setRecords(page.getRecords().stream().map(role -> {
            role.setPermissionCount(permissionCounts.getOrDefault(role.getId(), 0));
            role.setUserCount(userCounts.getOrDefault(role.getId(), 0));
            role.setDefaultRegistrationRole(role.getRoleCode() != null && role.getRoleCode().equals(defaultRegistrationRoleCode));
            return role;
        }).toList());
        return page;
    }

    public SystemVO.RoleDetailVO getRole(CurrentUser currentUser, Long roleId) {
        Long tenantId = currentTenantId(currentUser);
        SystemVO.RoleVO role = queryOne(
                """
                        select r.id, r.tenant_id as tenantId, r.role_code as roleCode, r.role_name as roleName,
                               r.role_type as roleType, r.default_home_path as defaultHomePath, r.created_at as createdAt, r.updated_at as updatedAt
                        from sys_role r
                        where r.id = ? and r.tenant_id = ? and r.deleted = 0
                        """,
                SystemVO.RoleVO.class,
                roleId,
                tenantId
        );
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        SystemVO.RoleDetailVO detail = new SystemVO.RoleDetailVO();
        copyRole(detail, role);
        CompletableFuture<Integer> permissionCountFuture = CompletableFuture.supplyAsync(() -> countRolePermissions(roleId, tenantId), BLOCKING_IO_EXECUTOR);
        CompletableFuture<Integer> userCountFuture = CompletableFuture.supplyAsync(() -> countRoleUsers(roleId, tenantId), BLOCKING_IO_EXECUTOR);
        detail.setPermissionCount(permissionCountFuture.join());
        detail.setUserCount(userCountFuture.join());
        detail.setDefaultRegistrationRole(role.getRoleCode() != null && role.getRoleCode().equals(resolveDefaultRegistrationRoleCode(tenantId)));
        detail.setPermissionKeys(listRolePermissionKeys(roleId, tenantId));
        detail.setDataScopes(listRoleDataScopes(roleId, tenantId));
        return detail;
    }

    public SystemVO.DefaultRegistrationRoleVO getDefaultRegistrationRole(CurrentUser currentUser) {
        Long tenantId = currentTenantId(currentUser);
        String roleCode = resolveDefaultRegistrationRoleCode(tenantId);
        SystemVO.RoleVO role = queryOne(
                """
                        select r.id, r.tenant_id as tenantId, r.role_code as roleCode, r.role_name as roleName,
                               r.role_type as roleType, r.default_home_path as defaultHomePath, r.created_at as createdAt, r.updated_at as updatedAt
                        from sys_role r
                        where r.tenant_id = ? and r.role_code = ? and r.deleted = 0
                        order by r.id desc
                        limit 1
                        """,
                SystemVO.RoleVO.class,
                tenantId,
                roleCode
        );
        if (role == null) {
            role = queryOne(
                    """
                            select r.id, r.tenant_id as tenantId, r.role_code as roleCode, r.role_name as roleName,
                                   r.role_type as roleType, r.default_home_path as defaultHomePath, r.created_at as createdAt, r.updated_at as updatedAt
                            from sys_role r
                            where r.tenant_id = ? and r.role_code = ? and r.deleted = 0
                            order by r.id desc
                            limit 1
                            """,
                    SystemVO.RoleVO.class,
                    tenantId,
                    DEFAULT_REGISTRATION_ROLE_CODE
            );
        }
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "默认注册角色不存在，请先创建可用角色");
        }
        return toDefaultRegistrationRole(tenantId, role);
    }

    @Transactional
    public SystemVO.DefaultRegistrationRoleVO updateDefaultRegistrationRole(CurrentUser currentUser, Long roleId) {
        Long tenantId = currentTenantId(currentUser);
        SystemVO.RoleVO role = queryOne(
                """
                        select r.id, r.tenant_id as tenantId, r.role_code as roleCode, r.role_name as roleName,
                               r.role_type as roleType, r.default_home_path as defaultHomePath, r.created_at as createdAt, r.updated_at as updatedAt
                        from sys_role r
                        where r.id = ? and r.tenant_id = ? and r.deleted = 0
                        """,
                SystemVO.RoleVO.class,
                roleId,
                tenantId
        );
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        upsertConfigValue(
                tenantId,
                DEFAULT_REGISTRATION_ROLE_CODE_KEY,
                "默认注册角色",
                role.getRoleCode(),
                "用户通过注册或验证码自动注册后默认绑定的角色编码",
                currentUser.getUserId()
        );
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "role", "default-registration", "UPDATE", "SUCCESS", "更新默认注册角色: " + role.getRoleName());
        return toDefaultRegistrationRole(tenantId, role);
    }

    @Transactional
    public SystemVO.RoleDetailVO createRole(CurrentUser currentUser, SystemDTO.RoleUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Long roleId = upsertRole(null, tenantId, request, currentUser.getUserId());
        replaceRolePermissionsWithDomainEvent(tenantId, roleId, Set.of(), request.getPermissionKeys(), currentUser.getUserId());
        replaceRoleDataScopes(tenantId, roleId, request.getDataScopes(), request.getRoleCode(), currentUser.getUserId(), true);
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "role", "create", "CREATE", "SUCCESS", "创建角色: " + request.getRoleName());
        return getRole(currentUser, roleId);
    }

    @Transactional
    public SystemVO.RoleDetailVO updateRole(CurrentUser currentUser, Long roleId, SystemDTO.RoleUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Set<String> existingPermissions = new LinkedHashSet<>(listRolePermissionKeys(roleId, tenantId));
        upsertRole(roleId, tenantId, request, currentUser.getUserId());
        replaceRolePermissionsWithDomainEvent(tenantId, roleId, existingPermissions, request.getPermissionKeys(), currentUser.getUserId());
        replaceRoleDataScopes(tenantId, roleId, request.getDataScopes(), request.getRoleCode(), currentUser.getUserId(), false);
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "role", "update", "UPDATE", "SUCCESS", "更新角色: " + request.getRoleName());
        return getRole(currentUser, roleId);
    }

    @Transactional
    public boolean updateRolePermissions(CurrentUser currentUser, Long roleId, List<String> permissionKeys) {
        Long tenantId = currentTenantId(currentUser);
        Set<String> existingPermissions = new LinkedHashSet<>(listRolePermissionKeys(roleId, tenantId));
        replaceRolePermissionsWithDomainEvent(tenantId, roleId, existingPermissions, permissionKeys, currentUser.getUserId());
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "role", "permissions", "UPDATE", "SUCCESS", "更新角色权限: " + roleId);
        return true;
    }

    @Transactional
    public boolean deleteRole(CurrentUser currentUser, Long roleId) {
        Long tenantId = currentTenantId(currentUser);
        SystemVO.RoleDetailVO role = getRole(currentUser, roleId);
        if (Boolean.TRUE.equals(role.getDefaultRegistrationRole())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "默认注册角色不允许删除");
        }
        int userCount = role.getUserCount();
        if (userCount > 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "角色已被用户占用，请先移除用户角色关系");
        }
        RoleAggregate roleAggregate = new RoleAggregate(roleId, tenantId, new LinkedHashSet<>(role.getPermissionKeys()));
        roleAggregate.replacePermissions(Set.of());
        domainEventPublisher.publishAll(roleAggregate.pullDomainEvents());
        jdbcTemplate.update("delete from sys_role_permission where tenant_id = ? and role_id = ?", tenantId, roleId);
        jdbcTemplate.update("delete from sys_role_data_scope where tenant_id = ? and role_id = ?", tenantId, roleId);
        jdbcTemplate.update(
                "update sys_role set deleted = 1, updated_by = ?, updated_at = ? where id = ? and tenant_id = ? and deleted = 0",
                currentUser.getUserId(),
                LocalDateTime.now(),
                roleId,
                tenantId
        );
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "role", "delete", "DELETE", "SUCCESS", "删除角色: " + role.getRoleName());
        return true;
    }

    private String resolveDefaultRegistrationRoleCode(Long tenantId) {
        Map<String, String> values = loadConfigValuesByKeys(tenantId, List.of(DEFAULT_REGISTRATION_ROLE_CODE_KEY));
        String roleCode = values.get(DEFAULT_REGISTRATION_ROLE_CODE_KEY);
        return StringUtils.hasText(roleCode) ? roleCode.trim() : DEFAULT_REGISTRATION_ROLE_CODE;
    }

    private SystemVO.DefaultRegistrationRoleVO toDefaultRegistrationRole(Long tenantId, SystemVO.RoleVO role) {
        SystemVO.DefaultRegistrationRoleVO result = new SystemVO.DefaultRegistrationRoleVO();
        copyRole(result, role);
        CompletableFuture<Integer> permissionCountFuture = CompletableFuture.supplyAsync(() -> countRolePermissions(role.getId(), tenantId), BLOCKING_IO_EXECUTOR);
        CompletableFuture<Integer> userCountFuture = CompletableFuture.supplyAsync(() -> countRoleUsers(role.getId(), tenantId), BLOCKING_IO_EXECUTOR);
        result.setPermissionCount(permissionCountFuture.join());
        result.setUserCount(userCountFuture.join());
        result.setDefaultRegistrationRole(Boolean.TRUE);
        return result;
    }

    private Long upsertRole(Long roleId, Long tenantId, SystemDTO.RoleUpsertRequest request, Long operatorId) {
        if (roleId == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_role (tenant_id, role_code, role_name, role_type, default_home_path, created_by, updated_by, deleted)
                            values (?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    tenantId,
                    request.getRoleCode(),
                    request.getRoleName(),
                    request.getRoleType(),
                    normalizeDefaultHomePath(request.getDefaultHomePath()),
                    operatorId,
                    operatorId
            );
            return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        }
        jdbcTemplate.update(
                """
                        update sys_role
                        set role_code = ?, role_name = ?, role_type = ?, default_home_path = ?, updated_by = ?, updated_at = ?
                        where id = ? and tenant_id = ? and deleted = 0
                        """,
                request.getRoleCode(),
                request.getRoleName(),
                request.getRoleType(),
                normalizeDefaultHomePath(request.getDefaultHomePath()),
                operatorId,
                LocalDateTime.now(),
                roleId,
                tenantId
        );
        return roleId;
    }

    private String normalizeDefaultHomePath(String defaultHomePath) {
        if (!StringUtils.hasText(defaultHomePath)) {
            return DEFAULT_HOME_PATH;
        }
        String normalized = defaultHomePath.trim();
        if (!normalized.startsWith("/") || normalized.startsWith("//") || normalized.length() > 255) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "默认访问页面必须是有效站内路径");
        }
        return normalized;
    }

    private void replaceRolePermissionsWithDomainEvent(
            Long tenantId,
            Long roleId,
            Set<String> existingPermissionKeys,
            List<String> permissionKeys,
            Long operatorId
    ) {
        LinkedHashSet<String> effectivePermissionKeys = filterRoleAssignablePermissionKeys(permissionKeys);
        RoleAggregate roleAggregate = new RoleAggregate(roleId, tenantId, existingPermissionKeys);
        roleAggregate.replacePermissions(effectivePermissionKeys);
        domainEventPublisher.publishAll(roleAggregate.pullDomainEvents());
        replaceRolePermissions(tenantId, roleId, effectivePermissionKeys, operatorId);
    }

    private void replaceRolePermissions(Long tenantId, Long roleId, Set<String> permissionKeys, Long operatorId) {
        jdbcTemplate.update(
                "delete from sys_role_permission where tenant_id = ? and role_id = ?",
                tenantId,
                roleId
        );
        if (CollectionUtils.isEmpty(permissionKeys)) {
            return;
        }
        for (String permissionKey : permissionKeys) {
            jdbcTemplate.update(
                    """
                            insert into sys_role_permission (tenant_id, role_id, permission_key, created_by, updated_by, deleted)
                            values (?, ?, ?, ?, ?, 0)
                            """,
                    tenantId,
                    roleId,
                    permissionKey,
                    operatorId,
                    operatorId
            );
        }
    }

    private List<String> listRolePermissionKeys(Long roleId, Long tenantId) {
        return jdbcTemplate.queryForList(
                """
                        select permission_key
                        from sys_role_permission
                        where role_id = ? and tenant_id = ? and deleted = 0
                        order by permission_key asc
                        """,
                String.class,
                roleId,
                tenantId
        ).stream().filter(this::isRoleAssignablePermissionKey).toList();
    }

    private LinkedHashSet<String> filterRoleAssignablePermissionKeys(List<String> permissionKeys) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (CollectionUtils.isEmpty(permissionKeys)) {
            return result;
        }
        for (String permissionKey : permissionKeys) {
            if (isRoleAssignablePermissionKey(permissionKey)) {
                result.add(permissionKey);
            }
        }
        return result;
    }

    private boolean isRoleAssignablePermissionKey(String permissionKey) {
        if (!StringUtils.hasText(permissionKey)) {
            return false;
        }
        String normalizedKey = permissionKey.trim();
        if ("*".equals(normalizedKey)) {
            return false;
        }
        if (ADMIN_ONLY_ROLE_PERMISSION_KEYS.contains(normalizedKey)) {
            return false;
        }
        for (String prefix : ADMIN_ONLY_ROLE_PERMISSION_PREFIXES) {
            if (normalizedKey.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private List<RoleDataScopeVO> listRoleDataScopes(Long roleId, Long tenantId) {
        return jdbcTemplate.query(
                """
                        select resource_code as resourceCode, scope_type as scopeType,
                               custom_dept_ids as customDeptIds, custom_user_ids as customUserIds
                        from sys_role_data_scope
                        where role_id = ? and tenant_id = ? and deleted = 0
                        order by case when resource_code = '*' then 0 else 1 end, resource_code asc
                        """,
                (rs, rowNum) -> {
                    RoleDataScopeVO scope = new RoleDataScopeVO();
                    scope.setResourceCode(rs.getString("resourceCode"));
                    scope.setScopeType(rs.getString("scopeType"));
                    scope.setCustomDeptIds(parseIdList(rs.getString("customDeptIds")));
                    scope.setCustomUserIds(parseIdList(rs.getString("customUserIds")));
                    return scope;
                },
                roleId,
                tenantId
        );
    }

    private void replaceRoleDataScopes(
            Long tenantId,
            Long roleId,
            List<RoleDataScopeRequest> dataScopes,
            String roleCode,
            Long operatorId,
            boolean createMode
    ) {
        if (dataScopes == null && !createMode) {
            return;
        }
        jdbcTemplate.update("delete from sys_role_data_scope where tenant_id = ? and role_id = ?", tenantId, roleId);
        List<RoleDataScopeRequest> effectiveScopes = CollectionUtils.isEmpty(dataScopes)
                ? List.of(defaultDataScope(roleCode))
                : dataScopes;
        Set<String> seenResources = new LinkedHashSet<>();
        for (RoleDataScopeRequest request : effectiveScopes) {
            String resourceCode = normalizeResourceCode(request.getResourceCode());
            if (!seenResources.add(resourceCode.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String scopeType = normalizeScopeType(request.getScopeType());
            jdbcTemplate.update(
                    """
                            insert into sys_role_data_scope (
                                tenant_id, role_id, resource_code, scope_type, custom_dept_ids, custom_user_ids,
                                created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    tenantId,
                    roleId,
                    resourceCode,
                    scopeType,
                    joinIds(request.getCustomDeptIds()),
                    joinIds(request.getCustomUserIds()),
                    operatorId,
                    operatorId
            );
        }
    }

    private RoleDataScopeRequest defaultDataScope(String roleCode) {
        RoleDataScopeRequest request = new RoleDataScopeRequest();
        request.setResourceCode("*");
        request.setScopeType("ADMIN".equalsIgnoreCase(roleCode) ? "ALL" : "SELF");
        return request;
    }

    private String normalizeResourceCode(String resourceCode) {
        if (!StringUtils.hasText(resourceCode)) {
            return "*";
        }
        String normalized = resourceCode.trim();
        if (normalized.length() > 128) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "数据权限资源编码长度不能超过128个字符");
        }
        return normalized;
    }

    private String normalizeScopeType(String scopeType) {
        String normalized = StringUtils.hasText(scopeType) ? scopeType.trim().toUpperCase(Locale.ROOT) : "SELF";
        if (!Set.of("ALL", "TENANT", "DEPT", "DEPT_AND_CHILD", "SELF", "CUSTOM").contains(normalized)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "不支持的数据权限范围");
        }
        return normalized;
    }

    private String joinIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return null;
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private List<Long> parseIdList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : value.split(",")) {
            try {
                long id = Long.parseLong(part.trim());
                if (id > 0) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed legacy values.
            }
        }
        return ids;
    }

    private Integer countRolePermissions(Long roleId, Long tenantId) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from sys_role_permission
                        where role_id = ? and tenant_id = ? and deleted = 0
                        """,
                Long.class,
                roleId,
                tenantId
        );
        return count == null ? 0 : count.intValue();
    }

    private Integer countRoleUsers(Long roleId, Long tenantId) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from sys_user_role
                        where role_id = ? and tenant_id = ? and deleted = 0
                        """,
                Long.class,
                roleId,
                tenantId
        );
        return count == null ? 0 : count.intValue();
    }

    private Map<Long, Integer> countRolePermissions(List<Long> roleIds, Long tenantId) {
        List<Long> distinctRoleIds = roleIds.stream().filter(id -> id != null).distinct().toList();
        if (distinctRoleIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = placeholders(distinctRoleIds.size());
        List<Object> params = new ArrayList<>();
        params.addAll(distinctRoleIds);
        params.add(tenantId);
        return jdbcTemplate.query(
                """
                        select role_id as roleId, count(1) as total
                        from sys_role_permission
                        where role_id in (%s) and tenant_id = ? and deleted = 0
                        group by role_id
                        """.formatted(placeholders),
                rs -> {
                    Map<Long, Integer> result = new LinkedHashMap<>();
                    while (rs.next()) {
                        result.put(rs.getLong("roleId"), rs.getInt("total"));
                    }
                    return result;
                },
                params.toArray()
        );
    }

    private Map<Long, Integer> countRoleUsers(List<Long> roleIds, Long tenantId) {
        List<Long> distinctRoleIds = roleIds.stream().filter(id -> id != null).distinct().toList();
        if (distinctRoleIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = placeholders(distinctRoleIds.size());
        List<Object> params = new ArrayList<>();
        params.addAll(distinctRoleIds);
        params.add(tenantId);
        return jdbcTemplate.query(
                """
                        select role_id as roleId, count(1) as total
                        from sys_user_role
                        where role_id in (%s) and tenant_id = ? and deleted = 0
                        group by role_id
                        """.formatted(placeholders),
                rs -> {
                    Map<Long, Integer> result = new LinkedHashMap<>();
                    while (rs.next()) {
                        result.put(rs.getLong("roleId"), rs.getInt("total"));
                    }
                    return result;
                },
                params.toArray()
        );
    }

    private Map<String, String> loadConfigValuesByKeys(Long tenantId, List<String> keys) {
        Long effectiveTenantId = tenantId == null ? DEFAULT_PUBLIC_TENANT_ID : tenantId;
        String placeholders = keys.stream().map(item -> "?").collect(Collectors.joining(", "));
        String sql = """
                select tenant_id as tenantId, config_key as configKey, config_value as configValue
                from sys_config
                where deleted = 0
                  and config_scope = 'PLATFORM'
                  and config_key in (%s)
                  and (tenant_id = ? or tenant_id is null)
                order by case when tenant_id = ? then 0 else 1 end, id desc
                """.formatted(placeholders);
        List<Object> params = new ArrayList<>(keys);
        params.add(effectiveTenantId);
        params.add(effectiveTenantId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        Map<String, String> valueByKey = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String configKey = String.valueOf(row.get("configKey"));
            if (!valueByKey.containsKey(configKey)) {
                valueByKey.put(configKey, normalizeConfigText(row.get("configValue")));
            }
        }
        return valueByKey;
    }

    private void upsertConfigValue(
            Long tenantId,
            String configKey,
            String configName,
            String configValue,
            String remark,
            Long operatorId
    ) {
        Long existingId = queryConfigId(configKey, tenantId);
        if (existingId == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_config (
                                tenant_id, config_key, config_name, config_value, config_scope, is_system, remark,
                                created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, 'PLATFORM', 0, ?, ?, ?, 0)
                            """,
                    tenantId,
                    configKey,
                    configName,
                    configValue,
                    remark,
                    operatorId,
                    operatorId
            );
            return;
        }
        jdbcTemplate.update(
                """
                        update sys_config
                        set config_name = ?, config_value = ?, config_scope = 'PLATFORM', remark = ?,
                            updated_by = ?, updated_at = ?, deleted = 0
                        where id = ?
                        """,
                configName,
                configValue,
                remark,
                operatorId,
                LocalDateTime.now(),
                existingId
        );
    }

    private Long queryConfigId(String configKey, Long tenantId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id
                            from sys_config
                            where config_key = ? and tenant_id <=> ?
                            order by id desc
                            limit 1
                            """,
                    Long.class,
                    configKey,
                    tenantId
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private <T> PageResponse<T> pageQuery(String selectSql, String countSql, Class<T> voClass, long pageNo, long pageSize, List<Object> params) {
        long safePageNo = pageNo <= 0 ? 1 : pageNo;
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        long offset = (safePageNo - 1) * safePageSize;
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize);
        queryParams.add(offset);
        String pagedSql = selectSql + " limit ? offset ?";
        List<T> records = jdbcTemplate.query(pagedSql, new BeanPropertyRowMapper<>(voClass), queryParams.toArray());
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
        return value == null ? 0 : value;
    }

    private <T> T queryOne(String sql, Class<T> voClass, Object... params) {
        try {
            return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(voClass), params);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private void copyRole(SystemVO.RoleDetailVO target, SystemVO.RoleVO source) {
        target.setId(source.getId());
        target.setTenantId(source.getTenantId());
        target.setRoleCode(source.getRoleCode());
        target.setRoleName(source.getRoleName());
        target.setRoleType(source.getRoleType());
        target.setPermissionCount(source.getPermissionCount());
        target.setUserCount(source.getUserCount());
        target.setDefaultRegistrationRole(source.getDefaultRegistrationRole());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    private String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private String normalizeConfigText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getCurrentTenantId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "租户上下文缺失");
        }
        return currentUser.getCurrentTenantId();
    }
}
