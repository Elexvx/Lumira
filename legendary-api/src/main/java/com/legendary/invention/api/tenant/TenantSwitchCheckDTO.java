package com.legendary.invention.api.tenant;

public record TenantSwitchCheckDTO(
        Long tenantId,
        TenantSummaryDTO tenant,
        boolean allowed,
        String message
) {
}
