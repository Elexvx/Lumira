package com.lumira.saas.modules.plugin.runtime.spi;

import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorChallenge;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorProfile;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorVerification;

public interface PluginSecondFactorProvider {

    String factorCode();

    String factorName();

    boolean requiresEmail();

    PluginSecondFactorProfile profile(PluginRuntimeContext context, Long tenantId, Long userId);

    PluginSecondFactorChallenge prepareChallenge(PluginRuntimeContext context, Long tenantId, Long userId);

    PluginSecondFactorVerification verify(PluginRuntimeContext context, String challengeId, String verificationCode);

    PluginSecondFactorChallenge bind(PluginRuntimeContext context, Long tenantId, Long userId, String email, String mobile);

    void unbind(PluginRuntimeContext context, Long tenantId, Long userId);
}
