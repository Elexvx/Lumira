package com.lumira.saas.modules.system.settings.repository;

import java.util.List;
import java.util.Map;

public interface SystemPlatformSettingsRepository {
    Map<String, String> findPlatformConfigValues(List<String> keys);
    Map<String, String> findEffectiveSettingValues(String groupCode);
    Map<String, String> findSettingDefaults(String groupCode);
    Map<String, String> findSettingResetValues(String groupCode);
    int upsertPlatformConfig(String key, String value, Long userId, String userUuid);
    String findEnabledUserUuid(Long userId);
}
