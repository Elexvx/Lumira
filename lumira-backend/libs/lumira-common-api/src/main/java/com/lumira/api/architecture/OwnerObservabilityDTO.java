package com.lumira.api.architecture;

import java.time.OffsetDateTime;
import java.util.List;

public record OwnerObservabilityDTO(
        String context,
        String ownerModule,
        String status,
        OffsetDateTime observedAt,
        List<HealthCheckDTO> healthChecks,
        List<MetricDTO> metrics
) {

    public record HealthCheckDTO(
            String name,
            String status,
            String description
    ) {
    }

    public record MetricDTO(
            String name,
            String type,
            String unit,
            String description,
            Double value
    ) {
        public MetricDTO(String name, String type, String unit, String description) {
            this(name, type, unit, description, null);
        }
    }
}
