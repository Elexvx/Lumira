package com.lumira.saas.modules.system.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.constant.PlatformConstants;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.platform.app.PlatformBootstrapService;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicSystemControllerTest {

    @Test
    void bootstrapAggregatesPublicStartupSettings() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SystemVerificationAppService systemVerificationAppService = mock(SystemVerificationAppService.class);
        PlatformBootstrapService platformBootstrapService = mock(PlatformBootstrapService.class);
        SystemVO.PublicBootstrapVO bootstrap = new SystemVO.PublicBootstrapVO();

        when(platformBootstrapService.getPublicBootstrap(PlatformConstants.PLATFORM_TENANT_ID)).thenReturn(bootstrap);

        PublicSystemController controller = new PublicSystemController(
                systemManagementAppService,
                systemVerificationAppService,
                platformBootstrapService
        );

        ApiResponse<SystemVO.PublicBootstrapVO> response = controller.bootstrap();

        assertThat(response.getData()).isSameAs(bootstrap);
        verify(platformBootstrapService).getPublicBootstrap(PlatformConstants.PLATFORM_TENANT_ID);
    }
}
