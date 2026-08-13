package com.lumira.saas.modules.competition.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;
import org.springframework.util.StringUtils;

/** Shared trusted-request refresh policy for the Competition bounded context. */
public final class CompetitionAuthenticationTrust {
    static final String ASYNC_EXPORT_SESSION_PREFIX = "internal-registration-export-task-";

    private CompetitionAuthenticationTrust() {
    }

    /**
     * Creates the session marker used by the registration export snapshot.
     * This is deliberately not a real web session and must not be sent to Redis
     * session authentication.
     */
    static String asyncExportSessionId(Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new IllegalArgumentException("Async export task id must be positive");
        }
        return ASYNC_EXPORT_SESSION_PREFIX + taskId;
    }

    static boolean isAsyncExportSession(CurrentUser currentUser) {
        if (currentUser == null || !StringUtils.hasText(currentUser.getSessionId())) {
            return false;
        }
        String sessionId = currentUser.getSessionId().trim();
        if (!sessionId.startsWith(ASYNC_EXPORT_SESSION_PREFIX)) {
            return false;
        }
        String taskId = sessionId.substring(ASYNC_EXPORT_SESSION_PREFIX.length());
        return !taskId.isEmpty() && taskId.chars().allMatch(Character::isDigit);
    }

    public static void refresh(
            CurrentUser currentUser,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            boolean enforceTrustedUserResolution
    ) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)
                || isAsyncExportSession(currentUser)) {
            // Export workers rebuild this user with TrustedUserSnapshotResolver
            // before each business operation. The ephemeral marker intentionally
            // has no Redis session ticket, so resolving it here would report a
            // false SESSION_EXPIRED failure.
            return;
        }
        if (trustedCurrentUserResolver == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return;
        }
        CurrentUser refreshed = trustedCurrentUserResolver.resolve(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(refreshed)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        copy(currentUser, refreshed);
    }

    static void copy(CurrentUser target, CurrentUser source) {
        target.setUserId(source.getUserId());
        target.setUserUuid(source.getUserUuid());
        target.setUsername(source.getUsername());
        target.setSessionId(source.getSessionId());
        target.setSessionVersion(source.getSessionVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setPermissions(source.getPermissions());
        target.setRoleIds(source.getRoleIds());
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds());
        target.setDescendantDeptIds(source.getDescendantDeptIds());
        target.setDataScopes(source.getDataScopes());
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
    }

    static Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }
}
