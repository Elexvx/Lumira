package com.lumira.common.security.data;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DataPermissionResolver {

    private DataPermissionResolver() {
    }

    public static DataPermissionDecision resolve(
            String resourceCode,
            Long userId,
            Collection<Long> deptIds,
            Collection<Long> descendantDeptIds,
            Collection<DataPermissionRule> rules,
            Collection<String> permissions
    ) {
        if (permissions != null && permissions.contains("*")) {
            return DataPermissionDecision.all();
        }
        List<DataPermissionRule> matchedRules = rules == null ? List.of() : rules.stream()
                .filter(rule -> rule != null && rule.matches(resourceCode))
                .toList();
        if (matchedRules.isEmpty()) {
            return DataPermissionDecision.self(userId);
        }
        if (matchedRules.stream().anyMatch(rule -> rule.scopeType() == DataScopeType.ALL)) {
            return DataPermissionDecision.all();
        }
        if (matchedRules.stream().anyMatch(rule -> rule.scopeType() == DataScopeType.TENANT)) {
            return DataPermissionDecision.tenant();
        }

        Set<Long> resolvedDeptIds = new LinkedHashSet<>();
        Set<Long> resolvedUserIds = new LinkedHashSet<>();
        DataScopeType effectiveType = DataScopeType.SELF;
        for (DataPermissionRule rule : matchedRules) {
            switch (rule.scopeType()) {
                case DEPT_AND_CHILD -> {
                    effectiveType = DataScopeType.DEPT_AND_CHILD;
                    addAll(resolvedDeptIds, deptIds);
                    addAll(resolvedDeptIds, descendantDeptIds);
                }
                case DEPT -> {
                    if (effectiveType == DataScopeType.SELF || effectiveType == DataScopeType.CUSTOM) {
                        effectiveType = DataScopeType.DEPT;
                    }
                    addAll(resolvedDeptIds, deptIds);
                }
                case CUSTOM -> {
                    if (effectiveType == DataScopeType.SELF) {
                        effectiveType = DataScopeType.CUSTOM;
                    }
                    addAll(resolvedDeptIds, rule.customDeptIds());
                    addAll(resolvedUserIds, rule.customUserIds());
                }
                case SELF -> {
                    if (userId != null) {
                        resolvedUserIds.add(userId);
                    }
                }
                default -> {
                }
            }
        }
        if (resolvedDeptIds.isEmpty() && resolvedUserIds.isEmpty() && userId != null) {
            resolvedUserIds.add(userId);
            effectiveType = DataScopeType.SELF;
        }
        return DataPermissionDecision.merge(effectiveType, resolvedDeptIds, resolvedUserIds);
    }

    private static void addAll(Set<Long> target, Collection<Long> source) {
        if (source == null) {
            return;
        }
        source.stream().filter(item -> item != null && item > 0).forEach(target::add);
    }
}
