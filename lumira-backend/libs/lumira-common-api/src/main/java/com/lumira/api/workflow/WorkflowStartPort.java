package com.lumira.api.workflow;

import com.lumira.common.security.CurrentUser;
import java.util.Map;

/**
 * Owner contract for starting a workflow from another bounded context.
 *
 * <p>This is deliberately narrower than the workflow HTTP API: callers may
 * create an instance for a business aggregate, but do not receive persistence
 * or configuration access.</p>
 */
public interface WorkflowStartPort {
    String BUSINESS_EXPERT_APPLICATION = "EXPERT_APPLICATION";

    Long startWorkflow(
            CurrentUser currentUser,
            String businessType,
            Long businessId,
            String businessUuid,
            String businessTitle,
            Map<String, Object> variables
    );
}
