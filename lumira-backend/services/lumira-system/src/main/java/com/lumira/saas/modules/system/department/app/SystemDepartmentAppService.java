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
import com.lumira.saas.modules.system.department.vo.DepartmentVO;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
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

    private final MyBatisQueryOperations jdbcTemplate;
    private final PermissionSnapshotService permissionSnapshotService;
    private final OperationAuditService operationAuditService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;

    public SystemDepartmentAppService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService
    ) {
        this(
                jdbcTemplate,
                permissionSnapshotService,
                operationAuditService,
                null,
                null
        );
    }

    @Autowired
    public SystemDepartmentAppService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionSnapshotService = permissionSnapshotService;
        this.operationAuditService = operationAuditService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    public SystemDepartmentAppService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(jdbcTemplate, permissionSnapshotService, operationAuditService, null, sessionAuthenticationService);
    }

    public List<DepartmentVO> listDepartments(CurrentUser currentUser) {
        assertAuthenticated(currentUser);
        List<DepartmentVO> rows = jdbcTemplate.query(
                """
                        select d.id,
                               d.parent_id as parentId,
                               d.dept_code as deptCode,
                               d.dept_name as deptName,
                               d.sort_no as sortNo,
                               d.status,
                               coalesce(uc.user_count, 0) as userCount,
                               d.created_at as createdAt,
                               d.updated_at as updatedAt
                        from sys_department d
                        left join (
                            select ud.dept_id, count(distinct ud.user_id) as user_count
                            from sys_user_department ud
                            join sys_user u
                              on u.id = ud.user_id
                             and u.uuid = ud.user_uuid
                             and u.deleted = 0
                            where ud.user_uuid is not null
                              and trim(ud.user_uuid) <> ''
                              and ud.deleted = 0
                            group by ud.dept_id
                        ) uc on uc.dept_id = d.id
                        where d.deleted = 0
                        order by d.sort_no asc, d.id asc
                        """,
                new BeanPropertyRowMapper<>(DepartmentVO.class)
        );
        return buildTree(rows);
    }

    public DepartmentVO getDepartment(CurrentUser currentUser, Long id) {
        assertAuthenticated(currentUser);
        requirePositiveId(id, "Department id is required");
        DepartmentVO department = queryDepartment(id);
        if (department == null) {
            throw visibleBizException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        return department;
    }

    @Transactional
    public DepartmentVO createDepartment(CurrentUser currentUser, DepartmentUpsertRequest request) {
        requirePermission(currentUser, "system:department:create");
        requireRequest(request, "Department request is required");
        validateParent(null, request.getParentId());
        validateDeptCodeUnique(null, request.getDeptCode());
        try {
            int inserted = jdbcTemplate.update(
                    """
                            insert into sys_department (
                                parent_id, dept_code, dept_name, sort_no, status,
                                created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    normalizeParentId(request.getParentId()),
                    request.getDeptCode(),
                    request.getDeptName(),
                    request.getSortNo() == null ? 0 : request.getSortNo(),
                    normalizeStatus(request.getStatus()),
                    currentUser.getUserId(),
                    currentUser.getUserUuid(),
                    currentUser.getUserId(),
                    currentUser.getUserUuid()
            );
            if (inserted != 1) {
                throw visibleBizException(ErrorCode.BIZ_ERROR, "Department changed, please retry");
            }
        } catch (DuplicateKeyException exception) {
            throw visibleBizException(ErrorCode.VALIDATION_ERROR, "部门编码已存在，请更换后重试");
        }
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        rebuildClosureForSubtree(id);
        permissionSnapshotService.invalidatePermissions();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "department", "create", "CREATE", "SUCCESS", "创建部门: " + request.getDeptName());
        return getDepartment(currentUser, id);
    }

    @Transactional
    public DepartmentVO updateDepartment(CurrentUser currentUser, Long id, DepartmentUpsertRequest request) {
        requirePermission(currentUser, "system:department:update");
        requirePositiveId(id, "Department id is required");
        requireRequest(request, "Department request is required");
        DepartmentVO existing = getDepartment(currentUser, id);
        validateParent(id, request.getParentId());
        validateDeptCodeUnique(id, request.getDeptCode());
        int updated;
        try {
            updated = jdbcTemplate.update(
                    """
                            update sys_department
                            set parent_id = ?,
                                dept_code = ?,
                                dept_name = ?,
                                sort_no = ?,
                                status = ?,
                                updated_by = ?,
                                updated_by_uuid = ?,
                                updated_at = ?
                            where id = ?
                              and dept_code = ?
                              and status = ?
                              and deleted = 0
                            """,
                    normalizeParentId(request.getParentId()),
                    request.getDeptCode(),
                    request.getDeptName(),
                    request.getSortNo() == null ? 0 : request.getSortNo(),
                    normalizeStatus(request.getStatus()),
                    currentUser.getUserId(),
                    currentUser.getUserUuid(),
                    LocalDateTime.now(),
                    id,
                    existing.getDeptCode(),
                    existing.getStatus()
            );
        } catch (DuplicateKeyException exception) {
            throw visibleBizException(ErrorCode.VALIDATION_ERROR, "部门编码已存在，请更换后重试");
        }
        if (updated == 0) {
            throw visibleBizException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        rebuildClosureForSubtree(id);
        permissionSnapshotService.invalidatePermissions();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "department", "update", "UPDATE", "SUCCESS", "更新部门: " + existing.getDeptName());
        return getDepartment(currentUser, id);
    }

    @Transactional
    public boolean deleteDepartment(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, "system:department:delete");
        requirePositiveId(id, "Department id is required");
        DepartmentVO existing = getDepartment(currentUser, id);
        boolean hasChildDepartment = jdbcTemplate.exists(
                "select 1 from sys_department where parent_id = ? and deleted = 0 limit 1",
                id
        );
        if (hasChildDepartment) {
            throw visibleBizException(ErrorCode.BIZ_ERROR, "存在下级部门，不能删除");
        }
        boolean hasAssignedUsers = jdbcTemplate.exists(
                """
                        select 1
                        from sys_user_department ud
                        join sys_user u
                          on u.id = ud.user_id
                         and u.uuid = ud.user_uuid
                         and u.deleted = 0
                        where ud.dept_id = ?
                          and ud.user_uuid is not null
                          and trim(ud.user_uuid) <> ''
                          and ud.deleted = 0
                        limit 1
                        """,
                id
        );
        if (hasAssignedUsers) {
            throw visibleBizException(ErrorCode.BIZ_ERROR, "部门下仍有用户，不能删除");
        }
        int updated = jdbcTemplate.update(
                """
                        update sys_department
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and dept_code = ? and status = ? and deleted = 0
                        """,
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                LocalDateTime.now(),
                id,
                existing.getDeptCode(),
                existing.getStatus()
        );
        if (updated == 0) {
            throw visibleBizException(ErrorCode.BIZ_ERROR, "Department changed, please retry");
        }
        jdbcTemplate.update(
                """
                        update sys_department_closure
                        set deleted = 1
                        where descendant_id = ?
                        """,
                id
        );
        permissionSnapshotService.invalidatePermissions();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "department", "delete", "DELETE", "SUCCESS", "删除部门: " + existing.getDeptName());
        return true;
    }

    private DepartmentVO queryDepartment(Long id) {
        List<DepartmentVO> list = jdbcTemplate.query(
                """
                        select d.id,
                               d.parent_id as parentId,
                               d.dept_code as deptCode,
                               d.dept_name as deptName,
                               d.sort_no as sortNo,
                               d.status,
                               coalesce(uc.user_count, 0) as userCount,
                               d.created_at as createdAt,
                               d.updated_at as updatedAt
                        from sys_department d
                        left join (
                            select ud.dept_id, count(distinct ud.user_id) as user_count
                            from sys_user_department ud
                            join sys_user u
                              on u.id = ud.user_id
                             and u.uuid = ud.user_uuid
                             and u.deleted = 0
                            where ud.user_uuid is not null
                              and trim(ud.user_uuid) <> ''
                              and ud.deleted = 0
                            group by ud.dept_id
                        ) uc on uc.dept_id = d.id
                        where d.id = ?
                          and d.deleted = 0
                        """,
                new BeanPropertyRowMapper<>(DepartmentVO.class),
                id
        );
        return list.isEmpty() ? null : list.get(0);
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
            throw visibleBizException(ErrorCode.NOT_FOUND, "上级部门不存在");
        }
        Long cursor = parent.getParentId();
        int guard = 0;
        while (cursor != null && cursor > 0 && guard++ < 32) {
            if (cursor.equals(currentId)) {
                throw visibleBizException(ErrorCode.VALIDATION_ERROR, "不能把部门移动到自己的下级");
            }
            DepartmentVO ancestor = queryDepartment(cursor);
            cursor = ancestor == null ? null : ancestor.getParentId();
        }
    }

    private void rebuildClosureForSubtree(Long rootDepartmentId) {
        if (rootDepartmentId == null) {
            return;
        }
        List<Long> subtreeIds = jdbcTemplate.queryForList(
                """
                        with recursive dept_tree as (
                            select id
                            from sys_department
                            where id = ? and deleted = 0
                            union all
                            select child.id
                            from sys_department child
                            join dept_tree parent on parent.id = child.parent_id
                            where child.deleted = 0
                        )
                        select id from dept_tree
                        """,
                Long.class,
                rootDepartmentId
        );
        if (subtreeIds == null || subtreeIds.isEmpty()) {
            return;
        }
        String placeholders = "?,".repeat(subtreeIds.size()).replaceFirst(",$", "");
        List<Object> deleteArgs = new ArrayList<>();
        deleteArgs.addAll(subtreeIds);
        jdbcTemplate.update(
                "update sys_department_closure set deleted = 1 where descendant_id in (" + placeholders + ")",
                deleteArgs.toArray()
        );
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
        jdbcTemplate.update(
                """
                        insert into sys_department_closure (ancestor_id, descendant_id, depth, deleted, created_at)
                        values (?, ?, 0, 0, ?)
                        on duplicate key update
                            deleted = case
                                when exists (select 1 from sys_department a where a.id = values(ancestor_id) and a.deleted = 0)
                                 and exists (select 1 from sys_department d where d.id = values(descendant_id) and d.deleted = 0)
                                then 0 else deleted end,
                            depth = case
                                when exists (select 1 from sys_department a where a.id = values(ancestor_id) and a.deleted = 0)
                                 and exists (select 1 from sys_department d where d.id = values(descendant_id) and d.deleted = 0)
                                then values(depth) else depth end
                        """,
                departmentId,
                departmentId,
                now
        );
        Long parentId = normalizeParentId(department.getParentId());
        if (parentId == null) {
            return;
        }
        jdbcTemplate.update(
                """
                        insert into sys_department_closure (ancestor_id, descendant_id, depth, deleted, created_at)
                        select closure.ancestor_id, ?, closure.depth + 1, 0, ?
                        from sys_department_closure closure
                        join sys_department ancestor on ancestor.id = closure.ancestor_id and ancestor.deleted = 0
                        where closure.descendant_id = ?
                          and closure.deleted = 0
                        on duplicate key update
                            deleted = case
                                when exists (select 1 from sys_department a where a.id = values(ancestor_id) and a.deleted = 0)
                                 and exists (select 1 from sys_department d where d.id = values(descendant_id) and d.deleted = 0)
                                then 0 else deleted end,
                            depth = case
                                when exists (select 1 from sys_department a where a.id = values(ancestor_id) and a.deleted = 0)
                                 and exists (select 1 from sys_department d where d.id = values(descendant_id) and d.deleted = 0)
                                then values(depth) else depth end
                        """,
                departmentId,
                now,
                parentId
        );
    }

    private void validateDeptCodeUnique(Long currentId, String deptCode) {
        if (!StringUtils.hasText(deptCode)) {
            return;
        }
        boolean exists = jdbcTemplate.exists(
                """
                        select 1
                        from sys_department
                        where dept_code = ?
                          and (? is null or id <> ?)
                        limit 1
                        """,
                deptCode.trim(),
                currentId,
                currentId
        );
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
        target.setSimulatedRoleId(source.getSimulatedRoleId());
        target.setLoginType(source.getLoginType());
    }

    private BizException visibleBizException(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message, message);
    }
}
