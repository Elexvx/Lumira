package com.legendary.invention.api.client;

import com.legendary.invention.api.tenant.MyTenantDTO;
import com.legendary.invention.api.tenant.TenantSummaryDTO;
import com.legendary.invention.api.tenant.TenantSwitchCheckDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "tenant-service", contextId = "tenantInternalApi", url = "${TENANT_SERVICE_BASE_URL:}", path = "/internal/tenant")
public interface TenantInternalApi {

    @GetMapping("/users/{userId}/visible-tenants")
    List<MyTenantDTO> listVisibleTenants(@PathVariable("userId") Long userId);

    @GetMapping("/summary/{tenantId}")
    TenantSummaryDTO findTenantSummary(@PathVariable("tenantId") Long tenantId);

    @GetMapping("/users/{userId}/switch-check")
    TenantSwitchCheckDTO validateTenantSwitch(@PathVariable("userId") Long userId, @RequestParam("tenantId") Long tenantId);
}
