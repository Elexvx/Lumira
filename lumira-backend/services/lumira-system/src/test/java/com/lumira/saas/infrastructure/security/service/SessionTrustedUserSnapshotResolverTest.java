package com.lumira.saas.infrastructure.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SessionTrustedUserSnapshotResolverTest {

    @Test
    void rejectsAnAsyncTrustedUserWhenTheLoadedSnapshotIsNoLongerAuthoritative() {
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(42L)).thenReturn(
                new SystemUserSnapshotDTO(
                        42L,
                        "user-uuid-42",
                        "async-user",
                        null,
                        "ENABLED",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
        when(permissionSnapshotService.isTrustedActiveUser(42L, "user-uuid-42")).thenReturn(true);
        PermissionSnapshotService.PermissionSnapshot staleSnapshot = new PermissionSnapshotService.PermissionSnapshot(
                "v1:data-scope-cache-v4",
                Set.of("competition:export")
        );
        when(permissionSnapshotService.loadSnapshot(42L, "user-uuid-42")).thenReturn(staleSnapshot);
        when(permissionSnapshotService.isAuthoritativeSessionPermissionSnapshotCurrent(staleSnapshot.getVersion()))
                .thenReturn(false);

        BizException exception = assertThrows(
                BizException.class,
                () -> new SessionTrustedUserSnapshotResolver(permissionSnapshotService, systemInternalApi).resolve(
                        42L,
                        "user-uuid-42",
                        null,
                        "async-job",
                        "competition:export"
                )
        );

        assertEquals(ErrorCode.SESSION_EXPIRED, exception.getErrorCode());
    }
}
