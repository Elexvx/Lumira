package com.lumira.saas.modules.system.user.app;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.PasswordPolicyService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.app.OnlineSessionManagementAppService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.user.domain.UserDomainService;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void getUserShouldReuseCurrentUserPermissionSnapshotWhenVersionPresent() {
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemUserManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService);

        SystemVO.UserDetailVO user = service.getUser(currentUserWithPermissionSnapshot(), 2001L);

        assertEquals(List.of(1001L, 1002L), user.getTenantIds());
        verify(permissionSnapshotService, never()).loadSnapshot(anyLong(), anyLong());
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
    void updateUserStatusShouldUseExistenceLookupForAccessCheck() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemUserManagementAppService service = buildService(jdbcTemplate);

        assertDoesNotThrow(() -> service.updateUserStatus(currentUser(), 2001L, "DISABLED"));

        assertEquals(0, jdbcTemplate.userAccessCountChecks);
    }

    @Test
    void createUserShouldUseLastInsertIdAfterInsert() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        UserDomainService userDomainService = mock(UserDomainService.class);
        SysUserEntity createdUser = new SysUserEntity();
        createdUser.setId(2001L);
        createdUser.setUsername("create-user");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(createdUser));

        IamUserService iamUserService = mock(IamUserService.class);
        when(iamUserService.listIdentities(anyLong())).thenReturn(List.of());
        when(iamUserService.listRecentDevices(anyLong(), anyInt())).thenReturn(List.of());
        when(iamUserService.findSecuritySetting(anyLong())).thenReturn(Optional.empty());

        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemUserManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService, userDomainService, iamUserService);
        SystemDTO.UserUpsertRequest request = userRequest(List.of());
        request.setUsername("create-user");
        request.setPassword("DemoPass1!");

        SystemVO.UserDetailVO detail = service.createUser(currentUser(), request);

        assertEquals(2001L, detail.getId());
        assertEquals(1, jdbcTemplate.lastInsertIdQueries);
        verify(iamUserService).createUserWithIdentity(createdUser, "create-user", "ADMIN_CREATE");
        verify(iamUserService).recordUserRegistered(2001L, "ADMIN_CREATE", null, null);
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
        UserDomainService userDomainService = mock(UserDomainService.class);
        when(userDomainService.findById(anyLong())).thenReturn(Optional.empty());

        IamUserService iamUserService = mock(IamUserService.class);
        when(iamUserService.listIdentities(anyLong())).thenReturn(List.of());
        when(iamUserService.listRecentDevices(anyLong(), anyInt())).thenReturn(List.of());
        when(iamUserService.findSecuritySetting(anyLong())).thenReturn(Optional.empty());

        return buildService(jdbcTemplate, mock(PermissionSnapshotService.class), userDomainService, iamUserService);
    }

    private SystemUserManagementAppService buildService(RecordingJdbcTemplate jdbcTemplate, PermissionSnapshotService permissionSnapshotService) {
        UserDomainService userDomainService = mock(UserDomainService.class);
        when(userDomainService.findById(anyLong())).thenReturn(Optional.empty());

        IamUserService iamUserService = mock(IamUserService.class);
        when(iamUserService.listIdentities(anyLong())).thenReturn(List.of());
        when(iamUserService.listRecentDevices(anyLong(), anyInt())).thenReturn(List.of());
        when(iamUserService.findSecuritySetting(anyLong())).thenReturn(Optional.empty());

        return buildService(jdbcTemplate, permissionSnapshotService, userDomainService, iamUserService);
    }

    private SystemUserManagementAppService buildService(
            RecordingJdbcTemplate jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            UserDomainService userDomainService,
            IamUserService iamUserService
    ) {
        return new SystemUserManagementAppService(
                new MyBatisQueryOperations(jdbcTemplate),
                userDomainService,
                iamUserService,
                permissionSnapshotService,
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

    private CurrentUser currentUserWithPermissionSnapshot() {
        CurrentUser currentUser = currentUser();
        currentUser.setPermissionsVersion("v-perf-2026-06-16");
        currentUser.setRoleIds(Set.of(1L));
        currentUser.setDeptIds(Set.of(10L));
        currentUser.setDescendantDeptIds(Set.of(10L));
        currentUser.setDataScopes(List.of());
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
        private int userAccessExistenceChecks;
        private int userAccessCountChecks;
        private int roleExistenceChecks;
        private int departmentExistenceChecks;
        private int insertedUserRoles;
        private int insertedUserDepartments;
        private int lastInsertIdQueries;
        private boolean deletedUserRoles;
        private boolean deletedUserDepartments;
        private Long lastInsertedId = 2001L;
        private String lastInsertedUsername = "demo-user";

        @Override
        public int update(String sql, Object... args) {
            updateCount += 1;
            if (sql.contains("insert into sys_user") && args.length > 0 && args[0] != null) {
                lastInsertedUsername = String.valueOf(args[0]);
            }
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
            if (sql.contains("select last_insert_id()")) {
                lastInsertIdQueries += 1;
                return requiredType.cast(lastInsertedId);
            }
            if (sql.contains("from sys_user u")) {
                if (sql.contains("select 1")) {
                    userAccessExistenceChecks += 1;
                    if (userRecordAccessCount <= 0) {
                        throw new EmptyResultDataAccessException(1);
                    }
                    return requiredType.cast(1L);
                }
                if (sql.contains("count(1)")) {
                    userAccessCountChecks += 1;
                    return requiredType.cast(userRecordAccessCount);
                }
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
        public List<java.util.Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("from sys_user u") || sql.contains("from sys_user_tenant")) {
                if (userRecordAccessCount <= 0) {
                    return new ArrayList<>();
                }
                return List.of(java.util.Map.of("exists", 1));
            }
            return new ArrayList<>();
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            if (sql.contains("from sys_user u") && Long.class.equals(elementType)) {
                if (userRecordAccessCount <= 0) {
                    return new ArrayList<>();
                }
                return castList(List.of(1L));
            }
            if (sql.contains("from sys_user_tenant") && sql.contains("status = 'ENABLED'") && Long.class.equals(elementType)) {
                if (userRecordAccessCount <= 0) {
                    return new ArrayList<>();
                }
                return castList(List.of(1L));
            }
            if (sql.contains("from sys_department") && sql.contains("deleted = 0 limit 1") && Long.class.equals(elementType)) {
                return castList(List.of(1L));
            }
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
            user.setId(lastInsertedId);
            user.setUsername(lastInsertedUsername);
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
