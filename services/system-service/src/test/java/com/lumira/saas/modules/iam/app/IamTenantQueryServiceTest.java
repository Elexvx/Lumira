package com.lumira.saas.modules.iam.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IamTenantQueryServiceTest {

    @Test
    void listCurrentUserTenants_shouldReturnEmptyWhenUserIsAnonymous() {
        FakeQueryOperations queryOperations = new FakeQueryOperations();
        IamTenantQueryService service = new IamTenantQueryService(queryOperations);

        assertThat(service.listCurrentUserTenants(null)).isEmpty();
        assertThat(queryOperations.queryArgs).isEmpty();
    }

    @Test
    void currentTenant_shouldUseCurrentTenantFromSession() {
        FakeQueryOperations queryOperations = new FakeQueryOperations();
        queryOperations.objectResult = tenant(2001L, "tenant-a", "Tenant A");
        IamTenantQueryService service = new IamTenantQueryService(queryOperations);
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setCurrentTenantId(2001L);

        var snapshot = service.currentTenant(currentUser);

        assertThat(snapshot.getId()).isEqualTo(2001L);
        assertThat(snapshot.getTenantCode()).isEqualTo("tenant-a");
        assertThat(queryOperations.objectArgs).containsExactly(1001L, 2001L);
    }

    @Test
    void listCurrentUserTenants_shouldQueryMembershipSnapshots() {
        FakeQueryOperations queryOperations = new FakeQueryOperations();
        queryOperations.listResult = List.of(tenant(1001L, "platform", "平台默认租户"));
        IamTenantQueryService service = new IamTenantQueryService(queryOperations);
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);

        var snapshots = service.listCurrentUserTenants(currentUser);

        assertThat(snapshots)
                .extracting(IamTenantQueryService.TenantSnapshot::getTenantCode)
                .containsExactly("platform");
        assertThat(queryOperations.queryArgs).containsExactly(1001L);
    }

    private static IamTenantQueryService.TenantSnapshot tenant(Long id, String code, String name) {
        IamTenantQueryService.TenantSnapshot snapshot = new IamTenantQueryService.TenantSnapshot();
        snapshot.setId(id);
        snapshot.setTenantCode(code);
        snapshot.setTenantName(name);
        snapshot.setTenantStatus("ENABLED");
        snapshot.setMembershipStatus("ENABLED");
        snapshot.setDefaultTenant(true);
        snapshot.setCreatedAt(LocalDateTime.now());
        snapshot.setUpdatedAt(LocalDateTime.now());
        return snapshot;
    }

    private static class FakeQueryOperations extends MyBatisQueryOperations {
        private List<IamTenantQueryService.TenantSnapshot> listResult = List.of();
        private IamTenantQueryService.TenantSnapshot objectResult;
        private final List<Object> queryArgs = new ArrayList<>();
        private final List<Object> objectArgs = new ArrayList<>();

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryArgs.addAll(List.of(args));
            @SuppressWarnings("unchecked")
            List<T> result = (List<T>) listResult;
            return result;
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            objectArgs.addAll(List.of(args));
            @SuppressWarnings("unchecked")
            T result = (T) objectResult;
            return result;
        }
    }
}
