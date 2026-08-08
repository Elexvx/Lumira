package com.lumira.saas.modules.system.integration;

import com.lumira.api.system.PlatformSettingDefaultsPort;
import com.lumira.saas.modules.system.settings.repository.SystemPlatformSettingsRepository;
import java.util.Map;
import org.springframework.util.StringUtils;

/** System-owned adapter for consumers that need immutable platform setting defaults. */
public class PlatformSettingDefaultsPortAdapter implements PlatformSettingDefaultsPort {
    private final SystemPlatformSettingsRepository settingsRepository;

    public PlatformSettingDefaultsPortAdapter(SystemPlatformSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @Override
    public Map<String, String> findEnabledDefaults(String groupCode) {
        if (!StringUtils.hasText(groupCode)) {
            return Map.of();
        }
        return settingsRepository.findSettingDefaults(groupCode);
    }
}
