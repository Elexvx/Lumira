package com.lumira.saas.modules.iam.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lumira.domain.event.DomainEvent;
import com.lumira.saas.modules.iam.domain.model.IamDomainModels.PermissionSnapshotReadModel;
import com.lumira.saas.modules.iam.domain.model.IamDomainModels.RoleAggregate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IamDomainModelsTest {

    @Test
    void roleAggregateEmitsEventOnlyWhenPermissionsChange() {
        RoleAggregate role = new RoleAggregate(10L, Set.of("system:user:view"));

        role.replacePermissions(Set.of("system:user:view"));
        assertThat(role.domainEvents()).isEmpty();

        role.replacePermissions(Set.of("system:user:view", "system:user:write"));
        assertThat(role.domainEvents()).hasSize(1);
        DomainEvent event = role.domainEvents().getFirst();
        assertThat(event.eventType()).isEqualTo("IAM_ROLE_PERMISSIONS_CHANGED");
        assertThat(event.eventKey()).isEqualTo("IAM_ROLE_PERMISSIONS_CHANGED:iam.role:10");
        assertThat(event.attributes()).containsEntry("permissionCount", 2);
    }

    @Test
    void roleAggregateShouldCarryTrustedActorWhenPresent() {
        RoleAggregate role = new RoleAggregate(10L, Set.of("system:user:view"));

        role.replacePermissions(Set.of("system:user:view", "system:user:write"), 1001L, " user-uuid-1001 ");

        assertThat(role.domainEvents()).hasSize(1);
        assertThat(role.domainEvents().getFirst().attributes())
                .containsEntry("permissionCount", 2)
                .containsEntry("userId", 1001L)
                .containsEntry("userUuid", "user-uuid-1001");
    }

    @Test
    void roleAggregateShouldRejectActorUserIdWithoutUserUuid() {
        RoleAggregate role = new RoleAggregate(10L, Set.of("system:user:view"));

        assertThatThrownBy(() -> role.replacePermissions(Set.of("system:user:write"), 1001L, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(role.domainEvents()).isEmpty();
    }

    @Test
    void permissionSnapshotReadModelUsesGlobalVersionScopeCacheKey() {
        PermissionSnapshotReadModel snapshot = new PermissionSnapshotReadModel(
                20L,
                7L,
                Set.of("message:message:view")
        );

        assertThat(snapshot.cacheKey()).isEqualTo("7:iam.permission-snapshot:20");
        assertThat(snapshot.hasPermission("message:message:view")).isTrue();
        assertThat(snapshot.hasPermission("message:message:write")).isFalse();
    }
}
