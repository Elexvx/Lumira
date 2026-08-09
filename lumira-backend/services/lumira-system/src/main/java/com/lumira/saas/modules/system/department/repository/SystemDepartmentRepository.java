package com.lumira.saas.modules.system.department.repository;

import com.lumira.saas.modules.system.department.vo.DepartmentVO;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Persistence boundary for the system-department aggregate.
 *
 * <p>The application service owns authorization, validation, closure-rebuild
 * orchestration and audit effects. This port owns only durable reads and
 * compare-and-set writes for department rows and their closure projection.</p>
 */
public interface SystemDepartmentRepository {
    List<DepartmentVO> findAllActive();

    DepartmentVO findActiveById(Long departmentId);

    DepartmentCreateResult create(DepartmentCreate command);

    int update(DepartmentUpdate command);

    boolean hasActiveChildren(Long departmentId);

    boolean hasAssignedActiveUsers(Long departmentId);

    int softDelete(DepartmentVersion department, Actor actor, LocalDateTime updatedAt);

    int retireClosureForDescendant(Long departmentId);

    List<Long> findActiveSubtreeIds(Long rootDepartmentId);

    int retireClosureForDescendants(List<Long> departmentIds);

    void ensureSelfClosure(Long departmentId, LocalDateTime createdAt);

    void ensureInheritedClosure(Long departmentId, Long parentDepartmentId, LocalDateTime createdAt);

    boolean existsActiveDeptCode(String deptCode, Long excludedDepartmentId);

    record Actor(Long userId, String userUuid) {}

    record DepartmentCreate(
            Long parentId,
            String deptCode,
            String deptName,
            Integer sortNo,
            String status,
            Actor actor
    ) {}

    record DepartmentCreateResult(int writeCount, Long departmentId) {}

    record DepartmentUpdate(
            Long departmentId,
            String expectedDeptCode,
            String expectedStatus,
            Long parentId,
            String deptCode,
            String deptName,
            Integer sortNo,
            String status,
            Actor actor
    ) {}

    record DepartmentVersion(Long departmentId, String deptCode, String status) {}
}
