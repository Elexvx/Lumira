package com.lumira.saas.modules.account.app.port;

import java.util.Optional;

/** Platform-owned configuration needed by the Account activation flow. */
public interface AccountActivationConfigurationPort {
    Optional<String> activationBaseUrl();
}
