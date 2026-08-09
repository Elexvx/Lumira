package com.lumira.saas.modules.competition.support;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;
import java.util.List;
import java.util.Set;
import org.springframework.util.StringUtils;

/** Adapts legacy-style test fixtures to the shared trusted-user port. */
public final class CompetitionTrustTestFixtures {
    private CompetitionTrustTestFixtures() {
    }

    public static TrustedCurrentUserResolver resolver(
            CompetitionPermissionSnapshotFixture permissionSnapshot,
            SystemInternalApi systemInternalApi,
            CompetitionSessionAuthenticationFixture sessionAuthentication
    ) {
        if (sessionAuthentication != null) {
            return currentUser -> {
                CompetitionSessionAuthenticationFixture.AuthenticatedAccess access =
                        sessionAuthentication.authenticateSessionTicket(
                                currentUser.getSessionId(),
                                currentUser.getUserId(),
                                currentUser.getUserUuid(),
                                normalizeRoleId(currentUser.getSimulatedRoleId()),
                                currentUser.getSessionVersion(),
                                currentUser.getPermissionsVersion()
                        );
                if (access == null || access.currentUser() == null) {
                    throw unauthorized("Login required");
                }
                return access.currentUser();
            };
        }
        if (permissionSnapshot == null) {
            return null;
        }
        return currentUser -> refresh(permissionSnapshot, systemInternalApi, currentUser);
    }

    private static CurrentUser refresh(
            CompetitionPermissionSnapshotFixture permissionSnapshot,
            SystemInternalApi systemInternalApi,
            CurrentUser currentUser
    ) {
        Long userId = currentUser.getUserId();
        String userUuid = trim(currentUser.getUserUuid());
        if (userId == null || userId <= 0 || userUuid == null) {
            throw unauthorized("Login required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO identity = systemInternalApi.findUserIdentityById(userId);
            if (identity == null
                    || !userId.equals(identity.userId())
                    || !userUuid.equals(trim(identity.userUuid()))) {
                throw unauthorized("Login required");
            }
            if (!"ENABLED".equalsIgnoreCase(identity.status())) {
                throw unauthorized("Trusted user is disabled or no longer active");
            }
            if (!StringUtils.hasText(identity.username())) {
                throw unauthorized("Trusted user username is unavailable");
            }
            currentUser.setUsername(identity.username().trim());
            currentUser.setUserUuid(identity.userUuid().trim());
            userUuid = currentUser.getUserUuid();
        }
        if (!permissionSnapshot.isTrustedActiveUser(userId, userUuid)) {
            throw unauthorized("Trusted user is disabled or no longer active");
        }
        Long simulatedRoleId = normalizeRoleId(currentUser.getSimulatedRoleId());
        CompetitionPermissionSnapshotFixture.PermissionSnapshot snapshot = simulatedRoleId == null
                ? permissionSnapshot.loadSnapshot(userId, userUuid)
                : permissionSnapshot.loadGrantedRoleSnapshot(userId, userUuid, simulatedRoleId);
        if (snapshot == null) {
            throw unauthorized("Trusted user permission snapshot is unavailable");
        }
        currentUser.setSimulatedRoleId(simulatedRoleId);
        currentUser.setPermissions(snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()));
        currentUser.setRoleIds(snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()));
        currentUser.setPrimaryDeptId(snapshot.getPrimaryDeptId());
        currentUser.setDeptIds(snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()));
        currentUser.setDescendantDeptIds(snapshot.getDescendantDeptIds() == null
                ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()));
        currentUser.setDataScopes(snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes()));
        currentUser.setPermissionsVersion(snapshot.getVersion());
        currentUser.setDefaultHomePath(snapshot.getDefaultHomePath());
        return currentUser;
    }

    private static Long normalizeRoleId(Long roleId) {
        return roleId == null || roleId <= 0 ? null : roleId;
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static BizException unauthorized(String message) {
        return new BizException(ErrorCode.UNAUTHORIZED, message);
    }
}
