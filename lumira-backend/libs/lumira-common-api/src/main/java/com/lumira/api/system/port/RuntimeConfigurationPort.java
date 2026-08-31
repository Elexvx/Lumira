package com.lumira.api.system.port;

import com.lumira.api.system.MaintenanceLoginPolicyDTO;
import com.lumira.api.system.SecuritySettingsDTO;
import java.util.Map;

public interface RuntimeConfigurationPort {
    SecuritySettingsDTO securitySettings();
    Map<String, String> smtpRuntimeConfigValues();
    Map<String, String> wechatOfficialRuntimeConfigValues();
    MaintenanceLoginPolicyDTO maintenanceLoginPolicy();
}
