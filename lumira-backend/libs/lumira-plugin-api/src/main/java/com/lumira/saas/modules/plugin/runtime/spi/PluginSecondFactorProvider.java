package com.lumira.saas.modules.plugin.runtime.spi;

import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorChallenge;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorProfile;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorVerification;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginUserIdentity;

public interface PluginSecondFactorProvider {

    String factorCode();

    String factorName();

    boolean requiresEmail();

    PluginSecondFactorProfile profile(PluginRuntimeContext context, PluginUserIdentity user);

    PluginSecondFactorChallenge prepareChallenge(PluginRuntimeContext context, PluginUserIdentity user);

    PluginSecondFactorVerification verify(PluginRuntimeContext context, String challengeId, String verificationCode);

    PluginSecondFactorChallenge bind(PluginRuntimeContext context, PluginUserIdentity user, String email, String mobile);

    void unbind(PluginRuntimeContext context, PluginUserIdentity user);
}
