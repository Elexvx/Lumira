package com.lumira.api.workflow;

import com.lumira.common.security.CurrentUser;

/**
 * System-owned identity and authorization resolution required by workflows.
 *
 * <p>The workflow context receives only this contract, never the system
 * service's session, IAM, or user persistence implementations.</p>
 */
public interface WorkflowUserAccessPort {
    CurrentUser refreshTrustedUser(CurrentUser currentUser);

    String findEnabledUserUuid(Long userId);
}
