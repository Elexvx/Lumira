package com.lumira.api.system;

public record WechatLoginSettingsDTO(
        boolean enabled,
        String appId,
        String appSecret,
        String redirectUri,
        int stateExpireMinutes,
        boolean configured,
        boolean appSecretConfigured
) {
}
