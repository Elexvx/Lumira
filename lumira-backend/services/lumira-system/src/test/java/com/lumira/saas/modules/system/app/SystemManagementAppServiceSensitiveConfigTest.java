package com.lumira.saas.modules.system.app;

import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemManagementAppServiceSensitiveConfigTest {

    @Test
    void detectsWechatOfficialAppSecretConfigAsSensitive() {
        assertThat(SystemManagementAppService.isSensitiveConfigKey("notification.wechat-official.app-secret"))
                .isTrue();
    }

    @Test
    void masksSensitiveConfigValue() {
        SystemVO.ConfigVO config = new SystemVO.ConfigVO();
        config.setConfigKey("notification.wechat-official.app-secret");
        config.setConfigValue("wx-secret-VALIDATION-123");

        SystemManagementAppService.maskSensitiveConfigValue(config);

        assertThat(config.getConfigValue()).isEqualTo("******");
    }

    @Test
    void leavesNonSecretConfigValuesVisible() {
        SystemVO.ConfigVO config = new SystemVO.ConfigVO();
        config.setConfigKey("notification.wechat-official.app-id");
        config.setConfigValue("wx-app-id");

        SystemManagementAppService.maskSensitiveConfigValue(config);

        assertThat(SystemManagementAppService.isSensitiveConfigKey(config.getConfigKey())).isFalse();
        assertThat(config.getConfigValue()).isEqualTo("wx-app-id");
    }
}
