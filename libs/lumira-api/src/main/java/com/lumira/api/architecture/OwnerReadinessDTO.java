package com.lumira.api.architecture;

import java.util.List;

public record OwnerReadinessDTO(
        String context,
        String ownerModule,
        String status,
        String readinessLevel,
        List<String> ownerTablePatterns,
        List<String> apiContracts,
        List<String> eventContracts,
        List<String> healthChecks,
        List<String> metrics,
        List<String> dependencies,
        List<String> rollbackSteps,
        List<String> blockers
) {
}
