package com.lumira.saas.modules.system.config.dto;

import jakarta.validation.constraints.NotBlank;

public class ConfigUpsertRequest {

    @NotBlank
    private String configKey;
    @NotBlank
    private String configName;
    @NotBlank
    private String configValue;
    @NotBlank
    private String configScope;
    private String remark;

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public String getConfigName() { return configName; }
    public void setConfigName(String configName) { this.configName = configName; }
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
    public String getConfigScope() { return configScope; }
    public void setConfigScope(String configScope) { this.configScope = configScope; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
