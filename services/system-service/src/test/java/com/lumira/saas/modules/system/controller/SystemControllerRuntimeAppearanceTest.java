package com.lumira.saas.modules.system.controller;

import com.lumira.api.client.FileInternalApi;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.export.ExportTaskService;
import com.lumira.saas.modules.system.user.app.UserExportAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SystemControllerRuntimeAppearanceTest {

    @Test
    void runtimeAppearanceSettingsDoesNotRequireConfigPermission() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        CurrentUser currentUser = new CurrentUser(2001L, "alice", 1001L, "session-1", 1, true, Set.of());
        SystemVO.RuntimeAppearanceSettingsVO settings = new SystemVO.RuntimeAppearanceSettingsVO();
        settings.setBrandingSettings(new SystemVO.BrandingSettingsVO());
        settings.setWatermarkSettings(new SystemVO.WatermarkSettingsVO());
        settings.setFloatingWindowSettings(new SystemVO.FloatingWindowSettingsVO());

        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemManagementAppService.getRuntimeAppearanceSettings(currentUser)).thenReturn(settings);

        SystemController controller = new SystemController(
                systemManagementAppService,
                securityContextFacade,
                permissionGuard,
                mock(FileInternalApi.class),
                mock(UserExportAppService.class),
                mock(ExportTaskService.class)
        );

        var response = controller.runtimeAppearanceSettings();

        assertThat(response.getData()).isSameAs(settings);
        verify(systemManagementAppService).getRuntimeAppearanceSettings(currentUser);
        verifyNoInteractions(permissionGuard);
    }
}
