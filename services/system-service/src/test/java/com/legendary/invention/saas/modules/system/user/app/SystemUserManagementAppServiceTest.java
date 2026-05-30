package com.legendary.invention.saas.modules.system.user.app;

import com.legendary.invention.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.common.security.CurrentUser;
import com.legendary.invention.saas.infrastructure.security.service.PasswordPolicyService;
import com.legendary.invention.saas.modules.audit.app.OperationAuditService;
import com.legendary.invention.saas.modules.iam.service.IamUserService;
import com.legendary.invention.saas.modules.iam.service.PermissionSnapshotService;
import com.legendary.invention.saas.modules.system.app.OnlineSessionManagementAppService;
import com.legendary.invention.saas.modules.system.dto.SystemDTO;
import com.legendary.invention.saas.modules.system.vo.SystemVO;
import com.legendary.invention.saas.modules.user.domain.UserDomainService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemUserManagementAppServiceTest {

    @Test
    void getUserShouldReturnTenantIdsAndTenantNames() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemUserManagementAppService service = buildService(jdbcTemplate);

        SystemVO.UserDetailVO user = service.getUser(currentUser(), 2001L);

        assertEquals(List.of(1001L, 1002L), user.getTenantIds());
        assertEquals(List.of("平台租户", "租户 1002"), user.getTenantNames());
    }

    @Test
    void updateUserShouldClearRolesWhenRoleIdsEmpty() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemUserManagementAppService service = buildService(jdbcTemplate);
        SystemDTO.UserUpsertRequest request = userRequest(List.of());

        assertDoesNotThrow(() -> service.updateUser(currentUser(), 2001L, request));

        assertTrue(jdbcTemplate.deletedUserRoles);
        assertEquals(0, jdbcTemplate.roleExistenceChecks);
        assertEquals(0, jdbcTemplate.insertedUserRoles);
    }

    @Test
    void updateUserShouldStillValidateRoleExistenceWhenRoleIdsProvided() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.existingRoleCount = 0L;
        SystemUserManagementAppService service = buildService(jdbcTemplate);
        SystemDTO.UserUpsertRequest request = userRequest(List.of(3001L));

        BizException exception = assertThrows(BizException.class, () -> service.updateUser(currentUser(), 2001L, request));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        assertTrue(jdbcTemplate.deletedUserRoles);
        assertEquals(1, jdbcTemplate.roleExistenceChecks);
        assertEquals(0, jdbcTemplate.insertedUserRoles);
    }

    @Test
    void updateUserShouldReplaceDepartmentsWhenDeptIdsProvided() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemUserManagementAppService service = buildService(jdbcTemplate);
        SystemDTO.UserUpsertRequest request = userRequest(List.of());
        request.setDeptIds(List.of(10L, 11L));
        request.setPrimaryDeptId(11L);

        assertDoesNotThrow(() -> service.updateUser(currentUser(), 2001L, request));

        assertTrue(jdbcTemplate.deletedUserDepartments);
        assertEquals(1, jdbcTemplate.departmentExistenceChecks);
        assertEquals(2, jdbcTemplate.insertedUserDepartments);
    }

    @Test
    void updateUserShouldRejectInaccessibleUserBeforeMutating() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.userRecordAccessCount = 0L;
        SystemUserManagementAppService service = buildService(jdbcTemplate);

        BizException exception = assertThrows(BizException.class, () -> service.updateUser(currentUser(), 2001L, userRequest(List.of())));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount);
        assertFalse(jdbcTemplate.deletedUserRoles);
        assertFalse(jdbcTemplate.deletedUserDepartments);
    }

    @Test
    void updateUserStatusShouldRejectInaccessibleUserBeforeMutating() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.userRecordAccessCount = 0L;
        SystemUserManagementAppService service = buildService(jdbcTemplate);

        BizException exception = assertThrows(BizException.class, () -> service.updateUserStatus(currentUser(), 2001L, "DISABLED"));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount);
    }

    @Test
    void deleteUserShouldRejectInaccessibleUserBeforeMutating() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.userRecordAccessCount = 0L;
        SystemUserManagementAppService service = buildService(jdbcTemplate);

        BizException exception = assertThrows(BizException.class, () -> service.deleteUser(currentUser(), 2001L));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount);
    }

    private SystemUserManagementAppService buildService(RecordingJdbcTemplate jdbcTemplate) {
        IamUserService iamUserService = mock(IamUserService.class);
        when(iamUserService.listIdentities(anyLong())).thenReturn(List.of());
        when(iamUserService.listRecentDevices(anyLong(), anyInt())).thenReturn(List.of());
        when(iamUserService.findSecuritySetting(anyLong())).thenReturn(Optional.empty());

        UserDomainService userDomainService = mock(UserDomainService.class);
        when(userDomainService.findById(anyLong())).thenReturn(Optional.empty());

        return new SystemUserManagementAppService(
                new MyBatisQueryOperations(jdbcTemplate),
                userDomainService,
                iamUserService,
                mock(PermissionSnapshotService.class),
                mock(OnlineSessionManagementAppService.class),
                mock(OperationAuditService.class),
                mock(PasswordEncoder.class),
                mock(PasswordPolicyService.class)
        );
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUsername("admin");
        currentUser.setCurrentTenantId(1001L);
        currentUser.setPermissions(java.util.Set.of("*"));
        return currentUser;
    }

    private SystemDTO.UserUpsertRequest userRequest(List<Long> roleIds) {
        SystemDTO.UserUpsertRequest request = new SystemDTO.UserUpsertRequest();
        request.setUsername("demo-user");
        request.setStatus("ENABLED");
        request.setRoleIds(roleIds);
        return request;
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private long existingRoleCount = 1L;
        private long userRecordAccessCount = 1L;
        private int updateCount;
        private int roleExistenceChecks;
        private int departmentExistenceChecks;
        private int insertedUserRoles;
        private int insertedUserDepartments;
        private boolean deletedUserRoles;
        private boolean deletedUserDepartments;

        @Override
        public int update(String sql, Object... args) {
            updateCount += 1;
            if (sql.contains("delete from sys_user_role")) {
                deletedUserRoles = true;
            }
            if (sql.contains("delete from sys_user_department")) {
                deletedUserDepartments = true;
            }
            if (sql.contains("insert into sys_user_role")) {
                insertedUserRoles += 1;
            }
            if (sql.contains("insert into sys_user_department")) {
                insertedUserDepartments += 1;
            }
            return 1;
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            return rowMapperResult();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("from sys_user u")) {
                return requiredType.cast(userRecordAccessCount);
            }
            if (sql.contains("from sys_role")) {
                roleExistenceChecks += 1;
                return requiredType.cast(existingRoleCount);
            }
            if (sql.contains("from sys_department")) {
                departmentExistenceChecks += 1;
                return requiredType.cast(2L);
            }
            return null;
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            if (sql.contains("from sys_user_tenant") && Long.class.equals(elementType)) {
                return castList(List.of(1001L, 1002L));
            }
            if (sql.contains("tenant_name") && String.class.equals(elementType)) {
                return castList(List.of("平台租户", "租户 1002"));
            }
            if (sql.contains("from sys_user_role") && Long.class.equals(elementType)) {
                return castList(List.of(2001L));
            }
            if (sql.contains("from sys_user_role") && String.class.equals(elementType)) {
                return castList(List.of("管理员"));
            }
            if (sql.contains("from sys_user_department") && Long.class.equals(elementType)) {
                return castList(List.of(10L, 11L));
            }
            if (sql.contains("from sys_user_department") && String.class.equals(elementType)) {
                return castList(List.of("产品部", "研发部"));
            }
            return new ArrayList<>();
        }

        @SuppressWarnings("unchecked")
        private <T> T rowMapperResult() {
            SystemVO.UserVO user = new SystemVO.UserVO();
            user.setId(2001L);
            user.setUsername("demo-user");
            user.setStatus("ENABLED");
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            return (T) user;
        }

        @SuppressWarnings("unchecked")
        private <T> List<T> castList(List<?> values) {
            return (List<T>) values;
        }
    }
}
