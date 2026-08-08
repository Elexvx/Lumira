package com.lumira.api.system;

import java.util.Map;

/** Read-only boundary for owner-managed platform setting defaults. */
public interface PlatformSettingDefaultsPort {
    Map<String, String> findEnabledDefaults(String groupCode);
}
