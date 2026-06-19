package com.lumira.saas.modules.system.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.platform.app.PlatformBootstrapService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicSystemController {

    private static final Long PLATFORM_TENANT_ID = com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID;

    private final SystemManagementAppService systemManagementAppService;
    private final SystemVerificationAppService systemVerificationAppService;
    private final PlatformBootstrapService platformBootstrapService;

    public PublicSystemController(
            SystemManagementAppService systemManagementAppService,
            SystemVerificationAppService systemVerificationAppService,
            PlatformBootstrapService platformBootstrapService
    ) {
        this.systemManagementAppService = systemManagementAppService;
        this.systemVerificationAppService = systemVerificationAppService;
        this.platformBootstrapService = platformBootstrapService;
    }

    @GetMapping("/bootstrap")
    public ApiResponse<SystemVO.PublicBootstrapVO> bootstrap() {
        return ApiResponse.success(platformBootstrapService.getPublicBootstrap(PLATFORM_TENANT_ID), TraceContext.getRequestId());
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
