package com.lumira.common.security.data;

import java.util.LinkedHashSet;
import java.util.Set;

public class DataPermissionDecision {
    private final DataScopeType scopeType;
    private final Set<Long> deptIds;
    private final Set<Long> userIds;

    public DataPermissionDecision(DataScopeType scopeType, Set<Long> deptIds, Set<Long> userIds) {
        this.scopeType = scopeType == null ? DataScopeType.SELF : scopeType;
        this.deptIds = deptIds == null ? Set.of() : Set.copyOf(deptIds);
        this.userIds = userIds == null ? Set.of() : Set.copyOf(userIds);
    }

    public static DataPermissionDecision all() {
        return new DataPermissionDecision(DataScopeType.ALL, Set.of(), Set.of());
    }

    public static DataPermissionDecision tenant() {
        return new DataPermissionDecision(DataScopeType.TENANT, Set.of(), Set.of());
    }

    public static DataPermissionDecision self(Long userId) {
        return new DataPermissionDecision(DataScopeType.SELF, Set.of(), userId == null ? Set.of() : Set.of(userId));
    }

    public DataScopeType scopeType() {
        return scopeType;
    }

    public Set<Long> deptIds() {
        return deptIds;
    }

    public Set<Long> userIds() {
        return userIds;
    }

    public boolean hasDeptRestriction() {
        return scopeType == DataScopeType.DEPT || scopeType == DataScopeType.DEPT_AND_CHILD || scopeType == DataScopeType.CUSTOM;
    }

    static DataPermissionDecision merge(DataScopeType scopeType, Set<Long> deptIds, Set<Long> userIds) {
        return new DataPermissionDecision(scopeType, new LinkedHashSet<>(deptIds), new LinkedHashSet<>(userIds));
    }
}
