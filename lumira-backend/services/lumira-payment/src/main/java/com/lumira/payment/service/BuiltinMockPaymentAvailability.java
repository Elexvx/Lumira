package com.lumira.payment.service;

import com.lumira.api.plugin.PluginFeatureStateApi;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class BuiltinMockPaymentAvailability {

    public static final String PLUGIN_CODE = "builtin-mock-payment";
    public static final String PROVIDER_CODE = "builtin_mock";

    private final ObjectProvider<PluginFeatureStateApi> pluginFeatureStateApiProvider;

    public BuiltinMockPaymentAvailability(ObjectProvider<PluginFeatureStateApi> pluginFeatureStateApiProvider) {
        this.pluginFeatureStateApiProvider = pluginFeatureStateApiProvider;
    }

    public boolean isEnabled() {
        try {
            PluginFeatureStateApi stateApi = pluginFeatureStateApiProvider == null
                    ? null
                    : pluginFeatureStateApiProvider.getIfAvailable();
            return stateApi != null && stateApi.isPluginEnabled(PLUGIN_CODE);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public void requireEnabled() {
        if (!isEnabled()) {
            throw notEnabled();
        }
    }

    public void requireEnabledForWrite() {
        try {
            PluginFeatureStateApi stateApi = pluginFeatureStateApiProvider == null
                    ? null
                    : pluginFeatureStateApiProvider.getIfAvailable();
            if (stateApi != null && stateApi.isPluginEnabledForWrite(PLUGIN_CODE)) {
                return;
            }
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException ignored) {
            // Fail closed when plugin state cannot be resolved.
        }
        throw notEnabled();
    }

    public BizException notEnabled() {
        return new BizException(ErrorCode.PLUGIN_NOT_ENABLED, "内置模拟支付插件未启用");
    }
}
