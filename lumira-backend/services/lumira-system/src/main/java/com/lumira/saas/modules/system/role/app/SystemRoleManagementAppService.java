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
import com.lumira.saas.modules.system.app.MaintenanceLoginPolicyService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.config.app.SystemConfigVersioningService;
import com.lumira.saas.modules.system.role.infrastructure.SystemRoleManagementPersistenceAdapters;
import com.lumira.saas.modules.system.role.dto.RoleDataScopeRequest;
import com.lumira.saas.modules.system.role.repository.SystemRoleManagementRepository;
import com.lumira.saas.modules.system.role.vo.RoleDataScopeVO;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

    private final SystemRoleManagementRepository roleRepository;
    private final PermissionSnapshotService permissionSnapshotService;
    private final OperationAuditService operationAuditService;
    private final DomainEventPublisher domainEventPublisher;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;
    private SystemConfigVersioningService configVersioningService;
    private MaintenanceLoginPolicyService maintenanceLoginPolicyService;

    @Autowired
    public void setConfigVersioningService(SystemConfigVersioningService configVersioningService) {
        this.configVersioningService = configVersioningService;
    }

    @Autowired(required = false)
    public void setMaintenanceLoginPolicyService(MaintenanceLoginPolicyService maintenanceLoginPolicyService) {
        this.maintenanceLoginPolicyService = maintenanceLoginPolicyService;
    }

    @Autowired
    public SystemRoleManagementAppService(
            SystemRoleManagementRepository roleRepository,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService
    ) {
        this(
                roleRepository,
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
            SystemRoleManagementRepository roleRepository,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService,
            @Qualifier("systemDomainEventPublisher") DomainEventPublisher domainEventPublisher
    ) {
        this(
                roleRepository,
                permissionSnapshotService,
                operationAuditService,
                domainEventPublisher,
                null,
                null,
                false
        );
    }

    public SystemRoleManagementAppService(
            SystemRoleManagementRepository roleRepository,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService,
            @Qualifier("systemDomainEventPublisher") DomainEventPublisher domainEventPublisher,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                roleRepository,
                permissionSnapshotService,
                operationAuditService,
                domainEventPublisher,
                systemInternalApi,
                sessionAuthenticationService,
                true
        );
    }

    private SystemRoleManagementAppService(
            SystemRoleManagementRepository roleRepository,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService,
            @Qualifier("systemDomainEventPublisher") DomainEventPublisher domainEventPublisher,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.roleRepository = roleRepository;
        this.permissionSnapshotService = permissionSnapshotService;
        this.operationAuditService = operationAuditService;
        this.domainEventPublisher = domainEventPublisher;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    /** Compatibility constructors for legacy tests; production injects the typed role repository. */
    public SystemRoleManagementAppService(
            Object persistence,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService
    ) {
        this(SystemRoleManagementPersistenceAdapters.from(persistence), permissionSnapshotService, operationAuditService);
    }

    public SystemRoleManagementAppService(
            Object persistence,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService,
            DomainEventPublisher domainEventPublisher
    ) {
        this(SystemRoleManagementPersistenceAdapters.from(persistence), permissionSnapshotService, operationAuditService, domainEventPublisher);
    }

    public SystemRoleManagementAppService(
            Object persistence,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService,
            DomainEventPublisher domainEventPublisher,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(SystemRoleManagementPersistenceAdapters.from(persistence), permissionSnapshotService, operationAuditService,
                domainEventPublisher, systemInternalApi, sessionAuthenticationService);
    }

    public PageResponse<SystemVO.RoleVO> listRoles(CurrentUser currentUser, String roleCode, String roleName, String roleType, long pageNo, long pageSize) {
        requirePermission(currentUser, "system:role:view");
        PageResponse<SystemVO.RoleVO> page = roleRepository.findRoles(
                new SystemRoleManagementRepository.RoleSearch(roleCode, roleName, roleType, pageNo, pageSize)
        );
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

    public List<SystemVO.MaintenanceLoginRoleOptionVO> listMaintenanceLoginRoleOptions(CurrentUser currentUser) {
        requirePermission(currentUser, "system:config:view");
        return roleRepository.findActiveRoles().stream()
                .map(role -> new SystemVO.MaintenanceLoginRoleOptionVO(
                        role.getId(), role.getRoleCode(), role.getRoleName(), role.getRoleType()
                ))
                .toList();
    }

    public SystemVO.RoleDetailVO getRole(CurrentUser currentUser, Long roleId) {
        requirePermission(currentUser, "system:role:view");
        requirePositiveId(roleId, "Role id is required");
        SystemVO.RoleVO role = roleRepository.findActiveRoleById(roleId);
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
        SystemVO.RoleVO role = roleRepository.findLatestActiveRoleByCode(roleCode);
        if (role == null) {
            role = roleRepository.findLatestActiveRoleByCode(DEFAULT_REGISTRATION_ROLE_CODE);
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
        SystemVO.RoleVO role = roleRepository.findActiveRoleById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Role does not exist");
        }
        requireSafeDefaultRegistrationRole(role);
        SystemConfigVersioningService governanceService = governanceServiceForWrite();
        SystemConfigVersioningService.GovernanceSession configVersion = governanceService == null ? null : governanceService.begin(
                new SystemConfigVersioningService.ChangeRequest(
                        "SYSTEM_CONFIG",
                        SystemConfigVersioningService.DOMAIN_PLATFORM,
                        null,
                        "set default registration role",
                        currentUser
                ),
                List.of(DEFAULT_REGISTRATION_ROLE_CODE_KEY)
        );
        upsertConfigValue(
                DEFAULT_REGISTRATION_ROLE_CODE_KEY,
                "默认注册角色",
                role.getRoleCode(),
                "用户通过注册或验证码自动注册后默认绑定的角色编码",
                currentUser.getUserId(),
                currentUser.getUserUuid()
        );
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "role", "default-registration", "UPDATE", "SUCCESS", "更新默认注册角色: " + role.getRoleName());
        if (governanceService != null) {
            governanceService.finish(configVersion);
        }
        return toDefaultRegistrationRole(role);
    }

    private SystemConfigVersioningService governanceServiceForWrite() {
        if (configVersioningService == null && enforceTrustedUserResolution) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Configuration governance is unavailable; configuration was not changed");
        }
        return configVersioningService;
    }

    @Transactional
    public SystemVO.RoleDetailVO createRole(CurrentUser currentUser, SystemDTO.RoleUpsertRequest request) {
        requirePermission(currentUser, "system:role:create");
        validateRoleRequest(currentUser, request);
        Long roleId = upsertRole(null, request, currentUser.getUserId(), currentUser.getUserUuid());
        replaceRolePermissionsWithDomainEvent(roleId, null, Set.of(), request.getPermissionKeys(), currentUser.getUserId(), currentUser.getUserUuid());
        replaceRoleDataScopes(roleId, null, request.getDataScopes(), request.getRoleCode(), currentUser.getUserId(), currentUser.getUserUuid(), true);
        // A newly created role has no assigned subjects, so no existing session is stale.
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "role", "create", "CREATE", "SUCCESS", "创建角色: " + request.getRoleName());
        return queryRoleDetail(roleId);
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
        permissionSnapshotService.invalidateRoleAuthorization(roleId);
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "role", "update", "UPDATE", "SUCCESS", "更新角色: " + request.getRoleName());
        return queryRoleDetail(roleId);
    }

    @Transactional
    public boolean updateRolePermissions(CurrentUser currentUser, Long roleId, List<String> permissionKeys) {
        requirePermission(currentUser, "system:role:grant");
        requirePositiveId(roleId, "Role id is required");
        validatePermissionKeys(permissionKeys);
        SystemVO.RoleDetailVO existingRole = queryRoleDetail(roleId);
        Set<String> existingPermissions = new LinkedHashSet<>(existingRole.getPermissionKeys());
        replaceRolePermissionsWithDomainEvent(roleId, existingRole, existingPermissions, permissionKeys, currentUser.getUserId(), currentUser.getUserUuid());
        permissionSnapshotService.invalidatePermissionsForRole(roleId);
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
        requireMaintenanceLoginRoleMayBeDeleted(roleId);
        RoleAggregate roleAggregate = new RoleAggregate(roleId, new LinkedHashSet<>(role.getPermissionKeys()));
        roleAggregate.replacePermissions(Set.of(), currentUser.getUserId(), currentUser.getUserUuid());
        domainEventPublisher.publishAll(roleAggregate.pullDomainEvents());
        SystemRoleManagementRepository.Actor actor = new SystemRoleManagementRepository.Actor(
                currentUser.getUserId(), currentUser.getUserUuid()
        );
        int deleted = roleRepository.softDeleteRole(
                new SystemRoleManagementRepository.RoleVersion(roleId, role.getRoleCode(), role.getRoleType()),
                actor,
                LocalDateTime.now()
        );
        requireRoleWrite(deleted, "Role changed, please retry");
        roleRepository.retireDeletedRoleRelations(roleId, actor, LocalDateTime.now());
        permissionSnapshotService.invalidateRoleAuthorization(roleId);
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "role", "delete", "DELETE", "SUCCESS", "删除角色: " + role.getRoleName());
        return true;
    }

    private void requireMaintenanceLoginRoleMayBeDeleted(Long roleId) {
        if (maintenanceLoginPolicyService == null) {
            return;
        }
        var policy = maintenanceLoginPolicyService.loadEffectivePolicy();
        if (policy != null
                && policy.enabled()
                && policy.allowedRoleIds() != null
                && policy.allowedRoleIds().size() == 1
                && policy.allowedRoleIds().contains(roleId)) {
            throw new BizException(
                    ErrorCode.VALIDATION_ERROR,
                    "维护模式下不可删除最后一个允许登录的角色，请先调整维护登录角色配置"
            );
        }
    }

    private SystemVO.RoleDetailVO queryRoleDetail(Long roleId) {
        SystemVO.RoleVO role = roleRepository.findActiveRoleById(roleId);
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
        return roleRepository.findActivePermissionKeys(roleId);
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
        SystemRoleManagementRepository.RoleVersion existing = roleId == null ? null
                : new SystemRoleManagementRepository.RoleVersion(roleId, existingRole == null ? null : existingRole.getRoleCode(), existingRole == null ? null : existingRole.getRoleType());
        SystemRoleManagementRepository.RoleSaveResult result = roleRepository.saveRole(
                new SystemRoleManagementRepository.RoleSave(
                        existing,
                        request.getRoleCode(),
                        request.getRoleName(),
                        request.getRoleType(),
                        normalizeDefaultHomePath(request.getDefaultHomePath()),
                        new SystemRoleManagementRepository.Actor(operatorId, operatorUuid),
                        LocalDateTime.now()
                )
        );
        requireRoleWrite(result.writeCount(), "Role changed, please retry");
        return result.roleId();
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
        SystemRoleManagementRepository.RoleVersion existing = existingRole == null ? null
                : new SystemRoleManagementRepository.RoleVersion(roleId, existingRole.getRoleCode(), existingRole.getRoleType());
        SystemRoleManagementRepository.Actor actor = new SystemRoleManagementRepository.Actor(operatorId, operatorUuid);
        roleRepository.retireRolePermissions(roleId, existing, actor, LocalDateTime.now());
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
        SystemRoleManagementRepository.RoleVersion existing = existingRole == null ? null
                : new SystemRoleManagementRepository.RoleVersion(roleId, existingRole.getRoleCode(), existingRole.getRoleType());
        int inserted = roleRepository.upsertRolePermissions(
                roleId, existing, permissionKeys, new SystemRoleManagementRepository.Actor(operatorId, operatorUuid)
        );
        requireCompleteUpsert(inserted, permissionKeys.size(), "Role changed, please retry");
    }

    private void requireRoleWrite(int updated, String message) {
        if (updated <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, message);
        }
    }

    private void requireCompleteUpsert(int updated, int expected, String message) {
        if (updated < expected || updated > expected * 2) {
            throw new BizException(ErrorCode.NOT_FOUND, message);
        }
    }

    private List<String> listRolePermissionKeys(Long roleId) {
        return roleRepository.findActivePermissionKeys(roleId).stream().filter(this::isRoleAssignablePermissionKey).toList();
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
        return roleRepository.findActiveDataScopes(roleId);
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
        SystemRoleManagementRepository.RoleVersion existing = existingRole == null ? null
                : new SystemRoleManagementRepository.RoleVersion(roleId, existingRole.getRoleCode(), existingRole.getRoleType());
        SystemRoleManagementRepository.Actor actor = new SystemRoleManagementRepository.Actor(operatorId, operatorUuid);
        roleRepository.retireRoleDataScopes(roleId, existing, actor, LocalDateTime.now());
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
        List<SystemRoleManagementRepository.RoleDataScopeValue> values = scopes.stream()
                .map(scope -> new SystemRoleManagementRepository.RoleDataScopeValue(
                        scope.resourceCode(), scope.scopeType(), scope.customDeptIds(), scope.customUserIds()
                ))
                .toList();
        int inserted = roleRepository.upsertRoleDataScopes(
                roleId, values, new SystemRoleManagementRepository.Actor(operatorId, operatorUuid)
        );
        requireCompleteUpsert(inserted, scopes.size(), "Role data scope changed, please retry");
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

    private Integer countRolePermissions(Long roleId) {
        return roleRepository.countActiveRolePermissions(roleId);
    }

    private Integer countRoleUsers(Long roleId) {
        return roleRepository.countActiveRoleUsers(roleId);
    }

    private Map<Long, Integer> countRolePermissions(List<Long> roleIds) {
        return roleRepository.countActiveRolePermissions(roleIds);
    }

    private Map<Long, Integer> countRoleUsers(List<Long> roleIds) {
        return roleRepository.countActiveRoleUsers(roleIds);
    }

    private Map<String, String> loadConfigValuesByKeys(List<String> keys) {
        return roleRepository.findPlatformConfigValues(keys);
    }

    private void upsertConfigValue(
            String configKey,
            String configName,
            String configValue,
            String remark,
            Long operatorId,
            String operatorUuid
    ) {
        SystemRoleManagementRepository.ConfigSaveResult result = roleRepository.savePlatformConfig(
                new SystemRoleManagementRepository.ConfigSave(
                        configKey, configName, configValue, remark,
                        new SystemRoleManagementRepository.Actor(operatorId, operatorUuid),
                        LocalDateTime.now()
                )
        );
        if (result.writeCount() <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Role config changed, please retry");
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

    private record NormalizedRoleDataScope(
            String resourceCode,
            String scopeType,
            String customDeptIds,
            String customUserIds
    ) {
    }

}
