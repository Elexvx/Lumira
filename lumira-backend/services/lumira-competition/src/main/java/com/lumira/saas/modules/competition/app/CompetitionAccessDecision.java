package com.lumira.saas.modules.competition.app;

import java.util.Set;

/** Result of resolving a user and a competition workspace together. */
public record CompetitionAccessDecision(
        CompetitionRef competition,
        Set<CompetitionCapability> capabilities
) {
    public boolean allows(CompetitionCapability capability) {
        return capability != null && capabilities.contains(capability);
    }
}
