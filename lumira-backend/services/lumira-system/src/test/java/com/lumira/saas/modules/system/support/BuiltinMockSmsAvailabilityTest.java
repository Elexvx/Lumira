package com.lumira.saas.modules.system.support;

import com.lumira.api.plugin.PluginFeatureStateApi;
import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuiltinMockSmsAvailabilityTest {

    @Test
    void developmentRequiresEnabledPlugin() {
        PluginFeatureStateApi stateApi = mock(PluginFeatureStateApi.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<PluginFeatureStateApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(stateApi);
        when(stateApi.isPluginEnabled(BuiltinMockSmsAvailability.PLUGIN_CODE)).thenReturn(true);
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "dev");
        environment.setActiveProfiles("dev");

        BuiltinMockSmsAvailability availability = new BuiltinMockSmsAvailability(provider, environment);

        assertThat(availability.isEnabled()).isTrue();
    }

    @Test
    void productionAlwaysFailsClosedEvenWhenPluginRowIsEnabled() {
        PluginFeatureStateApi stateApi = mock(PluginFeatureStateApi.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<PluginFeatureStateApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(stateApi);
        when(stateApi.isPluginEnabled(BuiltinMockSmsAvailability.PLUGIN_CODE)).thenReturn(true);
        when(stateApi.isPluginEnabledForWrite(BuiltinMockSmsAvailability.PLUGIN_CODE)).thenReturn(true);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        BuiltinMockSmsAvailability availability = new BuiltinMockSmsAvailability(provider, environment);

        assertThat(availability.isEnabled()).isFalse();
        assertThatThrownBy(availability::requireEnabledForWrite)
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅允许在本地开发或测试环境使用");
    }

    @Test
    void writeFailsClosedWhenPluginIsDisabled() {
        PluginFeatureStateApi stateApi = mock(PluginFeatureStateApi.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<PluginFeatureStateApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(stateApi);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        BuiltinMockSmsAvailability availability = new BuiltinMockSmsAvailability(provider, environment);

        assertThatThrownBy(availability::requireEnabledForWrite)
                .isInstanceOf(BizException.class)
                .hasMessageContaining("插件未启用");
    }
}
