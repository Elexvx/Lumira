package com.lumira.saas.modules.platform.integration.account;

import com.lumira.saas.modules.account.app.port.AccountActivationConfigurationPort;
import com.lumira.saas.modules.system.settings.repository.SystemPlatformSettingsRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.util.StringUtils;

/** Platform-owned adapter for Account's activation URL setting. */
public class PlatformAccountActivationConfigurationAdapter implements AccountActivationConfigurationPort {
    private static final String ACTIVATION_URL_KEY = "account.activation.url";

    private final SystemPlatformSettingsRepository settingsRepository;

    public PlatformAccountActivationConfigurationAdapter(SystemPlatformSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @Override
    public Optional<String> activationBaseUrl() {
        return Optional.ofNullable(settingsRepository.findPlatformConfigValues(List.of(ACTIVATION_URL_KEY))
                        .get(ACTIVATION_URL_KEY))
                .filter(StringUtils::hasText)
                .map(String::trim);
    }
}
