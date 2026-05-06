package com.legendary.invention.api.tenant;

import java.time.LocalDateTime;

public record TenantSummaryDTO(
        Long tenantId,
        String tenantCode,
        String tenantName,
        String tenantShortName,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
