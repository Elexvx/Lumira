package com.lumira.payment.service;

import com.lumira.api.plugin.PluginFeatureStateApi;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuiltinMockPaymentAvailabilityTest {

    @Test
    void shouldFailClosedWhenPluginStateContractIsUnavailable() {
        ObjectProvider<PluginFeatureStateApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        BuiltinMockPaymentAvailability availability = new BuiltinMockPaymentAvailability(provider);

        assertThat(availability.isEnabled()).isFalse();
        assertThatThrownBy(availability::requireEnabledForWrite)
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLUGIN_NOT_ENABLED));
    }

    @Test
    void shouldUseTheLockingPluginStateContractForCheckoutAndOrderWrites() {
        PluginFeatureStateApi stateApi = mock(PluginFeatureStateApi.class);
        ObjectProvider<PluginFeatureStateApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(stateApi);
        when(stateApi.isPluginEnabledForWrite(BuiltinMockPaymentAvailability.PLUGIN_CODE)).thenReturn(true);
        BuiltinMockPaymentAvailability availability = new BuiltinMockPaymentAvailability(provider);

        availability.requireEnabledForWrite();

        verify(stateApi).isPluginEnabledForWrite(BuiltinMockPaymentAvailability.PLUGIN_CODE);
    }
}

