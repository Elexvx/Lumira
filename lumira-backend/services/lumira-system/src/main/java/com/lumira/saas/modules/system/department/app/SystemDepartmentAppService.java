package com.lumira.saas.modules.system.department.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.department.dto.DepartmentUpsertRequest;
import com.lumira.saas.modules.system.department.vo.DepartmentVO;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemDepartmentAppService {

    private final MyBatisQueryOperations jdbcTemplate;
    private final PermissionSnapshotService permissionSnapshotService;
    private final OperationAuditService operationAuditService;

    public SystemDepartmentAppService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionSnapshotService = permissionSnapshotService;
        this.operationAuditService = operationAuditService;
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
                            select dept_id, count(distinct user_id) as user_count
                            from sys_user_department
                            where deleted = 0
                            group by dept_id
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
        DepartmentVO department = queryDepartment(id);
        if (department == null) {
            throw visibleBizException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        return department;
    }

    @Transactional
    public DepartmentVO createDepartment(CurrentUser currentUser, DepartmentUpsertRequest request) {
        assertAuthenticated(currentUser);
        validateParent(null, request.getParentId());
        validateDeptCodeUnique(null, request.getDeptCode());
        try {
            jdbcTemplate.update(
                    """
                            insert into sys_department (
                                parent_id, dept_code, dept_name, sort_no, status,
                                created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    normalizeParentId(request.getParentId()),
                    request.getDeptCode(),
                    request.getDeptName(),
                    request.getSortNo() == null ? 0 : request.getSortNo(),
                    normalizeStatus(request.getStatus()),
                    currentUser.getUserId(),
                    currentUser.getUserId()
            );
        } catch (DuplicateKeyException exception) {
            throw visibleBizException(ErrorCode.VALIDATION_ERROR, "部门编码已存在，请更换后重试");
        }
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        rebuildClosureForSubtree(id);
        permissionSnapshotService.invalidatePermissions();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "department", "create", "CREATE", "SUCCESS", "创建部门: " + request.getDeptName());
        return getDepartment(currentUser, id);
    }

    @Transactional
    public DepartmentVO updateDepartment(CurrentUser currentUser, Long id, DepartmentUpsertRequest request) {
        assertAuthenticated(currentUser);
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
                                updated_at = ?
                            where id = ?
                              and deleted = 0
                            """,
                    normalizeParentId(request.getParentId()),
                    request.getDeptCode(),
                    request.getDeptName(),
                    request.getSortNo() == null ? 0 : request.getSortNo(),
                    normalizeStatus(request.getStatus()),
                    currentUser.getUserId(),
                    LocalDateTime.now(),
                    id
            );
        } catch (DuplicateKeyException exception) {
            throw visibleBizException(ErrorCode.VALIDATION_ERROR, "部门编码已存在，请更换后重试");
        }
        if (updated == 0) {
            throw visibleBizException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        permissionSnapshotService.invalidatePermissions();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "department", "update", "UPDATE", "SUCCESS", "更新部门: " + existing.getDeptName());
        return getDepartment(currentUser, id);
    }

    @Transactional
    public boolean deleteDepartment(CurrentUser currentUser, Long id) {
        assertAuthenticated(currentUser);
        DepartmentVO existing = getDepartment(currentUser, id);
        boolean hasChildDepartment = jdbcTemplate.exists(
                "select 1 from sys_department where parent_id = ? and deleted = 0 limit 1",
                id
        );
        if (hasChildDepartment) {
            throw visibleBizException(ErrorCode.BIZ_ERROR, "存在下级部门，不能删除");
        }
        boolean hasAssignedUsers = jdbcTemplate.exists(
                "select 1 from sys_user_department where dept_id = ? and deleted = 0 limit 1",
                id
        );
        if (hasAssignedUsers) {
            throw visibleBizException(ErrorCode.BIZ_ERROR, "部门下仍有用户，不能删除");
        }
        jdbcTemplate.update(
                """
                        update sys_department
                        set deleted = 1, updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
                        """,
                currentUser.getUserId(),
                LocalDateTime.now(),
                id
        );
        permissionSnapshotService.invalidatePermissions();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "department", "delete", "DELETE", "SUCCESS", "删除部门: " + existing.getDeptName());
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
                            select dept_id, count(distinct user_id) as user_count
                            from sys_user_department
                            where deleted = 0
                            group by dept_id
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
                "delete from sys_department_closure where descendant_id in (" + placeholders + ")",
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
                        on duplicate key update deleted = 0, depth = values(depth)
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
                        select ancestor_id, ?, depth + 1, 0, ?
                        from sys_department_closure
                        where descendant_id = ?
                          and deleted = 0
                        on duplicate key update deleted = 0, depth = values(depth)
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
        if (currentUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
    }

    private BizException visibleBizException(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message, message);
    }
}
