package com.lumira.saas.modules.ai.integration;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.security.data.DataPermissionRule;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * AI-owned adapter for the System identity and permission contract.
 *
 * <p>It deliberately exposes only the snapshot operations AI needs and never
 * links this bounded context to the System/IAM implementation classes.</p>
 */
@Component
public class AiPermissionSnapshotResolver {

    private static final String STATUS_ENABLED = "ENABLED";

    private final SystemInternalApi systemInternalApi;

    public AiPermissionSnapshotResolver(SystemInternalApi systemInternalApi) {
        this.systemInternalApi = systemInternalApi;
    }

    public boolean isTrustedActiveUser(Long userId, String userUuid) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(userUuid)) {
            return false;
        }
        SystemUserSnapshotDTO user = systemInternalApi.findUserIdentityById(userId);
        return user != null
                && user.userId() != null
                && userId.equals(user.userId())
                && StringUtils.hasText(user.userUuid())
                && userUuid.trim().equals(user.userUuid().trim())
                && STATUS_ENABLED.equalsIgnoreCase(user.status());
    }

    public PermissionSnapshot loadSnapshot(Long userId, String userUuid) {
        return from(systemInternalApi.permissionSnapshot(userId, userUuid));
    }

    public PermissionSnapshot loadGrantedRoleSnapshot(Long userId, String userUuid, Long roleId) {
        return from(systemInternalApi.simulatedRolePermissionSnapshot(userId, userUuid, roleId));
    }

    private PermissionSnapshot from(PermissionSnapshotDTO source) {
        if (source == null) {
            return null;
        }
        return new PermissionSnapshot(
                source.version(),
                source.permissions() == null ? Set.of() : Set.copyOf(source.permissions()),
                source.roleIds() == null ? Set.of() : Set.copyOf(source.roleIds()),
                source.primaryDeptId(),
                source.deptIds() == null ? Set.of() : Set.copyOf(source.deptIds()),
                source.descendantDeptIds() == null ? Set.of() : Set.copyOf(source.descendantDeptIds()),
                source.dataScopes() == null ? List.of() : List.copyOf(source.dataScopes()),
                source.defaultHomePath()
        );
    }

    public static final class PermissionSnapshot {
        private final String version;
        private final Set<String> permissions;
        private final Set<Long> roleIds;
        private final Long primaryDeptId;
        private final Set<Long> deptIds;
        private final Set<Long> descendantDeptIds;
        private final List<DataPermissionRule> dataScopes;
        private final String defaultHomePath;

        public PermissionSnapshot(
            String version,
                Set<String> permissions,
                Set<Long> roleIds,
                Long primaryDeptId,
                Set<Long> deptIds,
                Set<Long> descendantDeptIds,
                List<DataPermissionRule> dataScopes,
                String defaultHomePath
        ) {
            this.version = version;
            this.permissions = permissions;
            this.roleIds = roleIds;
            this.primaryDeptId = primaryDeptId;
            this.deptIds = deptIds;
            this.descendantDeptIds = descendantDeptIds;
            this.dataScopes = dataScopes;
            this.defaultHomePath = defaultHomePath;
        }

        /** Lightweight compatibility constructor for callers that only need a permission version and grants. */
        public PermissionSnapshot(String version, Set<String> permissions) {
            this(version, permissions, Set.of(), null, Set.of(), Set.of(), List.of(), null);
        }

        public String getVersion() { return version; }
        public Set<String> getPermissions() { return permissions; }
        public Set<Long> getRoleIds() { return roleIds; }
        public Long getPrimaryDeptId() { return primaryDeptId; }
        public Set<Long> getDeptIds() { return deptIds; }
        public Set<Long> getDescendantDeptIds() { return descendantDeptIds; }
        public List<DataPermissionRule> getDataScopes() { return dataScopes; }
        public String getDefaultHomePath() { return defaultHomePath; }
    }
}
