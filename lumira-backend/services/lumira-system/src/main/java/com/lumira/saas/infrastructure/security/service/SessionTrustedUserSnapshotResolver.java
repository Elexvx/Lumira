package com.lumira.saas.infrastructure.security.service;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedUserSnapshotResolver;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import java.util.List;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * System-owned resolver for asynchronous work that must re-check a user's
 * active identity, role simulation and permission snapshot before execution.
 */
public class SessionTrustedUserSnapshotResolver implements TrustedUserSnapshotResolver {
    private static final String STATUS_ENABLED = "ENABLED";

    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;

    public SessionTrustedUserSnapshotResolver(
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi
    ) {
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
    }

    @Override
    public CurrentUser resolve(
            Long userId,
            String userUuid,
            Long simulatedRoleId,
            String ephemeralSessionId,
            String requiredPermission
    ) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted export user identity is required");
        }
        String normalizedUserUuid = userUuid.trim();
        SystemUserSnapshotDTO identity = systemInternalApi.findUserIdentityById(userId);
        if (identity == null
                || identity.userId() == null
                || !userId.equals(identity.userId())
                || !normalizedUserUuid.equals(trim(identity.userUuid()))
                || !STATUS_ENABLED.equalsIgnoreCase(identity.status())
                || !StringUtils.hasText(identity.username())
                || !permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Export user is disabled or no longer trusted");
        }
        PermissionSnapshotService.PermissionSnapshot snapshot = simulatedRoleId == null
                ? permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid)
                : permissionSnapshotService.loadGrantedRoleSnapshot(userId, normalizedUserUuid, simulatedRoleId);
        if (snapshot == null || !StringUtils.hasText(snapshot.getVersion())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Export user permission snapshot is unavailable");
        }
        if (!permissionSnapshotService.isAuthoritativeSessionPermissionSnapshotCurrent(snapshot.getVersion())) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "Export user authorization snapshot is stale");
        }
        Set<String> permissions = snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions());
        if (StringUtils.hasText(requiredPermission)
                && !permissions.contains("*")
                && !permissions.contains(requiredPermission.trim())) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + requiredPermission.trim());
        }
        CurrentUser currentUser = new CurrentUser(
                userId,
                identity.username().trim(),
                StringUtils.hasText(ephemeralSessionId) ? ephemeralSessionId.trim() : "internal-async",
                1,
                true,
                permissions,
                snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()),
                snapshot.getPrimaryDeptId(),
                snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()),
                snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()),
                snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes())
        );
        currentUser.setUserUuid(normalizedUserUuid);
        currentUser.setPermissionsVersion(snapshot.getVersion());
        currentUser.setDefaultHomePath(snapshot.getDefaultHomePath());
        currentUser.setSimulatedRoleId(simulatedRoleId);
        return currentUser;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
