package com.lumira.saas.modules.system.role.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.domain.event.DomainEvent;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.role.dto.RoleDataScopeRequest;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemRoleManagementAppServiceTest {

    @Test
    void defaultRegistrationRoleConfigWritesShouldPersistTrustedUserUuid() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/role/app/SystemRoleManagementAppService.java"));

        assertTrue(source.contains("created_by, created_by_uuid, updated_by, updated_by_uuid"));
        assertTrue(source.contains("updated_by = ?, updated_by_uuid = ?"));
        assertTrue(source.contains("currentUser.getUserUuid()"));
        assertTrue(source.contains("and config_key = ?"));
        assertTrue(source.contains("and config_scope = 'PLATFORM'"));
        assertTrue(source.contains("and is_system = 0"));
        assertTrue(source.contains("and deleted = 0"));
        assertFalse(source.contains("updated_at = ?, deleted = 0"));
        assertTrue(source.contains("Role config changed, please retry"));
        assertTrue(source.contains("join sys_user u"));
        assertTrue(source.contains("u.uuid = ur.user_uuid"));
    }

    @Test
    void listRolesShouldReturnCountsAndDefaultRegistrationRole() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));

        PageResponse<SystemVO.RoleVO> page = service.listRoles(currentUser(), "common", "普通", "BUSINESS", 1, 10);

        assertEquals(2, page.getTotal());
        assertEquals(2, page.getRecords().size());
        assertEquals(3, page.getRecords().get(0).getPermissionCount());
        assertEquals(7, page.getRecords().get(0).getUserCount());
        assertTrue(page.getRecords().get(0).getDefaultRegistrationRole());
        assertEquals(0, jdbcTemplate.roleListCountQueries);
    }

    @Test
    void getRoleShouldReturnPermissionKeys() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));

        SystemVO.RoleDetailVO role = service.getRole(currentUser(), 2001L);

        assertEquals(List.of("system:user:view", "system:role:view"), role.getPermissionKeys());
        assertEquals(3, role.getPermissionCount());
        assertEquals(7, role.getUserCount());
        assertTrue(role.getDefaultRegistrationRole());
    }

    @Test
    void listRolesShouldRejectMissingSessionVersionBeforeDatabaseAccess() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));
        CurrentUser currentUser = currentUser();
        currentUser.setSessionVersion(null);

        BizException exception = assertThrows(BizException.class, () -> service.listRoles(currentUser, null, null, null, 1, 10));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.roleListCountQueries);
    }

    @Test
    void updateDefaultRegistrationRoleShouldWriteSysConfigAndReturnRole() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.rolePermissionKeys = List.of("dashboard:view", "profile:view", "aiadc:registration:view");
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));

        SystemVO.DefaultRegistrationRoleVO role = service.updateDefaultRegistrationRole(currentUser(), 2001L);

        assertEquals("commonuser", jdbcTemplate.upsertedDefaultRoleCode);
        assertEquals("commonuser", role.getRoleCode());
        assertTrue(jdbcTemplate.auditLogged);
    }

    @Test
    void updateDefaultRegistrationRoleShouldRejectAdministratorRole() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.roleById = role(1001L, "ADMIN", "Administrator", "SYSTEM");
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));

        BizException exception = assertThrows(BizException.class, () -> service.updateDefaultRegistrationRole(currentUser(), 1001L));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        assertEquals(null, jdbcTemplate.upsertedDefaultRoleCode);
    }

    @Test
    void updateDefaultRegistrationRoleShouldRejectRoleWithPrivilegedPermissions() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.rolePermissionKeys = List.of("dashboard:view", "system:config:view");
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));

        BizException exception = assertThrows(BizException.class, () -> service.updateDefaultRegistrationRole(currentUser(), 2001L));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        assertEquals(null, jdbcTemplate.upsertedDefaultRoleCode);
    }

    @Test
    void updateDefaultRegistrationRoleShouldRejectRoleWithManagementPermissions() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.rolePermissionKeys = List.of("dashboard:view", "system:user:view");
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));

        BizException exception = assertThrows(BizException.class, () -> service.updateDefaultRegistrationRole(currentUser(), 2001L));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        assertEquals(null, jdbcTemplate.upsertedDefaultRoleCode);
    }

    @Test
    void updateDefaultRegistrationRoleShouldRejectRoleWithPluginOrWorkflowPermissions() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.rolePermissionKeys = List.of("dashboard:view", "plugin:sensitive-words:view", "workflow:view");
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));

        BizException exception = assertThrows(BizException.class, () -> service.updateDefaultRegistrationRole(currentUser(), 2001L));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        assertEquals(null, jdbcTemplate.upsertedDefaultRoleCode);
    }

    @Test
    void createRoleShouldIgnoreNonAssignablePermissionsAndInvalidateSnapshot() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemRoleManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService);

        SystemDTO.RoleUpsertRequest request = roleRequest("auditor", "审计员", List.of("audit:view", "audit:view", "audit:unknown"));
        SystemVO.RoleDetailVO role = service.createRole(currentUser(), request);

        assertTrue(jdbcTemplate.insertedRole);
        assertEquals(1, jdbcTemplate.lastInsertIdQueries);
        assertTrue(jdbcTemplate.deletedRolePermissions);
        assertTrue(jdbcTemplate.insertedPermissionKeys.isEmpty());
        verify(permissionSnapshotService).invalidatePermissions();
        assertEquals(2001L, role.getId());
    }

    @Test
    void createRoleShouldInsertDefaultDataScope() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemRoleManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService);

        SystemDTO.RoleUpsertRequest request = roleRequest("auditor", "审计员", List.of("system:user:view"));
        service.createRole(currentUser(), request);

        assertEquals(List.of("*:SELF"), jdbcTemplate.insertedDataScopes);
        verify(permissionSnapshotService).invalidatePermissions();
    }

    @Test
    void createRoleShouldRejectFullDataScopeForNonSuperUser() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:role:create")));
        SystemRoleManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService);

        SystemDTO.RoleUpsertRequest request = roleRequest("ops", "Ops", List.of("system:user:view"));
        request.setDataScopes(List.of(dataScope("system:user", "ALL")));

        BizException exception = assertThrows(BizException.class, () -> service.createRole(userWithPermission("system:role:create"), request));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        assertTrue(jdbcTemplate.insertedDataScopes.isEmpty());
    }

    @Test
    void createRoleShouldAllowFullDataScopeForSuperUser() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));

        SystemDTO.RoleUpsertRequest request = roleRequest("ops", "Ops", List.of("system:user:view"));
        request.setDataScopes(List.of(dataScope("system:user", "ALL")));

        service.createRole(superUser(), request);

        assertEquals(List.of("system:user:ALL"), jdbcTemplate.insertedDataScopes);
    }

    @Test
    void listRolesShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemRoleManagementAppService service = new SystemRoleManagementAppService(
                new MyBatisQueryOperations(jdbcTemplate),
                null,
                new RecordingOperationAuditService(jdbcTemplate)
        );

        BizException exception = assertThrows(BizException.class, () -> service.listRoles(currentUser(), null, null, null, 1, 10));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.roleListCountQueries);
    }

    @Test
    void listRolesShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(null);
        SystemRoleManagementAppService service = new SystemRoleManagementAppService(
                new MyBatisQueryOperations(jdbcTemplate),
                permissionSnapshotService,
                new RecordingOperationAuditService(jdbcTemplate)
        );

        BizException exception = assertThrows(BizException.class, () -> service.listRoles(currentUser(), null, null, null, 1, 10));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Trusted user permission snapshot is unavailable"));
        assertEquals(0, jdbcTemplate.roleListCountQueries);
    }

    @Test
    void createRoleShouldRejectFullDataScopeWhenLiveSnapshotRemovesWildcardPermission() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:role:create")));
        SystemRoleManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService);

        SystemDTO.RoleUpsertRequest request = roleRequest("ops", "Ops", List.of("system:user:view"));
        request.setDataScopes(List.of(dataScope("system:user", "ALL")));

        BizException exception = assertThrows(BizException.class, () -> service.createRole(superUser(), request));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        assertFalse(jdbcTemplate.insertedRole);
        assertTrue(jdbcTemplate.insertedDataScopes.isEmpty());
        verify(permissionSnapshotService, never()).invalidatePermissions();
    }

    @Test
    void updateRolePermissionsShouldReplacePermissionsAndInvalidateSnapshot() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemRoleManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService);

        boolean updated = service.updateRolePermissions(currentUser(), 2001L, List.of("system:user:view", "system:user:view", "system:role:view"));

        assertTrue(updated);
        assertTrue(jdbcTemplate.deletedRolePermissions);
        assertEquals(List.of("system:user:view", "system:role:view"), jdbcTemplate.insertedPermissionKeys);
        assertThat(jdbcTemplate.updateSql).anySatisfy(sql -> assertThat(sql)
                .contains("insert into sys_role_permission")
                .contains("from sys_role r")
                .contains("r.role_code = ?")
                .contains("r.role_type = ?"));
        verify(permissionSnapshotService).invalidatePermissions();
    }

    @Test
    void updateRolePermissionsShouldRejectWildcardPermission() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemRoleManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService);

        boolean updated = service.updateRolePermissions(currentUser(), 2001L, List.of("system:user:view", "*", "system:role:view"));

        assertTrue(updated);
        assertTrue(jdbcTemplate.deletedRolePermissions);
        assertEquals(List.of("system:user:view", "system:role:view"), jdbcTemplate.insertedPermissionKeys);
        verify(permissionSnapshotService).invalidatePermissions();
    }

    @Test
    void updateRolePermissionsShouldRejectPartialPermissionBatchInsert() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.rolePermissionInsertResult = 1;
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemRoleManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService);

        BizException exception = assertThrows(
                BizException.class,
                () -> service.updateRolePermissions(currentUser(), 2001L, List.of("system:user:view", "system:role:view"))
        );

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Role changed, please retry"));
        verify(permissionSnapshotService, org.mockito.Mockito.never()).invalidatePermissions();
    }

    @Test
    void updateRolePermissionsShouldPublishDomainEventWhenPermissionSetChanges() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        RecordingDomainEventPublisher domainEventPublisher = new RecordingDomainEventPublisher();
        SystemRoleManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService, domainEventPublisher);

        service.updateRolePermissions(currentUser(), 2001L, List.of("system:user:view"));

        assertEquals(1, domainEventPublisher.events.size());
        assertEquals("IAM_ROLE_PERMISSIONS_CHANGED", domainEventPublisher.events.getFirst().eventType());
        assertEquals(1001L, domainEventPublisher.events.getFirst().attributes().get("userId"));
        assertEquals("user-uuid-1001", domainEventPublisher.events.getFirst().attributes().get("userUuid"));
    }

    @Test
    void updateRolePermissionsShouldRequireGrantPermissionBeforeDatabaseLookup() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:role:update")));
        SystemRoleManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService);

        BizException exception = assertThrows(
                BizException.class,
                () -> service.updateRolePermissions(userWithPermission("system:role:update"), 2001L, List.of("system:user:view"))
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.rolePermissionLookupCount);
        assertTrue(jdbcTemplate.insertedPermissionKeys.isEmpty());
    }

    @Test
    void updateRolePermissionsShouldRejectRevokedSessionTicketBeforeDatabaseLookup() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is required"));
        SystemRoleManagementAppService service = buildService(
                jdbcTemplate,
                mock(PermissionSnapshotService.class),
                event -> {
                },
                sessionAuthenticationService
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> service.updateRolePermissions(currentUser(), 2001L, List.of("system:user:view"))
        );

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.rolePermissionLookupCount);
        assertFalse(jdbcTemplate.deletedRolePermissions);
        assertTrue(jdbcTemplate.insertedPermissionKeys.isEmpty());
    }

    @Test
    void updateRolePermissionsShouldRejectDisabledTrustedUserIdentityBeforeDatabaseLookup() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "admin-live", "DISABLED"));
        SystemRoleManagementAppService service = buildService(
                jdbcTemplate,
                permissionSnapshotService,
                event -> {
                },
                systemInternalApi,
                null
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> service.updateRolePermissions(currentUser(), 2001L, List.of("system:user:view"))
        );

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.rolePermissionLookupCount);
        assertFalse(jdbcTemplate.deletedRolePermissions);
        assertTrue(jdbcTemplate.insertedPermissionKeys.isEmpty());
        verify(permissionSnapshotService, never()).isTrustedActiveUser(anyLong(), anyString());
    }

    @Test
    void updateRolePermissionsShouldRejectBlankLiveUsernameBeforeDatabaseLookup() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", " ", "ENABLED"));
        SystemRoleManagementAppService service = buildService(
                jdbcTemplate,
                permissionSnapshotService,
                event -> {
                },
                systemInternalApi,
                null
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> service.updateRolePermissions(currentUser(), 2001L, List.of("system:user:view"))
        );

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Trusted user username is unavailable"));
        assertEquals(0, jdbcTemplate.rolePermissionLookupCount);
        assertFalse(jdbcTemplate.deletedRolePermissions);
        assertTrue(jdbcTemplate.insertedPermissionKeys.isEmpty());
        verify(permissionSnapshotService, never()).isTrustedActiveUser(anyLong(), anyString());
    }

    @Test
    void updateRolePermissionsShouldRejectBlankUsernameBeforeDatabaseLookup() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));
        CurrentUser currentUser = currentUser();
        currentUser.setUsername(" ");

        BizException exception = assertThrows(
                BizException.class,
                () -> service.updateRolePermissions(currentUser, 2001L, List.of("system:user:view"))
        );

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.rolePermissionLookupCount);
        assertTrue(jdbcTemplate.insertedPermissionKeys.isEmpty());
    }

    @Test
    void updateRolePermissionsShouldRejectInvalidRoleIdBeforeDatabaseLookup() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.updateRolePermissions(currentUser(), 0L, List.of("system:user:view"))
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.rolePermissionLookupCount);
        assertFalse(jdbcTemplate.deletedRolePermissions);
        assertTrue(jdbcTemplate.insertedPermissionKeys.isEmpty());
    }

    @Test
    void updateDefaultRegistrationRoleShouldLogRefreshedLiveUsername() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.rolePermissionKeys = List.of("dashboard:view", "profile:view", "aiadc:registration:view");
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("*")));
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "  admin-live  ", "ENABLED"));
        SystemRoleManagementAppService service = buildService(
                jdbcTemplate,
                permissionSnapshotService,
                event -> {
                },
                systemInternalApi,
                null
        );
        CurrentUser currentUser = currentUser();
        currentUser.setUsername("stale-admin");

        service.updateDefaultRegistrationRole(currentUser, 2001L);

        assertTrue(jdbcTemplate.auditLogged);
        assertEquals("admin-live", currentUser.getUsername());
        assertEquals("admin-live", jdbcTemplate.auditedUsername);
    }

    @Test
    void createRoleShouldRejectNullRequestBeforeMutating() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));

        BizException exception = assertThrows(BizException.class, () -> service.createRole(currentUser(), null));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertFalse(jdbcTemplate.insertedRole);
        assertFalse(jdbcTemplate.deletedRolePermissions);
    }

    @Test
    void createRoleShouldRejectBlankRoleCodeBeforeMutating() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));
        SystemDTO.RoleUpsertRequest request = roleRequest(" ", "Ops", List.of("system:user:view"));

        BizException exception = assertThrows(BizException.class, () -> service.createRole(currentUser(), request));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertFalse(jdbcTemplate.insertedRole);
        assertFalse(jdbcTemplate.deletedRolePermissions);
        assertTrue(jdbcTemplate.insertedDataScopes.isEmpty());
    }

    @Test
    void createRoleShouldRejectInvalidCustomDataScopeIdBeforeMutatingScopes() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));
        SystemDTO.RoleUpsertRequest request = roleRequest("ops", "Ops", List.of("system:user:view"));
        RoleDataScopeRequest scope = dataScope("system:user", "CUSTOM");
        scope.setCustomDeptIds(List.of(0L));
        request.setDataScopes(List.of(scope));

        BizException exception = assertThrows(BizException.class, () -> service.createRole(currentUser(), request));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertFalse(jdbcTemplate.insertedRole);
        assertFalse(jdbcTemplate.deletedRoleDataScopes);
        assertTrue(jdbcTemplate.insertedDataScopes.isEmpty());
    }

    @Test
    void createRoleShouldRejectWhenRoleInsertMissesBeforeReadingGeneratedId() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.roleInsertResult = 0;
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.createRole(currentUser(), roleRequest("ops", "Ops", List.of("system:user:view")))
        );

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Role changed, please retry"));
        assertEquals(0, jdbcTemplate.lastInsertIdQueries);
        assertFalse(jdbcTemplate.deletedRolePermissions);
    }

    @Test
    void createRoleShouldRejectPartialDataScopeBatchInsert() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.roleDataScopeInsertResult = 0;
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));
        SystemDTO.RoleUpsertRequest request = roleRequest("ops", "Ops", List.of("system:user:view"));
        request.setDataScopes(List.of(dataScope("system:user", "SELF")));

        BizException exception = assertThrows(BizException.class, () -> service.createRole(currentUser(), request));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Role data scope changed, please retry"));
    }

    @Test
    void updateRolePermissionsShouldRejectBlankPermissionBeforeDatabaseLookup() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.updateRolePermissions(currentUser(), 2001L, List.of("system:user:view", " "))
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.rolePermissionLookupCount);
        assertFalse(jdbcTemplate.deletedRolePermissions);
        assertTrue(jdbcTemplate.insertedPermissionKeys.isEmpty());
    }

    @Test
    void deleteRoleShouldSoftDeleteRoleBeforeClearingRelationsWithTrustedUuidBoundary() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.roleById = role(2001L, "auditor", "Auditor", "BUSINESS");
        jdbcTemplate.userCountByRoleId.put(2001L, 0);
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));

        assertTrue(service.deleteRole(currentUser(), 2001L));

        int roleDeleteIndex = indexOfSql(jdbcTemplate.updateSql, "update sys_role");
        int permissionDeleteIndex = indexOfSql(jdbcTemplate.updateSql, "update sys_role_permission");
        int dataScopeDeleteIndex = indexOfSql(jdbcTemplate.updateSql, "update sys_role_data_scope");
        assertTrue(roleDeleteIndex >= 0);
        assertTrue(permissionDeleteIndex > roleDeleteIndex);
        assertTrue(dataScopeDeleteIndex > roleDeleteIndex);
        assertTrue(jdbcTemplate.updateSql.get(roleDeleteIndex).contains("role_code = ?"));
        assertTrue(jdbcTemplate.updateSql.get(roleDeleteIndex).contains("role_type = ?"));
        assertTrue(jdbcTemplate.updateSql.get(permissionDeleteIndex).contains("set deleted = 1"));
        assertTrue(jdbcTemplate.updateSql.get(permissionDeleteIndex).contains("exists"));
        assertTrue(jdbcTemplate.updateSql.get(permissionDeleteIndex).contains("updated_by_uuid = ?"));
        assertTrue(jdbcTemplate.updateSql.get(dataScopeDeleteIndex).contains("set deleted = 1"));
        assertTrue(jdbcTemplate.updateSql.get(dataScopeDeleteIndex).contains("exists"));
        assertTrue(jdbcTemplate.updateSql.get(dataScopeDeleteIndex).contains("updated_by_uuid = ?"));
    }

    private int indexOfSql(List<String> sqlList, String fragment) {
        for (int index = 0; index < sqlList.size(); index += 1) {
            if (sqlList.get(index).contains(fragment)) {
                return index;
            }
        }
        return -1;
    }

    private SystemRoleManagementAppService buildService(RecordingJdbcTemplate jdbcTemplate, PermissionSnapshotService permissionSnapshotService) {
        return buildService(jdbcTemplate, permissionSnapshotService, event -> {
        });
    }

    private SystemRoleManagementAppService buildService(
            RecordingJdbcTemplate jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            DomainEventPublisher domainEventPublisher
    ) {
        return buildService(jdbcTemplate, permissionSnapshotService, domainEventPublisher, null, null);
    }

    private SystemRoleManagementAppService buildService(
            RecordingJdbcTemplate jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            DomainEventPublisher domainEventPublisher,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        return buildService(jdbcTemplate, permissionSnapshotService, domainEventPublisher, null, sessionAuthenticationService);
    }

    private SystemRoleManagementAppService buildService(
            RecordingJdbcTemplate jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            DomainEventPublisher domainEventPublisher,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        if (permissionSnapshotService != null && mockingDetails(permissionSnapshotService).getStubbings().isEmpty()) {
            when(permissionSnapshotService.isTrustedActiveUser(anyLong(), anyString())).thenReturn(true);
            when(permissionSnapshotService.loadSnapshot(anyLong(), anyString()))
                    .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-1", Set.of("*")));
            when(permissionSnapshotService.loadRoleSnapshot(anyLong()))
                    .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-1", Set.of("*")));
        }
        if (systemInternalApi == null && sessionAuthenticationService == null) {
            return new SystemRoleManagementAppService(
                    new MyBatisQueryOperations(jdbcTemplate),
                    permissionSnapshotService,
                    new RecordingOperationAuditService(jdbcTemplate),
                    domainEventPublisher
            );
        }
        return new SystemRoleManagementAppService(
                new MyBatisQueryOperations(jdbcTemplate),
                permissionSnapshotService,
                new RecordingOperationAuditService(jdbcTemplate),
                domainEventPublisher,
                systemInternalApi,
                sessionAuthenticationService
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

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("*")));
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "admin-live", "ENABLED"));
        SystemRoleManagementAppService service = buildService(
                jdbcTemplate,
                permissionSnapshotService,
                event -> {
                },
                systemInternalApi,
                null
        );
        CurrentUser currentUser = currentUser();
        currentUser.setSimulatedRoleId(0L);
        Method method = SystemRoleManagementAppService.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(service, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(1001L, "user-uuid-1001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(anyLong(), anyString(), anyLong());
    }

    private CurrentUser currentUser() {
        return userWithPermission("*");
    }

    private CurrentUser userWithPermission(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("admin");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(java.util.Set.of(permission));
        return currentUser;
    }

    private CurrentUser superUser() {
        return currentUser();
    }

    private SystemDTO.RoleUpsertRequest roleRequest(String roleCode, String roleName, List<String> permissionKeys) {
        SystemDTO.RoleUpsertRequest request = new SystemDTO.RoleUpsertRequest();
        request.setRoleCode(roleCode);
        request.setRoleName(roleName);
        request.setRoleType("BUSINESS");
        request.setPermissionKeys(permissionKeys);
        return request;
    }

    private RoleDataScopeRequest dataScope(String resourceCode, String scopeType) {
        RoleDataScopeRequest request = new RoleDataScopeRequest();
        request.setResourceCode(resourceCode);
        request.setScopeType(scopeType);
        return request;
    }

    private static SystemVO.RoleVO role(Long id, String roleCode, String roleName) {
        return role(id, roleCode, roleName, "BUSINESS");
    }

    private static SystemVO.RoleVO role(Long id, String roleCode, String roleName, String roleType) {
        SystemVO.RoleVO role = new SystemVO.RoleVO();
        role.setId(id);
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setRoleType(roleType);
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        return role;
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private boolean insertedRole;
        private boolean deletedRolePermissions;
        private boolean deletedRoleDataScopes;
        private boolean auditLogged;
        private String auditedUsername;
        private int lastInsertIdQueries;
        private int roleListCountQueries;
        private int rolePermissionLookupCount;
        private String upsertedDefaultRoleCode;
        private SystemVO.RoleVO roleById = role(2001L, "commonuser", "Common User");
        private List<String> rolePermissionKeys = List.of("system:user:view", "system:role:view");
        private final Map<Long, Integer> userCountByRoleId = new LinkedHashMap<>();
        private final List<String> insertedPermissionKeys = new ArrayList<>();
        private final List<String> insertedDataScopes = new ArrayList<>();
        private final List<String> updateSql = new ArrayList<>();
        private Integer rolePermissionInsertResult;
        private Integer roleDataScopeInsertResult;
        private Integer roleInsertResult;

        @Override
        public int update(String sql, Object... args) {
            updateSql.add(sql);
            if (sql.contains("insert into sys_role ")) {
                insertedRole = true;
                return roleInsertResult == null ? 1 : roleInsertResult;
            }
            if (sql.contains("delete from sys_role_permission") || sql.contains("update sys_role_permission")) {
                deletedRolePermissions = true;
            }
            if (sql.contains("delete from sys_role_data_scope") || sql.contains("update sys_role_data_scope")) {
                deletedRoleDataScopes = true;
            }
            if (sql.contains("insert into sys_role_permission")) {
                if (sql.contains("from sys_role r")) {
                    for (int index = 0; index < args.length; index += 8) {
                        insertedPermissionKeys.add(String.valueOf(args[index]));
                    }
                    return rolePermissionInsertResult == null ? args.length / 8 : rolePermissionInsertResult;
                } else {
                    for (int index = 0; index < args.length; index += 6) {
                        insertedPermissionKeys.add(String.valueOf(args[index + 1]));
                    }
                    return rolePermissionInsertResult == null ? args.length / 6 : rolePermissionInsertResult;
                }
            }
            if (sql.contains("insert into sys_role_data_scope")) {
                for (int index = 0; index < args.length; index += 9) {
                    insertedDataScopes.add(args[index + 1] + ":" + args[index + 2]);
                }
                return roleDataScopeInsertResult == null ? args.length / 9 : roleDataScopeInsertResult;
            }
            if (sql.contains("insert into sys_config")) {
                upsertedDefaultRoleCode = String.valueOf(args[2]);
            }
            if (sql.contains("update sys_config")) {
                upsertedDefaultRoleCode = String.valueOf(args[1]);
            }
            return 1;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("from sys_role r")) {
                return castList(List.of(role(2001L, "commonuser", "普通用户"), role(2002L, "admin", "管理员")));
            }
            return List.of();
        }

        @Override
        public <T> T query(String sql, ResultSetExtractor<T> rse, Object... args) {
            Map<Long, Integer> result = new LinkedHashMap<>();
            if (sql.contains("from sys_role_permission")) {
                rolePermissionLookupCount += 1;
                result.put(2001L, 3);
                result.put(2002L, 5);
            } else if (sql.contains("from sys_user_role")) {
                result.put(2001L, userCountByRoleId.getOrDefault(2001L, 7));
                result.put(2002L, 1);
            }
            return cast(result);
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("from sys_role r")) {
                if (roleById != null) {
                    return cast(roleById);
                }
                return cast(role(2001L, "commonuser", "普通用户"));
            }
            return null;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("select id from sys_config")) {
                throw new EmptyResultDataAccessException(1);
            }
            if (sql.contains("select id from sys_role")) {
                return requiredType.cast(2001L);
            }
            if (sql.contains("select last_insert_id()")) {
                lastInsertIdQueries += 1;
                return requiredType.cast(2001L);
            }
            if (sql.contains("select count(1) from sys_role")) {
                roleListCountQueries += 1;
                return requiredType.cast(2L);
            }
            if (sql.contains("from sys_role_permission")) {
                rolePermissionLookupCount += 1;
                return requiredType.cast(3L);
            }
            if (sql.contains("from sys_user_role")) {
                Long roleId = args != null && args.length > 0 && args[0] instanceof Number number ? number.longValue() : 2001L;
                return requiredType.cast(Long.valueOf(userCountByRoleId.getOrDefault(roleId, 7)));
            }
            return null;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("from sys_config")) {
                return List.of(Map.of("configKey", "auth.default-registration-role-code", "configValue", "commonuser"));
            }
            return List.of();
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            if (sql.contains("from sys_role_permission") && String.class.equals(elementType)) {
                rolePermissionLookupCount += 1;
                if (rolePermissionKeys != null) {
                    return castList(rolePermissionKeys);
                }
                return castList(List.of("system:user:view", "system:role:view"));
            }
            return List.of();
        }

        @SuppressWarnings("unchecked")
        private <T> T cast(Object value) {
            return (T) value;
        }

        @SuppressWarnings("unchecked")
        private <T> List<T> castList(List<?> values) {
            return (List<T>) values;
        }
    }

    private static final class RecordingOperationAuditService extends OperationAuditService {
        private final RecordingJdbcTemplate jdbcTemplate;

        private RecordingOperationAuditService(RecordingJdbcTemplate jdbcTemplate) {
            super(null, objectProvider(null));
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
            jdbcTemplate.auditLogged = true;
            jdbcTemplate.auditedUsername = username;
        }
    }

    private static <T> ObjectProvider<T> objectProvider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public Iterator<T> iterator() {
                return value == null ? List.<T>of().iterator() : List.of(value).iterator();
            }

            @Override
            public Stream<T> stream() {
                return value == null ? Stream.empty() : Stream.of(value);
            }

            @Override
            public Stream<T> orderedStream() {
                return stream();
            }
        };
    }

    private static final class RecordingDomainEventPublisher implements DomainEventPublisher {
        private final List<DomainEvent> events = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            events.add(event);
        }
    }
}
