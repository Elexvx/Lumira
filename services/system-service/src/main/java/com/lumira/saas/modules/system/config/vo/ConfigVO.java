package com.lumira.saas.modules.system.config.vo;

public class ConfigVO {

    private Long id;
    private Long tenantId;
    private String configKey;
    private String configName;
    private String configValue;
    private String configScope;
    private Integer isSystem;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public String getConfigName() { return configName; }
    public void setConfigName(String configName) { this.configName = configName; }
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
    public String getConfigScope() { return configScope; }
    public void setConfigScope(String configScope) { this.configScope = configScope; }
    public Integer getIsSystem() { return isSystem; }
    public void setIsSystem(Integer isSystem) { this.isSystem = isSystem; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
