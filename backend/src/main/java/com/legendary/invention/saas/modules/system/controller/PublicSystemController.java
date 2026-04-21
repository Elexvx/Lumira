package com.legendary.invention.saas.modules.system.controller;

import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.infrastructure.observability.TraceContext;
import com.legendary.invention.saas.infrastructure.tenant.TenantContext;
import com.legendary.invention.saas.modules.system.app.SystemManagementAppService;
import com.legendary.invention.saas.modules.system.vo.SystemVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicSystemController {

    private final SystemManagementAppService systemManagementAppService;

    public PublicSystemController(SystemManagementAppService systemManagementAppService) {
        this.systemManagementAppService = systemManagementAppService;
    }

    @GetMapping("/branding-settings")
    public ApiResponse<SystemVO.BrandingSettingsVO> brandingSettings() {
        return ApiResponse.success(
                systemManagementAppService.getPublicBrandingSettings(resolveTenantId()),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/agreement-settings")
    public ApiResponse<SystemVO.AgreementSettingsVO> agreementSettings() {
        return ApiResponse.success(
                systemManagementAppService.getPublicAgreementSettings(),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/security-settings")
    public ApiResponse<SystemVO.SecuritySettingsVO> securitySettings() {
        return ApiResponse.success(
                systemManagementAppService.getPublicSecuritySettings(),
                TraceContext.getRequestId()
        );
    }

    private Long resolveTenantId() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(tenantId.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
