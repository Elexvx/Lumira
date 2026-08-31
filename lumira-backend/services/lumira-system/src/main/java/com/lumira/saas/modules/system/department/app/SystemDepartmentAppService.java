package com.lumira.saas.modules.system.department.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.department.dto.DepartmentUpsertRequest;
import com.lumira.saas.modules.system.department.infrastructure.SystemDepartmentPersistenceAdapters;
import com.lumira.saas.modules.system.department.repository.SystemDepartmentRepository;
import com.lumira.saas.modules.system.department.vo.DepartmentVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SystemDepartmentAppService {

    private static final String STATUS_ENABLED = "ENABLED";

    private final SystemDepartmentRepository departmentRepository;
    private final PermissionSnapshotService permissionSnapshotService;
    private final OperationAuditService operationAuditService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    public SystemDepartmentAppService(
            SystemDepartmentRepository departmentRepository,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService
    ) {
        this(
                departmentRepository,
                permissionSnapshotService,
                operationAuditService,
                null,
                null,
                false
        );
    }

    @Autowired
    public SystemDepartmentAppService(
            SystemDepartmentRepository departmentRepository,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                departmentRepository,
                permissionSnapshotService,
                operationAuditService,
                systemInternalApi,
                sessionAuthenticationService,
                true
        );
    }

    private SystemDepartmentAppService(
            SystemDepartmentRepository departmentRepository,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.departmentRepository = departmentRepository;
        this.permissionSnapshotService = permissionSnapshotService;
        this.operationAuditService = operationAuditService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public SystemDepartmentAppService(
            SystemDepartmentRepository departmentRepository,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(departmentRepository, permissionSnapshotService, operationAuditService, null, sessionAuthenticationService, false);
    }

    /**
     * Compatibility constructor for legacy tests that still pass the low-level
     * query helper. Production wiring always injects {@link SystemDepartmentRepository}.
     */
    public SystemDepartmentAppService(
            Object persistence,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService
    ) {
        this(SystemDepartmentPersistenceAdapters.from(persistence), permissionSnapshotService, operationAuditService);
    }

    public SystemDepartmentAppService(
            Object persistence,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(SystemDepartmentPersistenceAdapters.from(persistence), permissionSnapshotService, operationAuditService,
                systemInternalApi, sessionAuthenticationService);
    }

    public SystemDepartmentAppService(
            Object persistence,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(SystemDepartmentPersistenceAdapters.from(persistence), permissionSnapshotService, operationAuditService,
                sessionAuthenticationService);
    }

    public List<DepartmentVO> listDepartments(CurrentUser currentUser) {
        requirePermission(currentUser, "system:department:view");
        return buildTree(departmentRepository.findAllActive());
    }

    public DepartmentVO getDepartment(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, "system:department:view");
        requirePositiveId(id, "Department id is required");
        DepartmentVO department = requireDepartment(id);
        if (department == null) {
            throw visibleBizException(ErrorCode.NOT_FOUND, "Department does not exist");
        }
        return department;
    }

    @Transactional
    public DepartmentVO createDepartment(CurrentUser currentUser, DepartmentUpsertRequest request) {
        requirePermission(currentUser, "system:department:create");
        requireRequest(request, "Department request is required");
        validateParent(null, request.getParentId());
        validateDeptCodeUnique(null, request.getDeptCode());
        SystemDepartmentRepository.DepartmentCreateResult createResult;
        try {
            createResult = departmentRepository.create(new SystemDepartmentRepository.DepartmentCreate(
                    normalizeParentId(request.getParentId()),
                    request.getDeptCode(),
                    request.getDeptName(),
                    request.getSortNo(),
                    normalizeStatus(request.getStatus()),
                    new SystemDepartmentRepository.Actor(currentUser.getUserId(), currentUser.getUserUuid())
            ));
            if (createResult.writeCount() != 1 || createResult.departmentId() == null) {
                throw visibleBizException(ErrorCode.BIZ_ERROR, "Department changed, please retry");
            }
        } catch (DuplicateKeyException exception) {
            throw visibleBizException(ErrorCode.VALIDATION_ERROR, "部门编码已存在，请更换后重试");
        }
        Long id = createResult.departmentId();
        rebuildClosureForSubtree(id);
        permissionSnapshotService.invalidateDataPolicies();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "department", "create", "CREATE", "SUCCESS", "创建部门: " + request.getDeptName());
        return requireDepartment(id);
    }

    @Transactional
    public DepartmentVO updateDepartment(CurrentUser currentUser, Long id, DepartmentUpsertRequest request) {
        requirePermission(currentUser, "system:department:update");
        requirePositiveId(id, "Department id is required");
        requireRequest(request, "Department request is required");
        DepartmentVO existing = requireDepartment(id);
        validateParent(id, request.getParentId());
        validateDeptCodeUnique(id, request.getDeptCode());
        int updated;
        try {
            updated = departmentRepository.update(new SystemDepartmentRepository.DepartmentUpdate(
                    id,
                    existing.getDeptCode(),
                    existing.getStatus(),
                    normalizeParentId(request.getParentId()),
                    request.getDeptCode(),
                    request.getDeptName(),
                    request.getSortNo(),
                    normalizeStatus(request.getStatus()),
                    new SystemDepartmentRepository.Actor(currentUser.getUserId(), currentUser.getUserUuid())
            ));
        } catch (DuplicateKeyException exception) {
            throw visibleBizException(ErrorCode.VALIDATION_ERROR, "部门编码已存在，请更换后重试");
        }
        if (updated == 0) {
            throw visibleBizException(ErrorCode.NOT_FOUND, "Department does not exist");
        }
        rebuildClosureForSubtree(id);
        permissionSnapshotService.invalidateDataPolicies();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "department", "update", "UPDATE", "SUCCESS", "更新部门: " + existing.getDeptName());
        return requireDepartment(id);
    }

    @Transactional
    public boolean deleteDepartment(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, "system:department:delete");
        requirePositiveId(id, "Department id is required");
        DepartmentVO existing = requireDepartment(id);
        boolean hasChildDepartment = departmentRepository.hasActiveChildren(id);
        if (hasChildDepartment) {
            throw visibleBizException(ErrorCode.BIZ_ERROR, "Department has child departments and cannot be deleted");
        }
        boolean hasAssignedUsers = departmentRepository.hasAssignedActiveUsers(id);
        if (hasAssignedUsers) {
            throw visibleBizException(ErrorCode.BIZ_ERROR, "部门下仍有用户，不能删除");
        }
        int updated = departmentRepository.softDelete(
                new SystemDepartmentRepository.DepartmentVersion(id, existing.getDeptCode(), existing.getStatus()),
                new SystemDepartmentRepository.Actor(currentUser.getUserId(), currentUser.getUserUuid()),
                LocalDateTime.now()
        );
        if (updated == 0) {
            throw visibleBizException(ErrorCode.BIZ_ERROR, "Department changed, please retry");
        }
        departmentRepository.retireClosureForDescendant(id);
        permissionSnapshotService.invalidateDataPolicies();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "department", "delete", "DELETE", "SUCCESS", "删除部门: " + existing.getDeptName());
        return true;
    }

    private DepartmentVO queryDepartment(Long id) {
        return departmentRepository.findActiveById(id);
    }

    private void validateParent(Long currentId, Long parentId) {
        Long normalizedParentId = normalizeParentId(parentId);
        if (normalizedParentId == null) {
            return;
        }
        if (normalizedParentId.equals(currentId)) {
            throw visibleBizException(ErrorCode.VALIDATION_ERROR, "上级部门不能选择自身");
        }
        DepartmentVO parent = queryDepartment(normalizedParentId);
        if (parent == null) {
            throw visibleBizException(ErrorCode.NOT_FOUND, "Parent department does not exist");
        }
        Long cursor = parent.getParentId();
        int guard = 0;
        while (cursor != null && cursor > 0 && guard++ < 32) {
            if (cursor.equals(currentId)) {
                throw visibleBizException(ErrorCode.VALIDATION_ERROR, "Department cannot be moved under its own descendant");
            }
            DepartmentVO ancestor = queryDepartment(cursor);
            cursor = ancestor == null ? null : ancestor.getParentId();
        }
    }

    private void rebuildClosureForSubtree(Long rootDepartmentId) {
        if (rootDepartmentId == null) {
            return;
        }
        List<Long> subtreeIds = departmentRepository.findActiveSubtreeIds(rootDepartmentId);
        if (subtreeIds == null || subtreeIds.isEmpty()) {
            return;
        }
        departmentRepository.retireClosureForDescendants(subtreeIds);
        for (Long descendantId : subtreeIds) {
            insertClosureForDepartment(descendantId);
        }
    }

    private void insertClosureForDepartment(Long departmentId) {
        DepartmentVO department = queryDepartment(departmentId);
        if (department == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        departmentRepository.ensureSelfClosure(departmentId, now);
        Long parentId = normalizeParentId(department.getParentId());
        if (parentId == null) {
            return;
        }
        departmentRepository.ensureInheritedClosure(departmentId, parentId, now);
    }

    private void validateDeptCodeUnique(Long currentId, String deptCode) {
        if (!StringUtils.hasText(deptCode)) {
            return;
        }
        boolean exists = departmentRepository.existsActiveDeptCode(deptCode.trim(), currentId);
        if (exists) {
            throw visibleBizException(ErrorCode.VALIDATION_ERROR, "部门编码已存在，请更换后重试");
        }
    }

    private List<DepartmentVO> buildTree(List<DepartmentVO> rows) {
        Map<Long, DepartmentVO> byId = new LinkedHashMap<>();
        for (DepartmentVO row : rows) {
            row.setChildren(new ArrayList<>());
            byId.put(row.getId(), row);
        }
        List<DepartmentVO> roots = new ArrayList<>();
        for (DepartmentVO row : rows) {
            Long parentId = normalizeParentId(row.getParentId());
            DepartmentVO parent = parentId == null ? null : byId.get(parentId);
            if (parent == null) {
                roots.add(row);
            } else {
                parent.getChildren().add(row);
            }
        }
        for (DepartmentVO root : roots) {
            rollupUserCount(root);
        }
        return roots;
    }

    private int rollupUserCount(DepartmentVO department) {
        int total = department.getUserCount() == null ? 0 : department.getUserCount();
        for (DepartmentVO child : department.getChildren()) {
            total += rollupUserCount(child);
        }
        department.setUserCount(total);
        return total;
    }

    private Long normalizeParentId(Long parentId) {
        return parentId == null || parentId <= 0 ? null : parentId;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "ENABLED";
        }
        String normalized = status.trim().toUpperCase();
        if (!List.of("ENABLED", "DISABLED").contains(normalized)) {
            throw visibleBizException(ErrorCode.VALIDATION_ERROR, "部门状态只能是 ENABLED 或 DISABLED");
        }
        return normalized;
    }

    private void assertAuthenticated(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is required");
        }
    }

    private DepartmentVO requireDepartment(Long departmentId) {
        DepartmentVO department = queryDepartment(departmentId);
        if (department == null) {
            throw visibleBizException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        return department;
    }

    private void requireRequest(Object request, String message) {
        if (request == null) {
            throw visibleBizException(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw visibleBizException(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private void requirePermission(CurrentUser currentUser, String permission) {
        assertAuthenticated(currentUser);
        Set<String> permissions = currentUser.getPermissions();
        if (permissions == null || (!permissions.contains("*") && !permissions.contains(permission))) {
            throw new BizException(ErrorCode.FORBIDDEN, "Permission denied");
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
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid())
                    || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            String currentUsername = StringUtils.hasText(userSnapshot.username()) ? userSnapshot.username().trim() : null;
            if (!StringUtils.hasText(currentUsername)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(currentUsername);
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

    private BizException visibleBizException(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message, message);
    }
}
