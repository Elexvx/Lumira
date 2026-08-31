package com.lumira.api.system.port;

import com.lumira.api.system.PermissionSnapshotDTO;

public interface PermissionSnapshotPort {
    PermissionSnapshotDTO permissionSnapshot(Long userId, String userUuid);
    PermissionSnapshotDTO permissionRoleSnapshot(Long userId, String userUuid);
    PermissionSnapshotDTO simulatedRolePermissionSnapshot(Long userId, String userUuid, Long roleId);
}
