package com.legendary.invention.saas.modules.system.controller;

import com.legendary.invention.common.api.ApiResponse;
import com.legendary.invention.common.web.TraceContext;
import com.legendary.invention.saas.modules.system.app.SystemManagementAppService;
import com.legendary.invention.saas.modules.system.verification.SystemVerificationAppService;
import com.legendary.invention.saas.modules.system.vo.SystemVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicSystemController {

    private static final Long PLATFORM_TENANT_ID = com.legendary.invention.common.constant.PlatformConstants.PLATFORM_TENANT_ID;

    private final SystemManagementAppService systemManagementAppService;
    private final SystemVerificationAppService systemVerificationAppService;

    public PublicSystemController(SystemManagementAppService systemManagementAppService, SystemVerificationAppService systemVerificationAppService) {
        this.systemManagementAppService = systemManagementAppService;
        this.systemVerificationAppService = systemVerificationAppService;
    }
    @GetMapping("/branding-settings")
    public ApiResponse<SystemVO.BrandingSettingsVO> brandingSettings() {
        return ApiResponse.success(
                systemManagementAppService.getPublicBrandingSettings(PLATFORM_TENANT_ID),
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
    @GetMapping("/login-capabilities")
    public ApiResponse<SystemVO.LoginCapabilitiesVO> loginCapabilities() {
        return ApiResponse.success(
                systemVerificationAppService.loadLoginCapabilities(PLATFORM_TENANT_ID),
                TraceContext.getRequestId()
        );
    }
}
