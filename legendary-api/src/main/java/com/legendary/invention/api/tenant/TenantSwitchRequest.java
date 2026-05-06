package com.legendary.invention.api.tenant;

import jakarta.validation.constraints.NotNull;

public record TenantSwitchRequest(@NotNull Long tenantId) {
}
