package com.lumira.saas.modules.iam.domain.model;

import com.lumira.domain.event.StandardDomainEvent;
import com.lumira.domain.model.AggregateRoot;
import com.lumira.domain.model.EntityId;
import com.lumira.domain.model.VersionedReadModel;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class IamDomainModels {

    private IamDomainModels() {
    }

    public static final class UserAggregate extends AggregateRoot<Long> {
        private boolean enabled;

        public UserAggregate(Long userId, boolean enabled) {
            super(EntityId.of(userId));
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
                    Map.of("enabled", enabled)
            ));
        }
    }

    public static final class RoleAggregate extends AggregateRoot<Long> {
        private final Set<String> permissionCodes;

        public RoleAggregate(Long roleId, Set<String> permissionCodes) {
            super(EntityId.of(roleId));
            this.permissionCodes = new LinkedHashSet<>(permissionCodes == null ? Set.of() : permissionCodes);
        }

        public void replacePermissions(Set<String> newPermissionCodes) {
            replacePermissions(newPermissionCodes, null, null);
        }

        public void replacePermissions(Set<String> newPermissionCodes, Long userId, String userUuid) {
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
                    actorAttributes(Map.of("permissionCount", permissionCodes.size()), userId, userUuid)
            ));
        }

        public void recordRoleChanged(String changeType, String roleCode, Long userId, String userUuid) {
            if (changeType == null || changeType.isBlank()) {
                throw new IllegalArgumentException("role change type is required");
            }
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("changeType", changeType.trim());
            if (roleCode != null && !roleCode.isBlank()) {
                attributes.put("roleCode", roleCode.trim());
            }
            registerEvent(StandardDomainEvent.of(
                    "IAM_ROLE_CHANGED",
                    "iam.role",
                    String.valueOf(id().value()),
                    actorAttributes(attributes, userId, userUuid)
            ));
        }

        private Map<String, Object> actorAttributes(Map<String, Object> baseAttributes, Long userId, String userUuid) {
            Map<String, Object> attributes = new LinkedHashMap<>(baseAttributes);
            if (userId != null) {
                if (userId <= 0 || userUuid == null || userUuid.isBlank()) {
                    throw new IllegalArgumentException("trusted actor identity is required");
                }
                attributes.put("userId", userId);
                attributes.put("userUuid", userUuid.trim());
            }
            return attributes;
        }
    }

    public record PermissionSnapshotReadModel(
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
