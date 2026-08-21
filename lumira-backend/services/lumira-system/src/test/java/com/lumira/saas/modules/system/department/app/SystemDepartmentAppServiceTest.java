package com.lumira.saas.modules.system.department.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.department.dto.DepartmentUpsertRequest;
import com.lumira.saas.modules.system.department.vo.DepartmentVO;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemDepartmentAppServiceTest {

    @Test
    void departmentWritesShouldPersistTrustedUserUuid() throws Exception {
        String appSource = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/department/app/SystemDepartmentAppService.java"));
        String adapterSource = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/department/infrastructure/JdbcSystemDepartmentRepository.java"));

        assertThat(appSource).contains("AuthenticationTrustSupport.isTrustedCurrentUser");
        assertThat(appSource).contains("rebuildClosureForSubtree(id)");
        assertThat(appSource).contains("existing.getDeptCode()");
        assertThat(appSource).contains("existing.getStatus()");
        assertThat(appSource).doesNotContain("MyBatisQueryOperations");
        assertThat(appSource).doesNotContain("jdbcTemplate");
        assertThat(appSource).doesNotContain("insert into sys_department");
        assertThat(adapterSource).contains("created_by, created_by_uuid, updated_by, updated_by_uuid");
        assertThat(adapterSource).contains("updated_by_uuid = ?");
        assertThat(adapterSource).contains("join sys_user u");
        assertThat(adapterSource).contains("u.uuid = ud.user_uuid");
        assertThat(adapterSource).contains("trim(ud.user_uuid) <> ''");
        assertThat(adapterSource).contains("and dept_code = ?");
        assertThat(adapterSource).contains("and status = ?");
        assertThat(adapterSource).contains("update sys_department_closure set deleted = 1 where descendant_id in");
        assertThat(adapterSource).contains("update sys_department_closure");
        assertThat(adapterSource).doesNotContain("delete from sys_department_closure");
        assertThat(adapterSource).doesNotContain("on duplicate key update deleted = 0");
        assertThat(adapterSource).contains("exists (select 1 from sys_department a where a.id = values(ancestor_id) and a.deleted = 0)");
        assertThat(adapterSource).contains("exists (select 1 from sys_department d where d.id = values(descendant_id) and d.deleted = 0)");
        assertThat(appSource).contains("Department changed, please retry");
    }

    @Test
    void deleteDepartmentShouldUseExistsChecksAndInvalidateSnapshot() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = permissionSnapshotService("*");
        SystemDepartmentAppService service = new SystemDepartmentAppService(
                queryOperations,
                permissionSnapshotService,
                new OperationAuditService(null, objectProvider(null)) {
                    @Override
                    public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
                    }
                }
        );

        boolean deleted = service.deleteDepartment(currentUser(), 2001L);

        assertThat(deleted).isTrue();
        assertThat(queryOperations.existsCallCount).isEqualTo(2);
        assertThat(queryOperations.countQueryCalled).isFalse();
        assertThat(queryOperations.updateCalled).isTrue();
        verify(permissionSnapshotService).invalidatePermissions();
    }

    @Test
    void createDepartmentShouldRejectDuplicateCodeViaExistsCheck() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.departmentCodeExists = true;
        SystemDepartmentAppService service = new SystemDepartmentAppService(
                queryOperations,
                permissionSnapshotService("*"),
                new OperationAuditService(null, objectProvider(null)) {
                    @Override
                    public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
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

    @Test
    void listDepartmentsShouldRequireViewPermissionBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemDepartmentAppService service = new SystemDepartmentAppService(
                queryOperations,
                permissionSnapshotService("system:department:create"),
                new OperationAuditService(null, objectProvider(null)) {
                    @Override
                    public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
                    }
                }
        );

        assertThatThrownBy(() -> service.listDepartments(currentUser("system:department:create")))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void getDepartmentShouldRejectWhenLiveSnapshotRevokesViewPermissionBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:department:update")));
        SystemDepartmentAppService service = new SystemDepartmentAppService(
                queryOperations,
                permissionSnapshotService,
                new OperationAuditService(null, objectProvider(null)) {
                    @Override
                    public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
                    }
                }
        );

        assertThatThrownBy(() -> service.getDepartment(currentUser("system:department:view"), 2001L))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void createDepartmentShouldRejectWhenInsertMisses() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.updateResult = 0;
        SystemDepartmentAppService service = new SystemDepartmentAppService(
                queryOperations,
                permissionSnapshotService("*"),
                new OperationAuditService(null, objectProvider(null)) {
                    @Override
                    public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
                    }
                }
        );

        DepartmentUpsertRequest request = new DepartmentUpsertRequest();
        request.setDeptCode("RD");
        request.setDeptName("RD");
        request.setStatus("ENABLED");

        assertThatThrownBy(() -> service.createDepartment(currentUser(), request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Department changed, please retry");
                });
        assertThat(queryOperations.updateCalled).isTrue();
        assertThat(queryOperations.countQueryCalled).isFalse();
    }

    @Test
    void createDepartmentShouldRequireCreatePermissionBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemDepartmentAppService service = new SystemDepartmentAppService(
                queryOperations,
                permissionSnapshotService("system:department:view"),
                new OperationAuditService(null, objectProvider(null)) {
                    @Override
                    public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
                    }
                }
        );

        DepartmentUpsertRequest request = new DepartmentUpsertRequest();
        request.setDeptCode("RD");
        request.setDeptName("研发部");
        request.setStatus("ENABLED");

        assertThatThrownBy(() -> service.createDepartment(currentUser("system:department:view"), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
        assertThat(queryOperations.updateCalled).isFalse();
    }

    @Test
    void createDepartmentShouldRejectWhenLiveSnapshotRevokesCreatePermissionBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:department:view")));
        SystemDepartmentAppService service = new SystemDepartmentAppService(
                queryOperations,
                permissionSnapshotService,
                new OperationAuditService(null, objectProvider(null)) {
                    @Override
                    public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
                    }
                }
        );

        DepartmentUpsertRequest request = new DepartmentUpsertRequest();
        request.setDeptCode("RD");
        request.setDeptName("RD");
        request.setStatus("ENABLED");

        assertThatThrownBy(() -> service.createDepartment(currentUser("system:department:create"), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
        assertThat(queryOperations.updateCalled).isFalse();
    }

    @Test
    void createDepartmentShouldRejectRevokedSessionTicketBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 2001L, "user-uuid-2001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is required"));
        SystemDepartmentAppService service = new SystemDepartmentAppService(
                queryOperations,
                mock(PermissionSnapshotService.class),
                new OperationAuditService(null, objectProvider(null)) {
                    @Override
                    public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
                    }
                },
                sessionAuthenticationService
        );

        DepartmentUpsertRequest request = new DepartmentUpsertRequest();
        request.setDeptCode("RD");
        request.setDeptName("RD");
        request.setStatus("ENABLED");

        assertThatThrownBy(() -> service.createDepartment(currentUser("system:department:create"), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
        assertThat(queryOperations.updateCalled).isFalse();
    }

    @Test
    void createDepartmentShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemDepartmentAppService service = new SystemDepartmentAppService(
                queryOperations,
                null,
                new RecordingOperationAuditService(),
                null,
                null
        );

        DepartmentUpsertRequest request = new DepartmentUpsertRequest();
        request.setDeptCode("RD");
        request.setDeptName("RD");
        request.setStatus("ENABLED");

        assertThatThrownBy(() -> service.createDepartment(currentUser("system:department:create"), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
        assertThat(queryOperations.updateCalled).isFalse();
    }

    @Test
    void createDepartmentShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001")).thenReturn(null);
        SystemDepartmentAppService service = new SystemDepartmentAppService(
                queryOperations,
                permissionSnapshotService,
                new RecordingOperationAuditService(),
                null,
                null
        );

        DepartmentUpsertRequest request = new DepartmentUpsertRequest();
        request.setDeptCode("RD");
        request.setDeptName("RD");
        request.setStatus("ENABLED");

        assertThatThrownBy(() -> service.createDepartment(currentUser("system:department:create"), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                });
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
        assertThat(queryOperations.updateCalled).isFalse();
    }

    @Test
    void createDepartmentShouldRejectDisabledTrustedUserIdentityBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "admin-live", "DISABLED"));
        SystemDepartmentAppService service = newService(
                queryOperations,
                permissionSnapshotService,
                null,
                systemInternalApi,
                new RecordingOperationAuditService()
        );

        DepartmentUpsertRequest request = new DepartmentUpsertRequest();
        request.setDeptCode("RD");
        request.setDeptName("RD");
        request.setStatus("ENABLED");

        assertThatThrownBy(() -> service.createDepartment(currentUser("system:department:create"), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
        assertThat(queryOperations.updateCalled).isFalse();
    }

    @Test
    void createDepartmentShouldRejectBlankLiveUsernameBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "  ", "ENABLED"));
        SystemDepartmentAppService service = newService(
                queryOperations,
                permissionSnapshotService,
                null,
                systemInternalApi,
                new RecordingOperationAuditService()
        );

        DepartmentUpsertRequest request = new DepartmentUpsertRequest();
        request.setDeptCode("RD");
        request.setDeptName("RD");
        request.setStatus("ENABLED");

        assertThatThrownBy(() -> service.createDepartment(currentUser("system:department:create"), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user username is unavailable");
                });
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
        assertThat(queryOperations.updateCalled).isFalse();
    }

    @Test
    void createDepartmentShouldRejectBlankUsernameBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemDepartmentAppService service = new SystemDepartmentAppService(
                queryOperations,
                mock(PermissionSnapshotService.class),
                new OperationAuditService(null, objectProvider(null)) {
                    @Override
                    public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
                    }
                }
        );

        DepartmentUpsertRequest request = new DepartmentUpsertRequest();
        request.setDeptCode("RD");
        request.setDeptName("研发部");
        request.setStatus("ENABLED");
        CurrentUser currentUser = currentUser();
        currentUser.setUsername(" ");

        assertThatThrownBy(() -> service.createDepartment(currentUser, request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
        assertThat(queryOperations.updateCalled).isFalse();
    }

    @Test
    void createDepartmentShouldRejectMissingSessionVersionBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemDepartmentAppService service = new SystemDepartmentAppService(
                queryOperations,
                mock(PermissionSnapshotService.class),
                new OperationAuditService(null, objectProvider(null)) {
                    @Override
                    public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
                    }
                }
        );

        DepartmentUpsertRequest request = new DepartmentUpsertRequest();
        request.setDeptCode("RD");
        request.setDeptName("研发部");
        request.setStatus("ENABLED");
        CurrentUser currentUser = currentUser();
        currentUser.setSessionVersion(null);

        assertThatThrownBy(() -> service.createDepartment(currentUser, request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
        assertThat(queryOperations.updateCalled).isFalse();
    }

    @Test
    void createDepartmentShouldLogRefreshedLiveUsername() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "  admin-live  ", "ENABLED"));
        RecordingOperationAuditService auditService = new RecordingOperationAuditService();
        SystemDepartmentAppService service = newService(
                queryOperations,
                permissionSnapshotService("system:department:create"),
                null,
                systemInternalApi,
                auditService
        );
        CurrentUser currentUser = currentUser("system:department:create");
        currentUser.setUsername("admin-stale");

        DepartmentUpsertRequest request = new DepartmentUpsertRequest();
        request.setDeptCode("RD");
        request.setDeptName("RD");
        request.setStatus("ENABLED");

        DepartmentVO department = service.createDepartment(currentUser, request);

        assertThat(department.getId()).isEqualTo(2001L);
        assertThat(currentUser.getUsername()).isEqualTo("admin-live");
        assertThat(auditService.username).isEqualTo("admin-live");
    }

    @Test
    void createDepartmentShouldRejectNullRequestBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemDepartmentAppService service = new SystemDepartmentAppService(
                queryOperations,
                permissionSnapshotService("*"),
                new OperationAuditService(null, objectProvider(null)) {
                    @Override
                    public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
                    }
                }
        );

        assertThatThrownBy(() -> service.createDepartment(currentUser(), null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
        assertThat(queryOperations.updateCalled).isFalse();
    }

    @Test
    void deleteDepartmentShouldRejectInvalidIdBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemDepartmentAppService service = new SystemDepartmentAppService(
                queryOperations,
                permissionSnapshotService("*"),
                new OperationAuditService(null, objectProvider(null)) {
                    @Override
                    public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
                    }
                }
        );

        assertThatThrownBy(() -> service.deleteDepartment(currentUser(), 0L))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
        assertThat(queryOperations.updateCalled).isFalse();
    }

    private CurrentUser currentUser() {
        return currentUser("*");
    }

    private CurrentUser currentUser(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(2001L);
        currentUser.setUserUuid("user-uuid-2001");
        currentUser.setUsername("admin");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of(permission));
        return currentUser;
    }

    private PermissionSnapshotService permissionSnapshotService(String... permissions) {
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        Set<String> permissionSet = Set.of(permissions);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", permissionSet));
        return permissionSnapshotService;
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = permissionSnapshotService("system:department:create");
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "admin-live", "ENABLED"));
        SystemDepartmentAppService service = newService(
                queryOperations,
                permissionSnapshotService,
                null,
                systemInternalApi,
                new RecordingOperationAuditService()
        );
        CurrentUser currentUser = currentUser("system:department:create");
        currentUser.setSimulatedRoleId(0L);
        Method method = SystemDepartmentAppService.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(service, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(2001L, "user-uuid-2001");
        org.mockito.Mockito.verify(permissionSnapshotService, org.mockito.Mockito.never())
                .loadGrantedRoleSnapshot(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    private SystemDepartmentAppService newService(
            RecordingQueryOperations queryOperations,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService,
            SystemInternalApi systemInternalApi,
            RecordingOperationAuditService auditService
    ) {
        return new SystemDepartmentAppService(
                queryOperations,
                permissionSnapshotService,
                auditService,
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

    private static DepartmentVO department(Long id, Long parentId, String deptCode, String deptName) {
        DepartmentVO department = new DepartmentVO();
        department.setId(id);
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

    private static final class RecordingOperationAuditService extends OperationAuditService {
        private String username;

        private RecordingOperationAuditService() {
            super(null, objectProvider(null));
        }

        @Override
        public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
            this.username = username;
        }
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private boolean departmentCodeExists;
        private boolean countQueryCalled;
        private boolean updateCalled;
        private int existsCallCount;
        private int queryCallCount;
        private int updateResult = 1;

        @Override
        public boolean exists(String sql, Object... args) {
            existsCallCount += 1;
            return departmentCodeExists && sql.contains("dept_code = ?");
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCallCount += 1;
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
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            if (elementType == Long.class && sql.contains("with recursive dept_tree")) {
                return castList(List.of(2001L));
            }
            return List.of();
        }

        @Override
        public int update(String sql, Object... args) {
            updateCalled = true;
            return updateResult;
        }

        @SuppressWarnings("unchecked")
        private <T> List<T> castList(List<?> items) {
            return (List<T>) new ArrayList<>(items);
        }
    }
}
