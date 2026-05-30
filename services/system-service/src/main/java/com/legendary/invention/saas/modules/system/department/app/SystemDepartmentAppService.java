package com.legendary.invention.saas.modules.system.department.app;

import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.common.security.CurrentUser;
import com.legendary.invention.saas.modules.audit.app.OperationAuditService;
import com.legendary.invention.saas.modules.iam.service.PermissionSnapshotService;
import com.legendary.invention.saas.modules.system.department.dto.DepartmentUpsertRequest;
import com.legendary.invention.saas.modules.system.department.vo.DepartmentVO;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
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
        Long tenantId = currentTenantId(currentUser);
        List<DepartmentVO> rows = jdbcTemplate.query(
                """
                        select d.id,
                               d.tenant_id as tenantId,
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
                            where tenant_id = ?
                              and deleted = 0
                            group by dept_id
                        ) uc on uc.dept_id = d.id
                        where d.tenant_id = ?
                          and d.deleted = 0
                        order by d.sort_no asc, d.id asc
                        """,
                new BeanPropertyRowMapper<>(DepartmentVO.class),
                tenantId,
                tenantId
        );
        return buildTree(rows);
    }

    public DepartmentVO getDepartment(CurrentUser currentUser, Long id) {
        DepartmentVO department = queryDepartment(currentTenantId(currentUser), id);
        if (department == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        return department;
    }

    @Transactional
    public DepartmentVO createDepartment(CurrentUser currentUser, DepartmentUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        validateParent(tenantId, null, request.getParentId());
        validateDeptCodeUnique(tenantId, null, request.getDeptCode());
        jdbcTemplate.update(
                """
                        insert into sys_department (
                            tenant_id, parent_id, dept_code, dept_name, sort_no, status,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                tenantId,
                normalizeParentId(request.getParentId()),
                request.getDeptCode(),
                request.getDeptName(),
                request.getSortNo() == null ? 0 : request.getSortNo(),
                normalizeStatus(request.getStatus()),
                currentUser.getUserId(),
                currentUser.getUserId()
        );
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "department", "create", "CREATE", "SUCCESS", "创建部门: " + request.getDeptName());
        return getDepartment(currentUser, id);
    }

    @Transactional
    public DepartmentVO updateDepartment(CurrentUser currentUser, Long id, DepartmentUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        DepartmentVO existing = getDepartment(currentUser, id);
        validateParent(tenantId, id, request.getParentId());
        validateDeptCodeUnique(tenantId, id, request.getDeptCode());
        int updated = jdbcTemplate.update(
                """
                        update sys_department
                        set parent_id = ?,
                            dept_code = ?,
                            dept_name = ?,
                            sort_no = ?,
                            status = ?,
                            updated_by = ?,
                            updated_at = ?
                        where tenant_id = ?
                          and id = ?
                          and deleted = 0
                        """,
                normalizeParentId(request.getParentId()),
                request.getDeptCode(),
                request.getDeptName(),
                request.getSortNo() == null ? 0 : request.getSortNo(),
                normalizeStatus(request.getStatus()),
                currentUser.getUserId(),
                LocalDateTime.now(),
                tenantId,
                id
        );
        if (updated == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "department", "update", "UPDATE", "SUCCESS", "更新部门: " + existing.getDeptName());
        return getDepartment(currentUser, id);
    }

    @Transactional
    public boolean deleteDepartment(CurrentUser currentUser, Long id) {
        Long tenantId = currentTenantId(currentUser);
        DepartmentVO existing = getDepartment(currentUser, id);
        Long childCount = jdbcTemplate.queryForObject(
                "select count(1) from sys_department where tenant_id = ? and parent_id = ? and deleted = 0",
                Long.class,
                tenantId,
                id
        );
        if (childCount != null && childCount > 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "存在下级部门，不能删除");
        }
        Long userCount = jdbcTemplate.queryForObject(
                "select count(1) from sys_user_department where tenant_id = ? and dept_id = ? and deleted = 0",
                Long.class,
                tenantId,
                id
        );
        if (userCount != null && userCount > 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "部门下仍有用户，不能删除");
        }
        jdbcTemplate.update(
                """
                        update sys_department
                        set deleted = 1, updated_by = ?, updated_at = ?
                        where tenant_id = ? and id = ? and deleted = 0
                        """,
                currentUser.getUserId(),
                LocalDateTime.now(),
                tenantId,
                id
        );
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "department", "delete", "DELETE", "SUCCESS", "删除部门: " + existing.getDeptName());
        return true;
    }

    private DepartmentVO queryDepartment(Long tenantId, Long id) {
        List<DepartmentVO> list = jdbcTemplate.query(
                """
                        select d.id,
                               d.tenant_id as tenantId,
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
                            where tenant_id = ?
                              and deleted = 0
                            group by dept_id
                        ) uc on uc.dept_id = d.id
                        where d.tenant_id = ?
                          and d.id = ?
                          and d.deleted = 0
                        """,
                new BeanPropertyRowMapper<>(DepartmentVO.class),
                tenantId,
                tenantId,
                id
        );
        return list.isEmpty() ? null : list.get(0);
    }

    private void validateParent(Long tenantId, Long currentId, Long parentId) {
        Long normalizedParentId = normalizeParentId(parentId);
        if (normalizedParentId == null) {
            return;
        }
        if (normalizedParentId.equals(currentId)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "上级部门不能选择自身");
        }
        DepartmentVO parent = queryDepartment(tenantId, normalizedParentId);
        if (parent == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "上级部门不存在");
        }
        Long cursor = parent.getParentId();
        int guard = 0;
        while (cursor != null && cursor > 0 && guard++ < 32) {
            if (cursor.equals(currentId)) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "不能把部门移动到自己的下级");
            }
            DepartmentVO ancestor = queryDepartment(tenantId, cursor);
            cursor = ancestor == null ? null : ancestor.getParentId();
        }
    }

    private void validateDeptCodeUnique(Long tenantId, Long currentId, String deptCode) {
        if (!StringUtils.hasText(deptCode)) {
            return;
        }
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from sys_department
                        where tenant_id = ?
                          and dept_code = ?
                          and deleted = 0
                          and (? is null or id <> ?)
                        """,
                Long.class,
                tenantId,
                deptCode.trim(),
                currentId,
                currentId
        );
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "部门编码已存在");
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
            throw new BizException(ErrorCode.VALIDATION_ERROR, "部门状态只能是 ENABLED 或 DISABLED");
        }
        return normalized;
    }

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getCurrentTenantId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return currentUser.getCurrentTenantId();
    }
}
