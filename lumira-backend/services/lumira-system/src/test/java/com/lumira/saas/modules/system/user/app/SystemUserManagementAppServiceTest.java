package com.lumira.saas.modules.system.user.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.PasswordPolicyService;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
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

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
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
    void userManagementWritesShouldPersistOperatorUuidSeparatelyFromTargetUserUuid() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/user/app/SystemUserManagementAppService.java"));

        assertTrue(source.contains("created_by, created_by_uuid, updated_by, updated_by_uuid"));
        assertTrue(source.contains("updated_by = ?, updated_by_uuid = ?"));
        assertTrue(source.contains("replaceUserRoles(userId, userUuid, request.getRoleIds(), currentUser.getUserId(), currentUser.getUserUuid())"));
        assertTrue(source.contains("replaceUserDepartments(userId, userUuid, request.getDeptIds(), request.getPrimaryDeptId(), currentUser.getUserId(), currentUser.getUserUuid()"));
        assertFalse(source.contains("delete from sys_user_role where user_id = ? and user_uuid = ?"));
        assertFalse(source.contains("delete from sys_user_department where user_id = ? and user_uuid = ?"));
        assertTrue(source.contains("update sys_user_role"));
        assertTrue(source.contains("update sys_user_department"));
        assertTrue(source.contains("from sys_role r"));
        assertTrue(source.contains("status = 'ENABLED' and id in"));
        assertTrue(source.contains("where r.id = ? and r.status = 'ENABLED' and r.deleted = 0"));
        assertTrue(source.contains("from sys_department d"));
        assertTrue(source.contains("deleted = case when user_id = values(user_id) and user_uuid = values(user_uuid) and role_id = values(role_id) then 0 else deleted end"));
        assertTrue(source.contains("primary_flag = case when user_id = values(user_id) and user_uuid = values(user_uuid) and dept_id = values(dept_id) then values(primary_flag) else primary_flag end"));
        assertTrue(source.contains("deleted = case when user_id = values(user_id) and user_uuid = values(user_uuid) and dept_id = values(dept_id) then 0 else deleted end"));
        assertTrue(source.contains("requireRelationshipWrite(inserted, \"Role changed, please retry\")"));
        assertTrue(source.contains("requireRelationshipWrite(inserted, \"Department changed, please retry\")"));
        assertTrue(source.contains("int updated = jdbcTemplate.update("));
        assertTrue(source.contains("int deleted = jdbcTemplate.update("));
        assertTrue(source.contains("User changed, please retry"));
    }

    @Test
    void getUserShouldNotQueryTenantMembership() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemUserManagementAppService service = buildService(jdbcTemplate);

        service.getUser(currentUser(), 2001L);

        assertFalse(jdbcTemplate.seenTenantReference);
    }

    @Test
    void getUserShouldReuseCurrentUserPermissionSnapshotWhenVersionPresent() {
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemUserManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(permissionSnapshot(Set.of("*")));

        service.getUser(currentUserWithPermissionSnapshot(), 2001L);

        verify(permissionSnapshotService, org.mockito.Mockito.times(2)).loadSnapshot(1001L, "user-uuid-1001");
    }

    @Test
    void resolvePermissionSnapshotShouldRejectUntrustedCurrentUserSnapshot() throws Exception {
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemUserManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService);
        CurrentUser currentUser = currentUserWithPermissionSnapshot();
        currentUser.setSessionId(null);
        Method method = SystemUserManagementAppService.class.getDeclaredMethod("resolvePermissionSnapshot", CurrentUser.class);
        method.setAccessible(true);

        PermissionSnapshotService.PermissionSnapshot snapshot =
                (PermissionSnapshotService.PermissionSnapshot) method.invoke(service, currentUser);

        assertTrue(snapshot.getPermissionList().isEmpty());
        verify(permissionSnapshotService, never()).loadSnapshot(anyLong(), org.mockito.ArgumentMatchers.anyString());
        verify(permissionSnapshotService, never()).loadRoleSnapshot(anyLong());
    }

    @Test
    void listUserRolesShouldRejectInaccessibleUserBeforeQueryingRoles() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.userRecordAccessCount = 0L;
        SystemUserManagementAppService service = buildService(jdbcTemplate);

        BizException exception = assertThrows(BizException.class, () -> service.listUserRoles(currentUser(), 2001L));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        assertEquals(1, jdbcTemplate.userAccessExistenceChecks);
        assertEquals(0, jdbcTemplate.userRoleQueries);
    }

    @Test
    void listUserRolesShouldRejectUnauthenticatedUserBeforeDatabaseAccess() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemUserManagementAppService service = buildService(jdbcTemplate);
        CurrentUser currentUser = currentUser();
        currentUser.setAuthenticated(false);

        BizException exception = assertThrows(BizException.class, () -> service.listUserRoles(currentUser, 2001L));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.userAccessExistenceChecks);
        assertEquals(0, jdbcTemplate.userRoleQueries);
    }

    @Test
    void listUserRolesShouldRejectBlankUsernameBeforeDatabaseAccess() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemUserManagementAppService service = buildService(jdbcTemplate);
        CurrentUser currentUser = currentUser();
        currentUser.setUsername(" ");

        BizException exception = assertThrows(BizException.class, () -> service.listUserRoles(currentUser, 2001L));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.userAccessExistenceChecks);
        assertEquals(0, jdbcTemplate.userRoleQueries);
    }

    @Test
    void listUserRolesShouldRejectMissingUserUuidBeforeDatabaseAccess() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemUserManagementAppService service = buildService(jdbcTemplate);
        CurrentUser currentUser = currentUser();
        currentUser.setUserUuid(" ");

        BizException exception = assertThrows(BizException.class, () -> service.listUserRoles(currentUser, 2001L));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.userAccessExistenceChecks);
        assertEquals(0, jdbcTemplate.userRoleQueries);
    }

    @Test
    void updateUserShouldClearRolesWhenRoleIdsEmpty() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemUserManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService);
        SystemDTO.UserUpsertRequest request = userRequest(List.of());

        assertDoesNotThrow(() -> service.updateUser(currentUser(), 2001L, request));

        assertTrue(jdbcTemplate.deletedUserRoles);
        assertEquals(0, jdbcTemplate.roleExistenceChecks);
        assertEquals(0, jdbcTemplate.insertedUserRoles);
        assertFalse(jdbcTemplate.seenTenantReference);
        verify(permissionSnapshotService).invalidatePermissions();
    }

    @Test
    void updateUserShouldRejectBlankUsernameBeforeDatabaseAccess() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemUserManagementAppService service = buildService(jdbcTemplate);
        CurrentUser currentUser = currentUser();
        currentUser.setUsername(" ");

        BizException exception = assertThrows(BizException.class, () -> service.updateUser(currentUser, 2001L, userRequest(List.of())));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.userAccessExistenceChecks);
        assertEquals(0, jdbcTemplate.updateCount);
    }

    @Test
    void updateUserShouldRejectMissingPermissionsVersionBeforeAccessCheckAndMutation() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemUserManagementAppService service = buildService(jdbcTemplate);
        CurrentUser currentUser = currentUser();
        currentUser.setPermissionsVersion(" ");

        BizException exception = assertThrows(BizException.class, () -> service.updateUser(currentUser, 2001L, userRequest(List.of())));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.userAccessExistenceChecks);
        assertEquals(0, jdbcTemplate.updateCount);
    }

    @Test
    void updateUserShouldValidateRoleExistenceBeforeMutating() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.existingRoleCount = 0L;
        SystemUserManagementAppService service = buildService(jdbcTemplate);
        SystemDTO.UserUpsertRequest request = userRequest(List.of(3001L));

        BizException exception = assertThrows(BizException.class, () -> service.updateUser(currentUser(), 2001L, request));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount);
        assertFalse(jdbcTemplate.deletedUserRoles);
        assertEquals(1, jdbcTemplate.roleExistenceChecks);
        assertEquals(0, jdbcTemplate.insertedUserRoles);
    }

    @Test
    void updateUserShouldRejectPrivilegedRoleAssignmentByNonSuperUser() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.privilegedRolePermissionCount = 1L;
        SystemUserManagementAppService service = buildService(jdbcTemplate);
        SystemDTO.UserUpsertRequest request = userRequest(List.of(3001L));

        BizException exception = assertThrows(BizException.class, () -> service.updateUser(limitedUser(), 2001L, request));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount);
        assertFalse(jdbcTemplate.deletedUserRoles);
        assertEquals(1, jdbcTemplate.roleExistenceChecks);
        assertEquals(1, jdbcTemplate.privilegedRoleChecks);
        assertEquals(0, jdbcTemplate.insertedUserRoles);
    }

    @Test
    void updateUserShouldRejectInvalidRoleIdBeforeMutating() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemUserManagementAppService service = buildService(jdbcTemplate);

        BizException exception = assertThrows(BizException.class, () -> service.updateUser(currentUser(), 2001L, userRequest(List.of(0L))));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount);
        assertFalse(jdbcTemplate.deletedUserRoles);
        assertEquals(0, jdbcTemplate.roleExistenceChecks);
    }

    @Test
    void updateUserShouldAllowPrivilegedRoleAssignmentBySuperUser() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.privilegedRolePermissionCount = 1L;
        SystemUserManagementAppService service = buildService(jdbcTemplate);
        SystemDTO.UserUpsertRequest request = userRequest(List.of(3001L));

        assertDoesNotThrow(() -> service.updateUser(currentUser(), 2001L, request));

        assertEquals(0, jdbcTemplate.privilegedRoleChecks);
        assertEquals(1, jdbcTemplate.insertedUserRoles);
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
        assertFalse(jdbcTemplate.seenTenantReference);
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
    void updateUserShouldRequireUpdatePermissionBeforeAccessCheck() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemUserManagementAppService service = buildService(jdbcTemplate);

        BizException exception = assertThrows(
                BizException.class,
                () -> service.updateUser(userWithPermission("system:user:view"), 2001L, userRequest(List.of()))
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.userAccessExistenceChecks);
        assertEquals(0, jdbcTemplate.updateCount);
    }

    @Test
    void updateUserShouldRejectNullRequestBeforeAccessCheck() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemUserManagementAppService service = buildService(jdbcTemplate);

        BizException exception = assertThrows(BizException.class, () -> service.updateUser(currentUser(), 2001L, null));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.userAccessExistenceChecks);
        assertEquals(0, jdbcTemplate.updateCount);
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
        createdUser.setUuid("user-uuid-2001");
        createdUser.setUsername("create-user");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(createdUser));

        IamUserService iamUserService = mock(IamUserService.class);
        when(iamUserService.listIdentities(anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());
        when(iamUserService.listRecentDevices(anyLong(), org.mockito.ArgumentMatchers.anyString(), anyInt())).thenReturn(List.of());
        when(iamUserService.findSecuritySetting(anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());

        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemUserManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService, userDomainService, iamUserService);
        SystemDTO.UserUpsertRequest request = userRequest(List.of());
        request.setUsername("create-user");
        request.setPassword("DemoPass1!");

        SystemVO.UserDetailVO detail = service.createUser(currentUser(), request);

        assertEquals(2001L, detail.getId());
        assertEquals(1, jdbcTemplate.lastInsertIdQueries);
        verify(iamUserService).createUserWithIdentity(createdUser, "create-user", "ADMIN_CREATE");
        verify(iamUserService).recordUserRegistered(2001L, "user-uuid-2001", "ADMIN_CREATE", null, null);
    }

    @Test
    void createUserShouldRejectWhenMainInsertMissesBeforeIdentitySync() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.updateResults.add(0);
        UserDomainService userDomainService = mock(UserDomainService.class);
        IamUserService iamUserService = mock(IamUserService.class);
        when(iamUserService.listIdentities(anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());
        when(iamUserService.listRecentDevices(anyLong(), org.mockito.ArgumentMatchers.anyString(), anyInt())).thenReturn(List.of());
        when(iamUserService.findSecuritySetting(anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());

        SystemUserManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class), userDomainService, iamUserService);
        SystemDTO.UserUpsertRequest request = userRequest(List.of());
        request.setUsername("create-user");
        request.setPassword("DemoPass1!");

        BizException exception = assertThrows(BizException.class, () -> service.createUser(currentUser(), request));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("User changed, please retry"));
        assertEquals(0, jdbcTemplate.lastInsertIdQueries);
        verify(userDomainService, never()).findById(anyLong());
        verify(iamUserService, never()).createUserWithIdentity(
                org.mockito.ArgumentMatchers.any(SysUserEntity.class),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
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

    @Test
    void deleteUserShouldRejectWhenMainDeleteMissesBeforeCleaningRelations() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.updateResults.add(0);
        IamUserService iamUserService = mock(IamUserService.class);
        when(iamUserService.listIdentities(anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());
        when(iamUserService.listRecentDevices(anyLong(), org.mockito.ArgumentMatchers.anyString(), anyInt())).thenReturn(List.of());
        when(iamUserService.findSecuritySetting(anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        SystemUserManagementAppService service = buildService(
                jdbcTemplate,
                mock(PermissionSnapshotService.class),
                mock(UserDomainService.class),
                iamUserService
        );

        BizException exception = assertThrows(BizException.class, () -> service.deleteUser(currentUser(), 2001L));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("User changed, please retry"));
        assertFalse(jdbcTemplate.deletedUserRoles);
        assertFalse(jdbcTemplate.deletedUserDepartments);
        verify(iamUserService, never()).softDeleteUser(anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void updateUserShouldRejectWhenMainUserWriteMissesBeforeReplacingRelations() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.updateResults.add(0);
        SystemUserManagementAppService service = buildService(jdbcTemplate);

        BizException exception = assertThrows(BizException.class, () -> service.updateUser(currentUser(), 2001L, userRequest(List.of(1L))));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("User changed, please retry"));
        assertFalse(jdbcTemplate.deletedUserRoles);
        assertFalse(jdbcTemplate.deletedUserDepartments);
    }

    @Test
    void updateUserShouldRejectWhenPasswordWriteMissesBeforeSyncingCredential() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.updateResults.add(1);
        jdbcTemplate.updateResults.add(0);
        IamUserService iamUserService = mock(IamUserService.class);
        when(iamUserService.listIdentities(anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());
        when(iamUserService.listRecentDevices(anyLong(), org.mockito.ArgumentMatchers.anyString(), anyInt())).thenReturn(List.of());
        when(iamUserService.findSecuritySetting(anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        SystemUserManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class), mock(UserDomainService.class), iamUserService);
        SystemDTO.UserUpsertRequest request = userRequest(List.of());
        request.setPassword("DemoPass1!");

        BizException exception = assertThrows(BizException.class, () -> service.updateUser(currentUser(), 2001L, request));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("User changed, please retry"));
        verify(iamUserService, never()).upsertPasswordCredential(anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getUserShouldRejectDisabledTrustedUserBeforeAccessCheck() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemUserManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(false);

        BizException exception = assertThrows(BizException.class, () -> service.getUser(currentUser(), 2001L));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.userAccessExistenceChecks);
    }

    @Test
    void getUserShouldRejectDisabledTrustedUserIdentityBeforeAccessCheck() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "admin-live", "DISABLED"));
        SystemUserManagementAppService service = buildService(
                jdbcTemplate,
                permissionSnapshotService,
                null,
                systemInternalApi,
                null,
                mock(UserDomainService.class),
                defaultIamUserService()
        );

        BizException exception = assertThrows(BizException.class, () -> service.getUser(currentUser(), 2001L));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.userAccessExistenceChecks);
        verify(permissionSnapshotService, never()).isTrustedActiveUser(anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getUserShouldRejectRevokedSessionTicketBeforeAccessCheck() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        SystemUserManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class), sessionAuthenticationService);

        BizException exception = assertThrows(BizException.class, () -> service.getUser(currentUser(), 2001L));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.userAccessExistenceChecks);
    }

    @Test
    void updateUserShouldRejectWhenLiveSnapshotRevokesUpdatePermissionBeforeAccessCheck() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemUserManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(permissionSnapshot(Set.of("system:user:view")));

        BizException exception = assertThrows(BizException.class, () -> service.updateUser(currentUser(), 2001L, userRequest(List.of())));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.userAccessExistenceChecks);
        assertEquals(0, jdbcTemplate.updateCount);
    }

    @Test
    void updateUserStatusShouldLogRefreshedLiveUsername() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(permissionSnapshot(Set.of("*")));
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "admin-live", "ENABLED"));
        OperationAuditService operationAuditService = mock(OperationAuditService.class);
        UserDomainService userDomainService = mock(UserDomainService.class);
        when(userDomainService.findById(anyLong())).thenReturn(Optional.empty());

        SystemUserManagementAppService service = buildService(
                jdbcTemplate,
                permissionSnapshotService,
                null,
                systemInternalApi,
                operationAuditService,
                userDomainService,
                defaultIamUserService()
        );
        CurrentUser currentUser = currentUser();
        currentUser.setUsername("stale-admin");

        assertTrue(service.updateUserStatus(currentUser, 2001L, "DISABLED"));

        assertEquals("admin-live", currentUser.getUsername());
        verify(operationAuditService).log(
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.eq("user-uuid-1001"),
                org.mockito.ArgumentMatchers.eq("admin-live"),
                org.mockito.ArgumentMatchers.eq("user"),
                org.mockito.ArgumentMatchers.eq("status"),
                org.mockito.ArgumentMatchers.eq("UPDATE"),
                org.mockito.ArgumentMatchers.eq("SUCCESS"),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    private SystemUserManagementAppService buildService(RecordingJdbcTemplate jdbcTemplate) {
        UserDomainService userDomainService = mock(UserDomainService.class);
        when(userDomainService.findById(anyLong())).thenReturn(Optional.empty());

        IamUserService iamUserService = defaultIamUserService();

        return buildService(jdbcTemplate, mock(PermissionSnapshotService.class), userDomainService, iamUserService);
    }

    private SystemUserManagementAppService buildService(RecordingJdbcTemplate jdbcTemplate, PermissionSnapshotService permissionSnapshotService) {
        UserDomainService userDomainService = mock(UserDomainService.class);
        when(userDomainService.findById(anyLong())).thenReturn(Optional.empty());

        IamUserService iamUserService = defaultIamUserService();

        return buildService(jdbcTemplate, permissionSnapshotService, userDomainService, iamUserService);
    }

    private SystemUserManagementAppService buildService(
            RecordingJdbcTemplate jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        UserDomainService userDomainService = mock(UserDomainService.class);
        when(userDomainService.findById(anyLong())).thenReturn(Optional.empty());

        IamUserService iamUserService = defaultIamUserService();

        return buildService(jdbcTemplate, permissionSnapshotService, sessionAuthenticationService, userDomainService, iamUserService);
    }

    private SystemUserManagementAppService buildService(
            RecordingJdbcTemplate jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            UserDomainService userDomainService,
            IamUserService iamUserService
    ) {
        return buildService(jdbcTemplate, permissionSnapshotService, null, null, null, userDomainService, iamUserService);
    }

    private SystemUserManagementAppService buildService(
            RecordingJdbcTemplate jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService,
            UserDomainService userDomainService,
            IamUserService iamUserService
    ) {
        return buildService(jdbcTemplate, permissionSnapshotService, sessionAuthenticationService, null, null, userDomainService, iamUserService);
    }

    private SystemUserManagementAppService buildService(
            RecordingJdbcTemplate jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService,
            SystemInternalApi systemInternalApi,
            OperationAuditService operationAuditService,
            UserDomainService userDomainService,
            IamUserService iamUserService
    ) {
        when(permissionSnapshotService.isTrustedActiveUser(anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        return new SystemUserManagementAppService(
                new MyBatisQueryOperations(jdbcTemplate),
                userDomainService,
                iamUserService,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                mock(OnlineSessionManagementAppService.class),
                operationAuditService == null ? mock(OperationAuditService.class) : operationAuditService,
                mock(PasswordEncoder.class),
                mock(PasswordPolicyService.class)
        );
    }

    private IamUserService defaultIamUserService() {
        IamUserService iamUserService = mock(IamUserService.class);
        when(iamUserService.listIdentities(anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());
        when(iamUserService.listRecentDevices(anyLong(), org.mockito.ArgumentMatchers.anyString(), anyInt())).thenReturn(List.of());
        when(iamUserService.findSecuritySetting(anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        return iamUserService;
    }

    private CurrentUser currentUser() {
        return userWithPermission("*");
    }

    private CurrentUser userWithPermission(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("admin");
        currentUser.setAuthenticated(true);
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setPermissions(java.util.Set.of(permission));
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

    private CurrentUser limitedUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1002L);
        currentUser.setUserUuid("user-uuid-1002");
        currentUser.setUsername("operator");
        currentUser.setAuthenticated(true);
        currentUser.setSessionId("session-2");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-2");
        currentUser.setPermissions(Set.of("system:user:update"));
        return currentUser;
    }

    private PermissionSnapshotService.PermissionSnapshot permissionSnapshot(Set<String> permissions) {
        return new PermissionSnapshotService.PermissionSnapshot(
                "permissions-2",
                permissions,
                Set.of(1L),
                10L,
                Set.of(10L),
                Set.of(10L),
                List.of(),
                "/dashboard/home"
        );
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String username, String status) {
        return new SystemUserSnapshotDTO(
                userId,
                userUuid,
                username,
                null,
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
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
        private int privilegedRoleChecks;
        private int departmentExistenceChecks;
        private int insertedUserRoles;
        private int insertedUserDepartments;
        private int lastInsertIdQueries;
        private int userRoleQueries;
        private boolean deletedUserRoles;
        private boolean deletedUserDepartments;
        private boolean seenTenantReference;
        private Long lastInsertedId = 2001L;
        private String lastInsertedUsername = "demo-user";
        private long privilegedRolePermissionCount;
        private final Queue<Integer> updateResults = new ArrayDeque<>();

        @Override
        public int update(String sql, Object... args) {
            recordTenantUsage(sql, args);
            updateCount += 1;
            if (sql.contains("insert into sys_user") && args.length > 1 && args[1] != null) {
                lastInsertedUsername = String.valueOf(args[1]);
            }
            if (sql.contains("delete from sys_user_role") || sql.contains("update sys_user_role")) {
                deletedUserRoles = true;
            }
            if (sql.contains("delete from sys_user_department") || sql.contains("update sys_user_department")) {
                deletedUserDepartments = true;
            }
            if (sql.contains("insert into sys_user_role")) {
                insertedUserRoles += 1;
            }
            if (sql.contains("insert into sys_user_department")) {
                insertedUserDepartments += 1;
            }
            return updateResults.isEmpty() ? 1 : updateResults.remove();
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            recordTenantUsage(sql, args);
            return rowMapperResult();
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            recordTenantUsage(sql, args);
            if (sql.contains("from sys_user_role")) {
                userRoleQueries += 1;
            }
            return new ArrayList<>();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            recordTenantUsage(sql, args);
            if (sql.contains("select last_insert_id()")) {
                lastInsertIdQueries += 1;
                return requiredType.cast(lastInsertedId);
            }
            if (sql.contains("select uuid from sys_user")) {
                return requiredType.cast("user-uuid-" + args[0]);
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
            if (sql.contains("from sys_role_permission")) {
                privilegedRoleChecks += 1;
                return requiredType.cast(privilegedRolePermissionCount);
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
            recordTenantUsage(sql, args);
            if (sql.contains("from sys_user u")) {
                if (sql.contains("select 1")) {
                    userAccessExistenceChecks += 1;
                }
                if (userRecordAccessCount <= 0) {
                    return new ArrayList<>();
                }
                return List.of(java.util.Map.of("exists", 1));
            }
            return new ArrayList<>();
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            recordTenantUsage(sql, args);
            if (sql.contains("from sys_user u") && Long.class.equals(elementType)) {
                if (userRecordAccessCount <= 0) {
                    return new ArrayList<>();
                }
                return castList(List.of(1L));
            }
            if (sql.contains("from sys_department") && sql.contains("deleted = 0 limit 1") && Long.class.equals(elementType)) {
                return castList(List.of(1L));
            }
            if (sql.contains("from sys_user_role") && Long.class.equals(elementType)) {
                userRoleQueries += 1;
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

        private void recordTenantUsage(String sql, Object... args) {
            if (sql != null && sql.toLowerCase().contains("tenant")) {
                seenTenantReference = true;
            }
            for (Object arg : args) {
                if (Long.valueOf(2002L).equals(arg)) {
                    seenTenantReference = true;
                }
            }
        }

        @SuppressWarnings("unchecked")
        private <T> T rowMapperResult() {
            SystemVO.UserVO user = new SystemVO.UserVO();
            user.setId(lastInsertedId);
            user.setUid("user-uuid-" + lastInsertedId);
            user.setUserUuid("user-uuid-" + lastInsertedId);
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
