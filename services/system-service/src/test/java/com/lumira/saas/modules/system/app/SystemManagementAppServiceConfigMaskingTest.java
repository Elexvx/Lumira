package com.lumira.saas.modules.system.app;

import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemManagementAppServiceConfigMaskingTest {

    @Test
    void shouldMaskWechatLoginAppSecretConfigValue() {
        SystemVO.ConfigVO config = new SystemVO.ConfigVO();
        config.setConfigKey("verification.wechat-login.app-secret");
        config.setConfigValue("wx_app_secret_PROOF_4b7b1e");

        SystemManagementAppService.maskSensitiveConfigValue(config);

        assertEquals("******", config.getConfigValue());
    }

    @Test
    void shouldKeepNonSensitiveConfigValueVisible() {
        SystemVO.ConfigVO config = new SystemVO.ConfigVO();
        config.setConfigKey("verification.wechat-login.app-id");
        config.setConfigValue("wx_app_id");

        SystemManagementAppService.maskSensitiveConfigValue(config);

        assertEquals("wx_app_id", config.getConfigValue());
    }

    @Test
    void shouldKeepEmptyWechatLoginAppSecretConfigValueEmpty() {
        SystemVO.ConfigVO config = new SystemVO.ConfigVO();
        config.setConfigKey("verification.wechat-login.app-secret");
        config.setConfigValue("");

        SystemManagementAppService.maskSensitiveConfigValue(config);

        assertEquals("", config.getConfigValue());
    }
}
