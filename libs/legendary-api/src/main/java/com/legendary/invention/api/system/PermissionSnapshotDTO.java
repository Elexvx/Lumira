package com.legendary.invention.api.system;

import com.legendary.invention.common.security.data.DataPermissionRule;

import java.util.List;

public record PermissionSnapshotDTO(
        String version,
        List<String> permissions,
        List<Long> roleIds,
        Long primaryDeptId,
        List<Long> deptIds,
        List<Long> descendantDeptIds,
        List<DataPermissionRule> dataScopes,
        String defaultHomePath
) {
}
