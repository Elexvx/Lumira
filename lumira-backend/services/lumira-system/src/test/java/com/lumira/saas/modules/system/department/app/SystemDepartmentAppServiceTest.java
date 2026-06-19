package com.lumira.saas.modules.system.department.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.department.dto.DepartmentUpsertRequest;
import com.lumira.saas.modules.system.department.vo.DepartmentVO;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SystemDepartmentAppServiceTest {

    @Test
    void deleteDepartmentShouldUseExistsChecksAndInvalidateSnapshot() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemDepartmentAppService service = new SystemDepartmentAppService(
                queryOperations,
                permissionSnapshotService,
                new OperationAuditService(null) {
                    @Override
                    public void log(Long tenantId, Long userId, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
                    }
                }
        );

        boolean deleted = service.deleteDepartment(currentUser(), 2001L);

        assertThat(deleted).isTrue();
        assertThat(queryOperations.existsCallCount).isEqualTo(2);
        assertThat(queryOperations.countQueryCalled).isFalse();
        assertThat(queryOperations.updateCalled).isTrue();
        verify(permissionSnapshotService).invalidateTenant(1001L);
    }

    @Test
    void createDepartmentShouldRejectDuplicateCodeViaExistsCheck() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.departmentCodeExists = true;
        SystemDepartmentAppService service = new SystemDepartmentAppService(
                queryOperations,
                mock(PermissionSnapshotService.class),
                new OperationAuditService(null) {
                    @Override
                    public void log(Long tenantId, Long userId, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
                    }
                }
        );

        DepartmentUpsertRequest request = new DepartmentUpsertRequest();
        request.setDeptCode("RD");
        request.setDeptName("研发部");
        request.setStatus("ENABLED");

        assertThatThrownBy(() -> service.createDepartment(currentUser(), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(queryOperations.existsCallCount).isEqualTo(1);
        assertThat(queryOperations.countQueryCalled).isFalse();
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(2001L);
        currentUser.setUsername("admin");
        currentUser.setCurrentTenantId(1001L);
        return currentUser;
    }

    private static DepartmentVO department(Long id, Long parentId, String deptCode, String deptName) {
        DepartmentVO department = new DepartmentVO();
        department.setId(id);
        department.setTenantId(1001L);
        department.setParentId(parentId);
        department.setDeptCode(deptCode);
        department.setDeptName(deptName);
        department.setSortNo(1);
        department.setStatus("ENABLED");
        department.setUserCount(0);
        department.setCreatedAt(LocalDateTime.now());
        department.setUpdatedAt(LocalDateTime.now());
        return department;
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private boolean departmentCodeExists;
        private boolean countQueryCalled;
        private boolean updateCalled;
        private int existsCallCount;

        @Override
        public boolean exists(String sql, Object... args) {
            existsCallCount += 1;
            return departmentCodeExists && sql.contains("dept_code = ?");
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("from sys_department d")) {
                return castList(List.of(department(2001L, null, "sales", "销售部")));
            }
            return List.of();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("count(1)")) {
                countQueryCalled = true;
            }
            if (sql.contains("select last_insert_id()")) {
                return requiredType.cast(2001L);
            }
            return null;
        }

        @Override
        public int update(String sql, Object... args) {
            updateCalled = true;
            return 1;
        }

        @SuppressWarnings("unchecked")
        private <T> List<T> castList(List<?> items) {
            return (List<T>) new ArrayList<>(items);
        }
    }
}
