package com.lumira.saas.modules.iam.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IamTenantCommandServiceTest {

    @Test
    void changeTenantStatus_shouldRejectDisablingPlatformTenant() {
        FakeQueryOperations queryOperations = new FakeQueryOperations();
        queryOperations.tenantExists = true;
        IamTenantCommandService service = service(queryOperations, mock(IamTenantQueryService.class), mock(PermissionSnapshotService.class));
        IamTenantCommandService.TenantStatusRequest request = new IamTenantCommandService.TenantStatusRequest();
        request.setStatus("DISABLED");

        assertThatThrownBy(() -> service.changeTenantStatus(operator(), 1001L, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("平台默认租户不允许停用");

        assertThat(queryOperations.updates).isEmpty();
    }

    @Test
    void archiveTenant_shouldRejectPlatformTenant() {
        FakeQueryOperations queryOperations = new FakeQueryOperations();
        queryOperations.tenantExists = true;
        IamTenantCommandService service = service(queryOperations, mock(IamTenantQueryService.class), mock(PermissionSnapshotService.class));

        assertThatThrownBy(() -> service.archiveTenant(operator(), 1001L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("平台默认租户不允许归档");

        assertThat(queryOperations.updates).isEmpty();
    }

    @Test
    void upsertTenantMember_shouldResetDefaultMembershipInvalidateSnapshotAndUseCopiedCurrentUser() {
        FakeQueryOperations queryOperations = new FakeQueryOperations();
        queryOperations.tenantExists = true;
        queryOperations.userExists = true;
        IamTenantQueryService tenantQueryService = mock(IamTenantQueryService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(tenantQueryService.currentTenant(any())).thenReturn(tenantSnapshot(2001L));
        IamTenantCommandService service = service(queryOperations, tenantQueryService, permissionSnapshotService);
        CurrentUser currentUser = operator();
        currentUser.setCurrentTenantId(1001L);
        IamTenantCommandService.TenantMemberRequest request = new IamTenantCommandService.TenantMemberRequest();
        request.setStatus("enabled");
        request.setDefaultTenant(true);

        var snapshot = service.upsertTenantMember(currentUser, 2001L, 3001L, request);

        assertThat(snapshot.getId()).isEqualTo(2001L);
        assertThat(currentUser.getCurrentTenantId()).isEqualTo(1001L);
        assertThat(queryOperations.updates).hasSize(2);
        assertThat(queryOperations.updates.get(0).sql()).contains("set is_default = 0");
        assertThat(queryOperations.updates.get(0).args()).contains(1001L, 3001L);
        assertThat(queryOperations.updates.get(1).sql()).contains("insert into sys_user_tenant");
        assertThat(queryOperations.updates.get(1).args()).contains(2001L, 3001L, 1, "ENABLED");
        verify(permissionSnapshotService).invalidateTenant(2001L);

        ArgumentCaptor<CurrentUser> currentUserCaptor = ArgumentCaptor.forClass(CurrentUser.class);
        verify(tenantQueryService).currentTenant(currentUserCaptor.capture());
        assertThat(currentUserCaptor.getValue()).isNotSameAs(currentUser);
        assertThat(currentUserCaptor.getValue().getCurrentTenantId()).isEqualTo(2001L);
        assertThat(currentUserCaptor.getValue().getUserId()).isEqualTo(1001L);
        assertThat(queryOperations.countQueryCalled).isFalse();
    }

    @Test
    void updateTenant_shouldInvalidateSnapshotAndKeepCallerTenantContext() {
        FakeQueryOperations queryOperations = new FakeQueryOperations();
        queryOperations.tenantExists = true;
        IamTenantQueryService tenantQueryService = mock(IamTenantQueryService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(tenantQueryService.currentTenant(any())).thenReturn(tenantSnapshot(2001L));
        IamTenantCommandService service = service(queryOperations, tenantQueryService, permissionSnapshotService);
        CurrentUser currentUser = operator();
        currentUser.setCurrentTenantId(1001L);
        IamTenantCommandService.TenantUpsertRequest request = new IamTenantCommandService.TenantUpsertRequest();
        request.setTenantCode(" tenant-a ");
        request.setTenantName(" Tenant A ");
        request.setStatus("enabled");

        service.updateTenant(currentUser, 2001L, request);

        assertThat(currentUser.getCurrentTenantId()).isEqualTo(1001L);
        assertThat(queryOperations.updates).hasSize(1);
        assertThat(queryOperations.updates.get(0).args()).contains("tenant-a", "Tenant A", "ENABLED", 2001L);
        verify(permissionSnapshotService).invalidateTenant(2001L);
    }

    private IamTenantCommandService service(
            MyBatisQueryOperations queryOperations,
            IamTenantQueryService tenantQueryService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        OperationAuditService auditService = mock(OperationAuditService.class);
        return new IamTenantCommandService(queryOperations, tenantQueryService, permissionSnapshotService, auditService);
    }

    private CurrentUser operator() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUsername("admin");
        currentUser.setCurrentTenantId(1001L);
        currentUser.setAuthenticated(true);
        return currentUser;
    }

    private IamTenantQueryService.TenantSnapshot tenantSnapshot(Long tenantId) {
        IamTenantQueryService.TenantSnapshot snapshot = new IamTenantQueryService.TenantSnapshot();
        snapshot.setId(tenantId);
        snapshot.setTenantCode("tenant-a");
        snapshot.setTenantName("Tenant A");
        snapshot.setTenantStatus("ENABLED");
        snapshot.setMembershipStatus("ENABLED");
        return snapshot;
    }

    private static class FakeQueryOperations extends MyBatisQueryOperations {
        private boolean tenantExists;
        private boolean userExists;
        private boolean countQueryCalled;
        private final List<UpdateCall> updates = new ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            updates.add(new UpdateCall(sql, Arrays.asList(args)));
            return 1;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("count(1)")) {
                countQueryCalled = true;
            }
            Long value;
            if (sql.contains("last_insert_id")) {
                value = 2001L;
            } else {
                value = 0L;
            }
            @SuppressWarnings("unchecked")
            T result = (T) value;
            return result;
        }

        @Override
        public boolean exists(String sql, Object... args) {
            if (sql.contains("from sys_tenant")) {
                return tenantExists;
            }
            if (sql.contains("from sys_user")) {
                return userExists;
            }
            return false;
        }
    }

    private record UpdateCall(String sql, List<Object> args) {
    }
}
