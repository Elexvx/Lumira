package com.lumira.saas.modules.system.integration.workflow;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.workflow.WorkflowUserAccessPort;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import org.springframework.util.StringUtils;

/**
 * System-owned identity adapter for the Workflow context.
 *
 * <p>Session and IAM resolution stay in system-service.  Workflow receives a
 * refreshed principal through a small common-api port instead of importing
 * either implementation.</p>
 */
public class SystemWorkflowUserAccessAdapter implements WorkflowUserAccessPort {
    private final SessionAuthenticationService sessionAuthenticationService;
    private final SystemInternalApi systemInternalApi;

    public SystemWorkflowUserAccessAdapter(
            SessionAuthenticationService sessionAuthenticationService,
            SystemInternalApi systemInternalApi
    ) {
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.systemInternalApi = systemInternalApi;
    }

    @Override
    public CurrentUser refreshTrustedUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return currentUser;
        }
        SessionAuthenticationService.AuthenticatedAccess authenticatedAccess =
                sessionAuthenticationService.authenticateSessionTicket(
                        currentUser.getSessionId(),
                        currentUser.getUserId(),
                        currentUser.getUserUuid(),
                        currentUser.getSimulatedRoleId(),
                        currentUser.getSessionVersion(),
                        currentUser.getPermissionsVersion()
                );
        CurrentUser refreshed = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(refreshed)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        return refreshed;
    }

    @Override
    public String findEnabledUserUuid(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        String userUuid = systemInternalApi.findTargetUserUuidById(userId);
        return StringUtils.hasText(userUuid) ? userUuid.trim() : null;
    }
}
