package com.lumira.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.auth.service.AuthPostLoginBootstrapProvider;
import com.lumira.saas.modules.plugin.app.PluginManagementAppService;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LumiraAuthPostLoginBootstrapProviderTest {

    @Test
    void loadShouldRejectUntrustedCurrentUserBeforeCallingDownstreamServices() {
        PluginManagementAppService pluginManagementAppService = mock(PluginManagementAppService.class);
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        LumiraAuthPostLoginBootstrapProvider provider = new LumiraAuthPostLoginBootstrapProvider(
                pluginManagementAppService,
                systemManagementAppService,
                new ObjectMapper()
        );
        CurrentUserDTO untrustedCurrentUser = new CurrentUserDTO(
                1001L,
                "user-uuid-1001",
                "operator",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "zh-CN",
                null,
                List.of(),
                null,
                "permissions-1",
                1,
                List.of("*"),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                false,
                "/"
        );

        AuthPostLoginBootstrapProvider.AuthPostLoginBootstrapPayload payload = provider.load(untrustedCurrentUser);

        assertThat(payload.menuTree()).isEmpty();
        assertThat(payload.availablePlugins()).isEmpty();
        assertThat(payload.runtimeAppearanceSettings()).isEmpty();
        verifyNoInteractions(pluginManagementAppService, systemManagementAppService);
    }

    @Test
    void loadShouldRejectCurrentUserWithoutUuidBeforeUsingPermissions() {
        PluginManagementAppService pluginManagementAppService = mock(PluginManagementAppService.class);
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        LumiraAuthPostLoginBootstrapProvider provider = new LumiraAuthPostLoginBootstrapProvider(
                pluginManagementAppService,
                systemManagementAppService,
                new ObjectMapper()
        );
        CurrentUserDTO untrustedCurrentUser = trustedCurrentUser(null, "permissions-1");

        AuthPostLoginBootstrapProvider.AuthPostLoginBootstrapPayload payload = provider.load(untrustedCurrentUser);

        assertThat(payload.menuTree()).isEmpty();
        assertThat(payload.availablePlugins()).isEmpty();
        assertThat(payload.runtimeAppearanceSettings()).isEmpty();
        verifyNoInteractions(pluginManagementAppService, systemManagementAppService);
    }

    @Test
    void loadShouldRejectCurrentUserWithoutPermissionsVersionBeforeUsingPermissions() {
        PluginManagementAppService pluginManagementAppService = mock(PluginManagementAppService.class);
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        LumiraAuthPostLoginBootstrapProvider provider = new LumiraAuthPostLoginBootstrapProvider(
                pluginManagementAppService,
                systemManagementAppService,
                new ObjectMapper()
        );
        CurrentUserDTO untrustedCurrentUser = trustedCurrentUser("user-uuid", null);

        AuthPostLoginBootstrapProvider.AuthPostLoginBootstrapPayload payload = provider.load(untrustedCurrentUser);

        assertThat(payload.menuTree()).isEmpty();
        assertThat(payload.availablePlugins()).isEmpty();
        assertThat(payload.runtimeAppearanceSettings()).isEmpty();
        verifyNoInteractions(pluginManagementAppService, systemManagementAppService);
    }

    @Test
    void loadShouldRejectCurrentUserWithUnsafeSessionIdBeforeCallingDownstreamServices() {
        PluginManagementAppService pluginManagementAppService = mock(PluginManagementAppService.class);
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        LumiraAuthPostLoginBootstrapProvider provider = new LumiraAuthPostLoginBootstrapProvider(
                pluginManagementAppService,
                systemManagementAppService,
                new ObjectMapper()
        );
        CurrentUserDTO untrustedCurrentUser = trustedCurrentUser("user-uuid", "permissions-1", "session//1");

        AuthPostLoginBootstrapProvider.AuthPostLoginBootstrapPayload payload = provider.load(untrustedCurrentUser);

        assertThat(payload.menuTree()).isEmpty();
        assertThat(payload.availablePlugins()).isEmpty();
        assertThat(payload.runtimeAppearanceSettings()).isEmpty();
        verifyNoInteractions(pluginManagementAppService, systemManagementAppService);
    }

    @Test
    void loadShouldUsePublicRuntimeAppearanceSettingsForTrustedUsers() {
        PluginManagementAppService pluginManagementAppService = mock(PluginManagementAppService.class);
        when(pluginManagementAppService.currentBootstrap(List.of("*"), "permissions-1", null, null))
                .thenReturn(Map.of(
                        "menuTree", List.of(Map.of("menuCode", "dashboard.home")),
                        "availablePlugins", List.of(Map.of("pluginCode", "feedback"))
                ));
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SystemVO.RuntimeAppearanceSettingsVO runtimeAppearanceSettings = new SystemVO.RuntimeAppearanceSettingsVO();
        runtimeAppearanceSettings.setBrandingSettings(new SystemVO.BrandingSettingsVO());
        runtimeAppearanceSettings.setWatermarkSettings(new SystemVO.WatermarkSettingsVO());
        runtimeAppearanceSettings.setFloatingWindowSettings(new SystemVO.FloatingWindowSettingsVO());
        when(systemManagementAppService.getPublicRuntimeAppearanceSettings()).thenReturn(runtimeAppearanceSettings);

        LumiraAuthPostLoginBootstrapProvider provider = new LumiraAuthPostLoginBootstrapProvider(
                pluginManagementAppService,
                systemManagementAppService,
                new ObjectMapper()
        );

        AuthPostLoginBootstrapProvider.AuthPostLoginBootstrapPayload payload = provider.load(trustedCurrentUser("user-uuid", "permissions-1"));

        assertThat(payload.menuTree()).hasSize(1);
        assertThat(payload.availablePlugins()).hasSize(1);
        assertThat(payload.runtimeAppearanceSettings()).containsKeys("brandingSettings", "watermarkSettings", "floatingWindowSettings");
        verify(systemManagementAppService).getPublicRuntimeAppearanceSettings();
    }

    private static CurrentUserDTO trustedCurrentUser(String userUuid, String permissionsVersion) {
        return trustedCurrentUser(userUuid, permissionsVersion, "session-1");
    }

    private static CurrentUserDTO trustedCurrentUser(String userUuid, String permissionsVersion, String sessionId) {
        return new CurrentUserDTO(
                1001L,
                userUuid,
                "operator",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "zh-CN",
                null,
                List.of(),
                sessionId,
                permissionsVersion,
                1,
                List.of("*"),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                false,
                "/"
        );
    }
}
