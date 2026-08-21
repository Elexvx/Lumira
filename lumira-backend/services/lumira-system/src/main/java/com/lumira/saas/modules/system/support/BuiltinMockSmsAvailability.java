package com.lumira.saas.modules.system.support;

import com.lumira.api.plugin.PluginFeatureStateApi;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class BuiltinMockSmsAvailability {

    public static final String PLUGIN_CODE = "builtin-mock-sms";
    public static final String PROVIDER_CODE = "builtin_mock_sms";

    private final ObjectProvider<PluginFeatureStateApi> pluginFeatureStateApiProvider;
    private final Environment environment;

    public BuiltinMockSmsAvailability(
            ObjectProvider<PluginFeatureStateApi> pluginFeatureStateApiProvider,
            Environment environment
    ) {
        this.pluginFeatureStateApiProvider = pluginFeatureStateApiProvider;
        this.environment = environment;
    }

    public boolean isEnvironmentAllowed() {
        // A mixed profile set containing prod must always fail closed.
        if (environment == null || environment.acceptsProfiles(Profiles.of("prod"))) {
            return false;
        }
        return environment.acceptsProfiles(Profiles.of("dev", "docker-local", "test"));
    }

    public boolean isEnabled() {
        if (!isEnvironmentAllowed()) {
            return false;
        }
        try {
            PluginFeatureStateApi stateApi = pluginFeatureStateApiProvider == null
                    ? null
                    : pluginFeatureStateApiProvider.getIfAvailable();
            return stateApi != null && stateApi.isPluginEnabled(PLUGIN_CODE);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public void requireEnabledForWrite() {
        if (!isEnvironmentAllowed()) {
            throw new BizException(ErrorCode.FORBIDDEN, "模拟短信验证码仅允许在本地开发或测试环境使用");
        }
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
        throw new BizException(ErrorCode.PLUGIN_NOT_ENABLED, "内置模拟短信验证码插件未启用");
    }
}
