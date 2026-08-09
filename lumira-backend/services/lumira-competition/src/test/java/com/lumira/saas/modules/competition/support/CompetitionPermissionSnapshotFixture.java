package com.lumira.saas.modules.competition.support;

import com.lumira.common.security.data.DataPermissionRule;
import java.util.List;
import java.util.Set;

/** Test double seam for the System-owned permission snapshot capability. */
public class CompetitionPermissionSnapshotFixture {

    public boolean isTrustedActiveUser(Long userId, String userUuid) {
        return false;
    }

    public PermissionSnapshot loadSnapshot(Long userId, String userUuid) {
        return null;
    }

    public PermissionSnapshot loadGrantedRoleSnapshot(Long userId, String userUuid, Long roleId) {
        return null;
    }

    public static class PermissionSnapshot {
        private String version;
        private Set<String> permissions;
        private Set<Long> roleIds;
        private Long primaryDeptId;
        private Set<Long> deptIds;
        private Set<Long> descendantDeptIds;
        private List<DataPermissionRule> dataScopes;
        private String defaultHomePath;

        public PermissionSnapshot() {
        }

        public PermissionSnapshot(String version, Set<String> permissions) {
            this(version, permissions, Set.of(), null, Set.of(), Set.of(), List.of(), "/dashboard/home");
        }

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

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public Set<String> getPermissions() {
            return permissions == null ? Set.of() : permissions;
        }

        public void setPermissions(Set<String> permissions) {
            this.permissions = permissions;
        }

        public Set<Long> getRoleIds() {
            return roleIds == null ? Set.of() : roleIds;
        }

        public void setRoleIds(Set<Long> roleIds) {
            this.roleIds = roleIds;
        }

        public Long getPrimaryDeptId() {
            return primaryDeptId;
        }

        public void setPrimaryDeptId(Long primaryDeptId) {
            this.primaryDeptId = primaryDeptId;
        }

        public Set<Long> getDeptIds() {
            return deptIds == null ? Set.of() : deptIds;
        }

        public void setDeptIds(Set<Long> deptIds) {
            this.deptIds = deptIds;
        }

        public Set<Long> getDescendantDeptIds() {
            return descendantDeptIds == null ? Set.of() : descendantDeptIds;
        }

        public void setDescendantDeptIds(Set<Long> descendantDeptIds) {
            this.descendantDeptIds = descendantDeptIds;
        }

        public List<DataPermissionRule> getDataScopes() {
            return dataScopes == null ? List.of() : dataScopes;
        }

        public void setDataScopes(List<DataPermissionRule> dataScopes) {
            this.dataScopes = dataScopes;
        }

        public String getDefaultHomePath() {
            return defaultHomePath;
        }

        public void setDefaultHomePath(String defaultHomePath) {
            this.defaultHomePath = defaultHomePath;
        }
    }
}
