package com.lumira.saas.modules.system.role.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
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
@ConditionalOnLumiraControlPlaneEnabled
public class SystemRoleManagementAppService {

    private static final String DEFAULT_REGISTRATION_ROLE_CODE_KEY = "auth.default-registration-role-code";
    private static final String DEFAULT_REGISTRATION_ROLE_CODE = "commonuser";
    private static final String DEFAULT_HOME_PATH = "/dashboard/home";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int BULK_INSERT_BATCH_SIZE = 200;
    private static final java.util.concurrent.Executor BLOCKING_IO_EXECUTOR = command -> Thread.ofVirtual().start(command);
    private static final Set<String> ADMIN_ONLY_ROLE_PERMISSION_PREFIXES = Set.of(
            "ai:",
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
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    @Autowired
    public SystemRoleManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService
    ) {
        this(
                jdbcTemplate,
                permissionSnapshotService,
                operationAuditService,
                event -> {
                },
                null,
                null,
                true
        );
    }

    public SystemRoleManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService,
            @Qualifier("systemDomainEventPublisher") DomainEventPublisher domainEventPublisher
    ) {
        this(
                jdbcTemplate,
                permissionSnapshotService,
                operationAuditService,
                domainEventPublisher,
                null,
                null,
                false
        );
    }

    public SystemRoleManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService,
            @Qualifier("systemDomainEventPublisher") DomainEventPublisher domainEventPublisher,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                jdbcTemplate,
                permissionSnapshotService,
                operationAuditService,
                domainEventPublisher,
                systemInternalApi,
                sessionAuthenticationService,
                true
        );
    }

    private SystemRoleManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService,
            @Qualifier("systemDomainEventPublisher") DomainEventPublisher domainEventPublisher,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionSnapshotService = permissionSnapshotService;
        this.operationAuditService = operationAuditService;
        this.domainEventPublisher = domainEventPublisher;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public PageResponse<SystemVO.RoleVO> listRoles(CurrentUser currentUser, String roleCode, String roleName, String roleType, long pageNo, long pageSize) {
        requirePermission(currentUser, "system:role:view");
        String baseSql = """
                from sys_role r
                where r.deleted = 0
                """;
        List<Object> params = new ArrayList<>();
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
                select r.id, r.role_code as roleCode, r.role_name as roleName,
                       r.role_type as roleType, r.default_home_path as defaultHomePath, r.created_at as createdAt, r.updated_at as updatedAt
                """ + baseSql + " order by r.id desc";
        PageResponse<SystemVO.RoleVO> page = pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.RoleVO.class, pageNo, pageSize, params);
        String defaultRegistrationRoleCode = resolveDefaultRegistrationRoleCode();
        CompletableFuture<Map<Long, Integer>> permissionCountsFuture = CompletableFuture.supplyAsync(
                () -> countRolePermissions(page.getRecords().stream().map(SystemVO.RoleVO::getId).toList()),
                BLOCKING_IO_EXECUTOR
        );
        CompletableFuture<Map<Long, Integer>> userCountsFuture = CompletableFuture.supplyAsync(
                () -> countRoleUsers(page.getRecords().stream().map(SystemVO.RoleVO::getId).toList()),
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
        requirePermission(currentUser, "system:role:view");
        requirePositiveId(roleId, "Role id is required");
        SystemVO.RoleVO role = queryOne(
                """
                        select r.id, r.role_code as roleCode, r.role_name as roleName,
                               r.role_type as roleType, r.default_home_path as defaultHomePath, r.created_at as createdAt, r.updated_at as updatedAt
                        from sys_role r
                        where r.id = ? and r.deleted = 0
                        """,
                SystemVO.RoleVO.class,
                roleId
        );
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Role does not exist");
        }
        SystemVO.RoleDetailVO detail = new SystemVO.RoleDetailVO();
        copyRole(detail, role);
        CompletableFuture<Integer> permissionCountFuture = CompletableFuture.supplyAsync(() -> countRolePermissions(roleId), BLOCKING_IO_EXECUTOR);
        CompletableFuture<Integer> userCountFuture = CompletableFuture.supplyAsync(() -> countRoleUsers(roleId), BLOCKING_IO_EXECUTOR);
        detail.setPermissionCount(permissionCountFuture.join());
        detail.setUserCount(userCountFuture.join());
        detail.setDefaultRegistrationRole(role.getRoleCode() != null && role.getRoleCode().equals(resolveDefaultRegistrationRoleCode()));
        detail.setPermissionKeys(listRolePermissionKeys(roleId));
        detail.setDataScopes(listRoleDataScopes(roleId));
        return detail;
    }

    public SystemVO.DefaultRegistrationRoleVO getDefaultRegistrationRole(CurrentUser currentUser) {
        requirePermission(currentUser, "system:role:view");
        String roleCode = resolveDefaultRegistrationRoleCode();
        SystemVO.RoleVO role = queryOne(
                """
                        select r.id, r.role_code as roleCode, r.role_name as roleName,
                               r.role_type as roleType, r.default_home_path as defaultHomePath, r.created_at as createdAt, r.updated_at as updatedAt
                        from sys_role r
                        where r.role_code = ? and r.deleted = 0
                        order by r.id desc
                        limit 1
                        """,
                SystemVO.RoleVO.class,
                roleCode
        );
        if (role == null) {
            role = queryOne(
                    """
                            select r.id, r.role_code as roleCode, r.role_name as roleName,
                                   r.role_type as roleType, r.default_home_path as defaultHomePath, r.created_at as createdAt, r.updated_at as updatedAt
                            from sys_role r
                            where r.role_code = ? and r.deleted = 0
                            order by r.id desc
                            limit 1
                            """,
                    SystemVO.RoleVO.class,
                    DEFAULT_REGISTRATION_ROLE_CODE
            );
        }
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "默认注册角色不存在，请先创建可用角色");
        }
        return toDefaultRegistrationRole(role);
    }

    @Transactional
    public SystemVO.DefaultRegistrationRoleVO updateDefaultRegistrationRole(CurrentUser currentUser, Long roleId) {
        requirePermission(currentUser, "system:role:update");
        requirePositiveId(roleId, "Role id is required");
        SystemVO.RoleVO role = queryOne(
                """
                        select r.id, r.role_code as roleCode, r.role_name as roleName,
                               r.role_type as roleType, r.default_home_path as defaultHomePath, r.created_at as createdAt, r.updated_at as updatedAt
                        from sys_role r
                        where r.id = ? and r.deleted = 0
                        """,
                SystemVO.RoleVO.class,
                roleId
        );
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Role does not exist");
        }
        requireSafeDefaultRegistrationRole(role);
        upsertConfigValue(
                DEFAULT_REGISTRATION_ROLE_CODE_KEY,
                "默认注册角色",
                role.getRoleCode(),
                "用户通过注册或验证码自动注册后默认绑定的角色编码",
                currentUser.getUserId(),
                currentUser.getUserUuid()
        );
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "role", "default-registration", "UPDATE", "SUCCESS", "更新默认注册角色: " + role.getRoleName());
        return toDefaultRegistrationRole(role);
    }

    @Transactional
    public SystemVO.RoleDetailVO createRole(CurrentUser currentUser, SystemDTO.RoleUpsertRequest request) {
        requirePermission(currentUser, "system:role:create");
        validateRoleRequest(currentUser, request);
        Long roleId = upsertRole(null, request, currentUser.getUserId(), currentUser.getUserUuid());
        replaceRolePermissionsWithDomainEvent(roleId, null, Set.of(), request.getPermissionKeys(), currentUser.getUserId(), currentUser.getUserUuid());
        replaceRoleDataScopes(roleId, null, request.getDataScopes(), request.getRoleCode(), currentUser.getUserId(), currentUser.getUserUuid(), true);
        permissionSnapshotService.invalidatePermissions();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "role", "create", "CREATE", "SUCCESS", "创建角色: " + request.getRoleName());
        return getRole(currentUser, roleId);
    }

    @Transactional
    public SystemVO.RoleDetailVO updateRole(CurrentUser currentUser, Long roleId, SystemDTO.RoleUpsertRequest request) {
        requirePermission(currentUser, "system:role:update");
        requirePositiveId(roleId, "Role id is required");
        validateRoleRequest(currentUser, request);
        SystemVO.RoleDetailVO existingRole = queryRoleDetail(roleId);
        Set<String> existingPermissions = new LinkedHashSet<>(existingRole.getPermissionKeys());
        upsertRole(roleId, existingRole, request, currentUser.getUserId(), currentUser.getUserUuid());
        replaceRolePermissionsWithDomainEvent(roleId, existingRole, existingPermissions, request.getPermissionKeys(), currentUser.getUserId(), currentUser.getUserUuid());
        replaceRoleDataScopes(roleId, existingRole, request.getDataScopes(), request.getRoleCode(), currentUser.getUserId(), currentUser.getUserUuid(), false);
        permissionSnapshotService.invalidatePermissions();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "role", "update", "UPDATE", "SUCCESS", "更新角色: " + request.getRoleName());
        return getRole(currentUser, roleId);
    }

    @Transactional
    public boolean updateRolePermissions(CurrentUser currentUser, Long roleId, List<String> permissionKeys) {
        requirePermission(currentUser, "system:role:grant");
        requirePositiveId(roleId, "Role id is required");
        validatePermissionKeys(permissionKeys);
        SystemVO.RoleDetailVO existingRole = queryRoleDetail(roleId);
        Set<String> existingPermissions = new LinkedHashSet<>(existingRole.getPermissionKeys());
        replaceRolePermissionsWithDomainEvent(roleId, existingRole, existingPermissions, permissionKeys, currentUser.getUserId(), currentUser.getUserUuid());
        permissionSnapshotService.invalidatePermissions();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "role", "permissions", "UPDATE", "SUCCESS", "更新角色权限: " + roleId);
        return true;
    }

    @Transactional
    public boolean deleteRole(CurrentUser currentUser, Long roleId) {
        requirePermission(currentUser, "system:role:delete");
        requirePositiveId(roleId, "Role id is required");
        SystemVO.RoleDetailVO role = queryRoleDetail(roleId);
        if (Boolean.TRUE.equals(role.getDefaultRegistrationRole())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Default registration role cannot be deleted");
        }
        int userCount = role.getUserCount();
        if (userCount > 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Role is assigned to users; remove user-role bindings first");
        }
        RoleAggregate roleAggregate = new RoleAggregate(roleId, new LinkedHashSet<>(role.getPermissionKeys()));
        roleAggregate.replacePermissions(Set.of(), currentUser.getUserId(), currentUser.getUserUuid());
        domainEventPublisher.publishAll(roleAggregate.pullDomainEvents());
        int deleted = jdbcTemplate.update(
                """
                        update sys_role
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ?
                          and role_code = ?
                          and role_type = ?
                          and deleted = 0
                        """,
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                LocalDateTime.now(),
                roleId,
                role.getRoleCode(),
                role.getRoleType()
        );
        requireRoleWrite(deleted, "Role changed, please retry");
        jdbcTemplate.update(
                """
                        update sys_role_permission
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where role_id = ?
                          and deleted = 0
                          and exists (
                              select 1 from sys_role r
                              where r.id = sys_role_permission.role_id
                                and r.deleted = 1
                                and r.updated_by = ?
                                and r.updated_by_uuid = ?
                          )
                        """,
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                LocalDateTime.now(),
                roleId,
                currentUser.getUserId(),
                currentUser.getUserUuid()
        );
        jdbcTemplate.update(
                """
                        update sys_role_data_scope
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where role_id = ?
                          and deleted = 0
                          and exists (
                              select 1 from sys_role r
                              where r.id = sys_role_data_scope.role_id
                                and r.deleted = 1
                                and r.updated_by = ?
                                and r.updated_by_uuid = ?
                          )
                        """,
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                LocalDateTime.now(),
                roleId,
                currentUser.getUserId(),
                currentUser.getUserUuid()
        );
        permissionSnapshotService.invalidatePermissions();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "role", "delete", "DELETE", "SUCCESS", "删除角色: " + role.getRoleName());
        return true;
    }

    private SystemVO.RoleDetailVO queryRoleDetail(Long roleId) {
        SystemVO.RoleVO role = queryOne(
                """
                        select r.id, r.role_code as roleCode, r.role_name as roleName,
                               r.role_type as roleType, r.default_home_path as defaultHomePath, r.created_at as createdAt, r.updated_at as updatedAt
                        from sys_role r
                        where r.id = ? and r.deleted = 0
                        """,
                SystemVO.RoleVO.class,
                roleId
        );
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Role not found");
        }
        SystemVO.RoleDetailVO detail = new SystemVO.RoleDetailVO();
        copyRole(detail, role);
        detail.setPermissionCount(countRolePermissions(roleId));
        detail.setUserCount(countRoleUsers(roleId));
        detail.setDefaultRegistrationRole(role.getRoleCode() != null && role.getRoleCode().equals(resolveDefaultRegistrationRoleCode()));
        detail.setPermissionKeys(listRolePermissionKeys(roleId));
        detail.setDataScopes(listRoleDataScopes(roleId));
        return detail;
    }

    private void requirePermission(CurrentUser currentUser, String permissionKey) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is required");
        }
        Set<String> permissions = currentUser.getPermissions();
        if (permissions == null || (!permissions.contains("*") && !permissions.contains(permissionKey))) {
            throw new BizException(ErrorCode.FORBIDDEN, "Permission denied");
        }
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private void validateRoleRequest(CurrentUser currentUser, SystemDTO.RoleUpsertRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Role request is required");
        }
        if (!StringUtils.hasText(request.getRoleCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Role code is required");
        }
        if (!StringUtils.hasText(request.getRoleName())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Role name is required");
        }
        validatePermissionKeys(request.getPermissionKeys());
        validateRoleDataScopes(currentUser, request.getDataScopes());
    }

    private void validatePermissionKeys(List<String> permissionKeys) {
        if (CollectionUtils.isEmpty(permissionKeys)) {
            return;
        }
        for (String permissionKey : permissionKeys) {
            if (!StringUtils.hasText(permissionKey)) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Permission key is required");
            }
        }
    }

    private void validateRoleDataScopes(CurrentUser currentUser, List<RoleDataScopeRequest> dataScopes) {
        if (CollectionUtils.isEmpty(dataScopes)) {
            return;
        }
        for (RoleDataScopeRequest dataScope : dataScopes) {
            if (dataScope == null) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Data scope is required");
            }
            if (dataScope.getCustomDeptIds() != null
                    && dataScope.getCustomDeptIds().stream().anyMatch(id -> id == null || id <= 0)) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Custom department id must be positive");
            }
            if (dataScope.getCustomUserIds() != null
                    && dataScope.getCustomUserIds().stream().anyMatch(id -> id == null || id <= 0)) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Custom user id must be positive");
            }
            String scopeType = dataScope.getScopeType();
            if (StringUtils.hasText(scopeType)
                    && "ALL".equalsIgnoreCase(scopeType.trim())
                    && !currentUser.getPermissions().contains("*")) {
                throw new BizException(ErrorCode.FORBIDDEN, "Full data scope requires super user");
            }
        }
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return;
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
            return;
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid())
                    || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            String currentUsername = StringUtils.hasText(userSnapshot.username()) ? userSnapshot.username().trim() : null;
            if (!StringUtils.hasText(currentUsername)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            currentUser.setUserId(userSnapshot.userId());
            currentUser.setUserUuid(userSnapshot.userUuid().trim());
            currentUser.setUsername(currentUsername);
            normalizedUserUuid = userSnapshot.userUuid().trim();
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
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
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
            }
            return;
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
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        if (authenticatedAccess == null || !AuthenticationTrustSupport.isTrustedCurrentUser(authenticatedAccess.currentUser())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is required");
        }
        return authenticatedAccess.currentUser();
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
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setRoleIds(source.getRoleIds() == null ? Set.of() : Set.copyOf(source.getRoleIds()));
        target.setPermissions(source.getPermissions() == null ? Set.of() : Set.copyOf(source.getPermissions()));
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds() == null ? Set.of() : Set.copyOf(source.getDeptIds()));
        target.setDescendantDeptIds(source.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(source.getDescendantDeptIds()));
        target.setDataScopes(source.getDataScopes() == null ? List.of() : List.copyOf(source.getDataScopes()));
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
    }

    private void requireSafeDefaultRegistrationRole(SystemVO.RoleVO role) {
        String roleCode = role.getRoleCode() == null ? "" : role.getRoleCode().trim();
        String roleType = role.getRoleType() == null ? "" : role.getRoleType().trim().toUpperCase(Locale.ROOT);
        if ("ADMIN".equalsIgnoreCase(roleCode) || "SYSTEM".equals(roleType) || "ADMIN".equals(roleType)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Default registration role cannot be privileged");
        }
        for (String permissionKey : listRawRolePermissionKeys(role.getId())) {
            if (!isSafeDefaultRegistrationPermission(permissionKey)) {
                throw new BizException(ErrorCode.FORBIDDEN, "Default registration role cannot include management permissions");
            }
        }
    }

    private boolean isSafeDefaultRegistrationPermission(String permissionKey) {
        if (!StringUtils.hasText(permissionKey)) {
            return false;
        }
        String normalized = permissionKey.trim();
        if ("*".equals(normalized)) {
            return false;
        }
        return normalized.startsWith("dashboard:")
                || normalized.startsWith("profile:")
                || normalized.startsWith("aiadc:registration:");
    }

    private List<String> listRawRolePermissionKeys(Long roleId) {
        return jdbcTemplate.queryForList(
                """
                        select permission_key
                        from sys_role_permission
                        where role_id = ? and deleted = 0
                        order by permission_key asc
                        """,
                String.class,
                roleId
        );
    }

    private String resolveDefaultRegistrationRoleCode() {
        Map<String, String> values = loadConfigValuesByKeys(List.of(DEFAULT_REGISTRATION_ROLE_CODE_KEY));
        String roleCode = values.get(DEFAULT_REGISTRATION_ROLE_CODE_KEY);
        return StringUtils.hasText(roleCode) ? roleCode.trim() : DEFAULT_REGISTRATION_ROLE_CODE;
    }

    private SystemVO.DefaultRegistrationRoleVO toDefaultRegistrationRole(SystemVO.RoleVO role) {
        SystemVO.DefaultRegistrationRoleVO result = new SystemVO.DefaultRegistrationRoleVO();
        copyRole(result, role);
        CompletableFuture<Integer> permissionCountFuture = CompletableFuture.supplyAsync(() -> countRolePermissions(role.getId()), BLOCKING_IO_EXECUTOR);
        CompletableFuture<Integer> userCountFuture = CompletableFuture.supplyAsync(() -> countRoleUsers(role.getId()), BLOCKING_IO_EXECUTOR);
        result.setPermissionCount(permissionCountFuture.join());
        result.setUserCount(userCountFuture.join());
        result.setDefaultRegistrationRole(Boolean.TRUE);
        return result;
    }

    private Long upsertRole(Long roleId, SystemDTO.RoleUpsertRequest request, Long operatorId, String operatorUuid) {
        return upsertRole(roleId, null, request, operatorId, operatorUuid);
    }

    private Long upsertRole(Long roleId, SystemVO.RoleDetailVO existingRole, SystemDTO.RoleUpsertRequest request, Long operatorId, String operatorUuid) {
        if (roleId == null) {
            int inserted = jdbcTemplate.update(
                    """
                            insert into sys_role (role_code, role_name, role_type, default_home_path, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                            values (?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    request.getRoleCode(),
                    request.getRoleName(),
                    request.getRoleType(),
                    normalizeDefaultHomePath(request.getDefaultHomePath()),
                    operatorId,
                    operatorUuid,
                    operatorId,
                    operatorUuid
            );
            requireRoleWrite(inserted, "Role changed, please retry");
            return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        }
        int updated = jdbcTemplate.update(
                """
                        update sys_role
                        set role_code = ?, role_name = ?, role_type = ?, default_home_path = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and role_code = ? and role_type = ? and deleted = 0
                        """,
                request.getRoleCode(),
                request.getRoleName(),
                request.getRoleType(),
                normalizeDefaultHomePath(request.getDefaultHomePath()),
                operatorId,
                operatorUuid,
                LocalDateTime.now(),
                roleId,
                existingRole == null ? null : existingRole.getRoleCode(),
                existingRole == null ? null : existingRole.getRoleType()
        );
        requireRoleWrite(updated, "Role changed, please retry");
        return roleId;
    }

    private String normalizeDefaultHomePath(String defaultHomePath) {
        if (!StringUtils.hasText(defaultHomePath)) {
            return DEFAULT_HOME_PATH;
        }
        String normalized = defaultHomePath.trim();
        if (!normalized.startsWith("/") || normalized.startsWith("//") || normalized.length() > 255) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Default home path must be a valid internal route");
        }
        return normalized;
    }

    private void replaceRolePermissionsWithDomainEvent(
            Long roleId,
            SystemVO.RoleDetailVO existingRole,
            Set<String> existingPermissionKeys,
            List<String> permissionKeys,
            Long operatorId,
            String operatorUuid
    ) {
        LinkedHashSet<String> effectivePermissionKeys = filterRoleAssignablePermissionKeys(permissionKeys);
        RoleAggregate roleAggregate = new RoleAggregate(roleId, existingPermissionKeys);
        roleAggregate.replacePermissions(effectivePermissionKeys, operatorId, operatorUuid);
        domainEventPublisher.publishAll(roleAggregate.pullDomainEvents());
        replaceRolePermissions(roleId, existingRole, effectivePermissionKeys, operatorId, operatorUuid);
    }

    private void replaceRolePermissions(Long roleId, SystemVO.RoleDetailVO existingRole, Set<String> permissionKeys, Long operatorId, String operatorUuid) {
        jdbcTemplate.update(
                """
                        update sys_role_permission
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where role_id = ?
                          and deleted = 0
                          and exists (
                              select 1 from sys_role r
                              where r.id = sys_role_permission.role_id
                                and r.role_code = ?
                                and r.role_type = ?
                                and r.deleted = 0
                          )
                        """,
                operatorId,
                operatorUuid,
                LocalDateTime.now(),
                roleId,
                existingRole == null ? null : existingRole.getRoleCode(),
                existingRole == null ? null : existingRole.getRoleType()
        );
        if (CollectionUtils.isEmpty(permissionKeys)) {
            return;
        }
        List<String> orderedPermissionKeys = new ArrayList<>(permissionKeys);
        for (int start = 0; start < orderedPermissionKeys.size(); start += BULK_INSERT_BATCH_SIZE) {
            insertRolePermissionsBatch(
                    roleId,
                    existingRole,
                    orderedPermissionKeys.subList(start, Math.min(orderedPermissionKeys.size(), start + BULK_INSERT_BATCH_SIZE)),
                    operatorId,
                    operatorUuid
            );
        }
    }

    private void insertRolePermissionsBatch(Long roleId, SystemVO.RoleDetailVO existingRole, List<String> permissionKeys, Long operatorId, String operatorUuid) {
        if (CollectionUtils.isEmpty(permissionKeys)) {
            return;
        }
        StringBuilder sql = new StringBuilder("""
                insert into sys_role_permission (role_id, permission_key, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                """);
        List<Object> params = new ArrayList<>(permissionKeys.size() * 8);
        if (existingRole == null) {
            sql.append("values ");
            for (int index = 0; index < permissionKeys.size(); index += 1) {
                if (index > 0) {
                    sql.append(", ");
                }
                sql.append("(?, ?, ?, ?, ?, ?, 0)");
                params.add(roleId);
                params.add(permissionKeys.get(index));
                params.add(operatorId);
                params.add(operatorUuid);
                params.add(operatorId);
                params.add(operatorUuid);
            }
        } else {
            for (int index = 0; index < permissionKeys.size(); index += 1) {
                if (index > 0) {
                    sql.append(" union all ");
                }
                sql.append("""
                        select r.id, ?, ?, ?, ?, ?, 0
                        from sys_role r
                        where r.id = ?
                          and r.role_code = ?
                          and r.role_type = ?
                          and r.deleted = 0
                        """);
                params.add(permissionKeys.get(index));
                params.add(operatorId);
                params.add(operatorUuid);
                params.add(operatorId);
                params.add(operatorUuid);
                params.add(roleId);
                params.add(existingRole.getRoleCode());
                params.add(existingRole.getRoleType());
            }
        }
        int inserted = jdbcTemplate.update(sql.toString(), params.toArray());
        requireExactRoleWrite(inserted, permissionKeys.size(), "Role changed, please retry");
    }

    private void requireRoleWrite(int updated, String message) {
        if (updated <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, message);
        }
    }

    private void requireExactRoleWrite(int updated, int expected, String message) {
        if (updated != expected) {
            throw new BizException(ErrorCode.NOT_FOUND, message);
        }
    }

    private List<String> listRolePermissionKeys(Long roleId) {
        return jdbcTemplate.queryForList(
                """
                        select permission_key
                        from sys_role_permission
                        where role_id = ? and deleted = 0
                        order by permission_key asc
                        """,
                String.class,
                roleId
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

    private List<RoleDataScopeVO> listRoleDataScopes(Long roleId) {
        return jdbcTemplate.query(
                """
                        select resource_code as resourceCode, scope_type as scopeType,
                               custom_dept_ids as customDeptIds, custom_user_ids as customUserIds
                        from sys_role_data_scope
                        where role_id = ? and deleted = 0
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
                roleId
        );
    }

    private void replaceRoleDataScopes(
            Long roleId,
            SystemVO.RoleDetailVO existingRole,
            List<RoleDataScopeRequest> dataScopes,
            String roleCode,
            Long operatorId,
            String operatorUuid,
            boolean createMode
    ) {
        if (dataScopes == null && !createMode) {
            return;
        }
        if (existingRole == null) {
            jdbcTemplate.update(
                    """
                            update sys_role_data_scope
                            set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                            where role_id = ? and deleted = 0
                            """,
                    operatorId,
                    operatorUuid,
                    LocalDateTime.now(),
                    roleId
            );
        } else {
            jdbcTemplate.update(
                    """
                            update sys_role_data_scope
                            set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                            where role_id = ?
                              and deleted = 0
                              and exists (
                                  select 1 from sys_role r
                                  where r.id = sys_role_data_scope.role_id
                                    and r.role_code = ?
                                    and r.role_type = ?
                                    and r.deleted = 0
                              )
                            """,
                    operatorId,
                    operatorUuid,
                    LocalDateTime.now(),
                    roleId,
                    existingRole.getRoleCode(),
                    existingRole.getRoleType()
            );
        }
        List<RoleDataScopeRequest> effectiveScopes = CollectionUtils.isEmpty(dataScopes)
                ? List.of(defaultDataScope(roleCode))
                : dataScopes;
        Set<String> seenResources = new LinkedHashSet<>();
        List<NormalizedRoleDataScope> normalizedScopes = new ArrayList<>();
        for (RoleDataScopeRequest request : effectiveScopes) {
            String resourceCode = normalizeResourceCode(request.getResourceCode());
            if (!seenResources.add(resourceCode.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String scopeType = normalizeScopeType(request.getScopeType());
            normalizedScopes.add(new NormalizedRoleDataScope(
                    resourceCode,
                    scopeType,
                    joinIds(request.getCustomDeptIds()),
                    joinIds(request.getCustomUserIds())
            ));
        }
        for (int start = 0; start < normalizedScopes.size(); start += BULK_INSERT_BATCH_SIZE) {
            insertRoleDataScopesBatch(
                    roleId,
                    normalizedScopes.subList(start, Math.min(normalizedScopes.size(), start + BULK_INSERT_BATCH_SIZE)),
                    operatorId,
                    operatorUuid
            );
        }
    }

    private void insertRoleDataScopesBatch(Long roleId, List<NormalizedRoleDataScope> scopes, Long operatorId, String operatorUuid) {
        if (CollectionUtils.isEmpty(scopes)) {
            return;
        }
        StringBuilder sql = new StringBuilder("""
                insert into sys_role_data_scope (
                    role_id, resource_code, scope_type, custom_dept_ids, custom_user_ids,
                    created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                ) values
                """);
        List<Object> params = new ArrayList<>(scopes.size() * 9);
        for (int index = 0; index < scopes.size(); index += 1) {
            if (index > 0) {
                sql.append(", ");
            }
            sql.append("(?, ?, ?, ?, ?, ?, ?, ?, ?, 0)");
            NormalizedRoleDataScope scope = scopes.get(index);
            params.add(roleId);
            params.add(scope.resourceCode());
            params.add(scope.scopeType());
            params.add(scope.customDeptIds());
            params.add(scope.customUserIds());
            params.add(operatorId);
            params.add(operatorUuid);
            params.add(operatorId);
            params.add(operatorUuid);
        }
        int inserted = jdbcTemplate.update(sql.toString(), params.toArray());
        requireExactRoleWrite(inserted, scopes.size(), "Role data scope changed, please retry");
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
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Data-scope resource code cannot exceed 128 characters");
        }
        return normalized;
    }

    private String normalizeScopeType(String scopeType) {
        String normalized = StringUtils.hasText(scopeType) ? scopeType.trim().toUpperCase(Locale.ROOT) : "SELF";
        if (!Set.of("ALL", "DEPT", "DEPT_AND_CHILD", "SELF", "CUSTOM").contains(normalized)) {
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

    private Integer countRolePermissions(Long roleId) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from sys_role_permission
                        where role_id = ? and deleted = 0
                        """,
                Long.class,
                roleId
        );
        return count == null ? 0 : count.intValue();
    }

    private Integer countRoleUsers(Long roleId) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from sys_user_role ur
                        join sys_user u
                          on u.id = ur.user_id
                         and u.uuid = ur.user_uuid
                         and u.deleted = 0
                        where ur.role_id = ?
                          and ur.user_uuid is not null
                          and trim(ur.user_uuid) <> ''
                          and ur.deleted = 0
                        """,
                Long.class,
                roleId
        );
        return count == null ? 0 : count.intValue();
    }

    private Map<Long, Integer> countRolePermissions(List<Long> roleIds) {
        List<Long> distinctRoleIds = roleIds.stream().filter(id -> id != null).distinct().toList();
        if (distinctRoleIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = placeholders(distinctRoleIds.size());
        List<Object> params = new ArrayList<>();
        params.addAll(distinctRoleIds);
        return jdbcTemplate.query(
                """
                        select role_id as roleId, count(1) as total
                        from sys_role_permission
                        where role_id in (%s) and deleted = 0
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

    private Map<Long, Integer> countRoleUsers(List<Long> roleIds) {
        List<Long> distinctRoleIds = roleIds.stream().filter(id -> id != null).distinct().toList();
        if (distinctRoleIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = placeholders(distinctRoleIds.size());
        List<Object> params = new ArrayList<>();
        params.addAll(distinctRoleIds);
        return jdbcTemplate.query(
                """
                        select ur.role_id as roleId, count(1) as total
                        from sys_user_role ur
                        join sys_user u
                          on u.id = ur.user_id
                         and u.uuid = ur.user_uuid
                         and u.deleted = 0
                        where ur.role_id in (%s)
                          and ur.user_uuid is not null
                          and trim(ur.user_uuid) <> ''
                          and ur.deleted = 0
                        group by ur.role_id
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

    private Map<String, String> loadConfigValuesByKeys(List<String> keys) {
        String placeholders = keys.stream().map(item -> "?").collect(Collectors.joining(", "));
        String sql = """
                select config_key as configKey, config_value as configValue
                from sys_config
                where deleted = 0
                  and config_scope = 'PLATFORM'
                  and config_key in (%s)
                order by id desc
                """.formatted(placeholders);
        List<Object> params = new ArrayList<>(keys);
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
            String configKey,
            String configName,
            String configValue,
            String remark,
            Long operatorId,
            String operatorUuid
    ) {
        Long existingId = queryConfigId(configKey);
        if (existingId == null) {
            int inserted = jdbcTemplate.update(
                    """
                            insert into sys_config (
                                config_key, config_name, config_value, config_scope, is_system, remark,
                                created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, 'PLATFORM', 0, ?, ?, ?, ?, 0)
                            """,
                    configKey,
                    configName,
                    configValue,
                    remark,
                    operatorId,
                    operatorUuid,
                    operatorId,
                    operatorUuid
            );
            requireRoleWrite(inserted, "Role config changed, please retry");
            return;
        }
        int updated = jdbcTemplate.update(
                """
                        update sys_config
                        set config_name = ?, config_value = ?, config_scope = 'PLATFORM', remark = ?,
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ?
                          and config_key = ?
                          and config_scope = 'PLATFORM'
                          and is_system = 0
                          and deleted = 0
                        """,
                configName,
                configValue,
                remark,
                operatorId,
                operatorUuid,
                LocalDateTime.now(),
                existingId,
                configKey
        );
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Role config changed, please retry");
        }
    }

    private Long queryConfigId(String configKey) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id
                            from sys_config
                            where config_key = ?
                              and config_scope = 'PLATFORM'
                              and is_system = 0
                              and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    Long.class,
                    configKey
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

    private record NormalizedRoleDataScope(
            String resourceCode,
            String scopeType,
            String customDeptIds,
            String customUserIds
    ) {
    }

}
