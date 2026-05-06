package com.legendary.invention.saas.modules.system.controller;

import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.infrastructure.observability.TraceContext;
import com.legendary.invention.saas.modules.system.app.SystemManagementAppService;
import com.legendary.invention.saas.modules.system.verification.SystemVerificationAppService;
import com.legendary.invention.saas.modules.system.vo.SystemVO;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicSystemController {

    private static final Long PLATFORM_TENANT_ID = 1001L;

    private final SystemManagementAppService systemManagementAppService;
    private final SystemVerificationAppService systemVerificationAppService;

    public PublicSystemController(SystemManagementAppService systemManagementAppService, SystemVerificationAppService systemVerificationAppService) {
        this.systemManagementAppService = systemManagementAppService;
        this.systemVerificationAppService = systemVerificationAppService;
    }

    @SentinelResource(value = "public-branding-settings", blockHandler = "brandingSettingsBlocked", blockHandlerClass = PublicSystemSentinelBlockHandler.class)
    @GetMapping("/branding-settings")
    public ApiResponse<SystemVO.BrandingSettingsVO> brandingSettings() {
        return ApiResponse.success(
                systemManagementAppService.getPublicBrandingSettings(PLATFORM_TENANT_ID),
                TraceContext.getRequestId()
        );
    }

    @SentinelResource(value = "public-agreement-settings", blockHandler = "agreementSettingsBlocked", blockHandlerClass = PublicSystemSentinelBlockHandler.class)
    @GetMapping("/agreement-settings")
    public ApiResponse<SystemVO.AgreementSettingsVO> agreementSettings() {
        return ApiResponse.success(
                systemManagementAppService.getPublicAgreementSettings(),
                TraceContext.getRequestId()
        );
    }

    @SentinelResource(value = "public-security-settings", blockHandler = "securitySettingsBlocked", blockHandlerClass = PublicSystemSentinelBlockHandler.class)
    @GetMapping("/security-settings")
    public ApiResponse<SystemVO.SecuritySettingsVO> securitySettings() {
        return ApiResponse.success(
                systemManagementAppService.getPublicSecuritySettings(),
                TraceContext.getRequestId()
        );
    }

    @SentinelResource(value = "public-login-capabilities", blockHandler = "loginCapabilitiesBlocked", blockHandlerClass = PublicSystemSentinelBlockHandler.class)
    @GetMapping("/login-capabilities")
    public ApiResponse<SystemVO.LoginCapabilitiesVO> loginCapabilities() {
        return ApiResponse.success(
                systemVerificationAppService.loadLoginCapabilities(PLATFORM_TENANT_ID),
                TraceContext.getRequestId()
        );
    }
}
