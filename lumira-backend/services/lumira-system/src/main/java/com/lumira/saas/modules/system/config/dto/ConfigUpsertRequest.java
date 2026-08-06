package com.lumira.saas.modules.system.config.dto;

import jakarta.validation.constraints.NotBlank;

public class ConfigUpsertRequest {

    @NotBlank
    private String configKey;
    @NotBlank
    private String configName;
    @NotBlank
    private String configValue;
    private String remark;
    private Long expectedConfigVersion;
    private String changeReason;

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public String getConfigName() { return configName; }
    public void setConfigName(String configName) { this.configName = configName; }
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getExpectedConfigVersion() { return expectedConfigVersion; }
    public void setExpectedConfigVersion(Long expectedConfigVersion) { this.expectedConfigVersion = expectedConfigVersion; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
}
