package com.lumira.team.app;

import org.springframework.stereotype.Component;

@Component
public class NoopTeamAuditPort implements TeamAuditPort {
    @Override
    public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
        // TODO(team-governance): bridge this port to the platform audit facade once audit has a stable cross-module API.
    }
}
