package com.lumira.saas.modules.iam.domain.model;

import com.lumira.domain.event.StandardDomainEvent;
import com.lumira.domain.model.AggregateRoot;
import com.lumira.domain.model.EntityId;
import com.lumira.domain.model.VersionedReadModel;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class IamDomainModels {

    private IamDomainModels() {
    }

    public static final class UserAggregate extends AggregateRoot<Long> {
        private final Long tenantId;
        private boolean enabled;

        public UserAggregate(Long userId, Long tenantId, boolean enabled) {
            super(EntityId.of(userId));
            this.tenantId = tenantId;
            this.enabled = enabled;
        }

        public void changeStatus(boolean enabled) {
            if (this.enabled == enabled) {
                return;
            }
            this.enabled = enabled;
            registerEvent(StandardDomainEvent.of(
                    "IAM_USER_STATUS_CHANGED",
                    "iam.user",
                    String.valueOf(id().value()),
                    tenantId,
                    Map.of("enabled", enabled)
            ));
        }
    }

    public static final class RoleAggregate extends AggregateRoot<Long> {
        private final Long tenantId;
        private final Set<String> permissionCodes;

        public RoleAggregate(Long roleId, Long tenantId, Set<String> permissionCodes) {
            super(EntityId.of(roleId));
            this.tenantId = tenantId;
            this.permissionCodes = new LinkedHashSet<>(permissionCodes == null ? Set.of() : permissionCodes);
        }

        public void replacePermissions(Set<String> newPermissionCodes) {
            Set<String> normalized = new LinkedHashSet<>(newPermissionCodes == null ? Set.of() : newPermissionCodes);
            if (permissionCodes.equals(normalized)) {
                return;
            }
            permissionCodes.clear();
            permissionCodes.addAll(normalized);
            registerEvent(StandardDomainEvent.of(
                    "IAM_ROLE_PERMISSIONS_CHANGED",
                    "iam.role",
                    String.valueOf(id().value()),
                    tenantId,
                    Map.of("permissionCount", permissionCodes.size())
            ));
        }
    }

    public record PermissionSnapshotReadModel(
            Long tenantId,
            Long userId,
            long version,
            Set<String> permissions
    ) implements VersionedReadModel {

        @Override
        public String cacheScope() {
            return "iam.permission-snapshot:" + userId;
        }

        public boolean hasPermission(String permission) {
            return permissions != null && permissions.contains(permission);
        }
    }
}
