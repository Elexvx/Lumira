package com.lumira.api.system.port;

public interface AuthorizationVersionPort {
    Boolean isPermissionSnapshotVersionCurrent(String version);
    Boolean invalidatePermissionSnapshot();
}
