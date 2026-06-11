package com.lumira.saas.modules.system.dict.dto;

import jakarta.validation.constraints.NotBlank;

public class DictTypeUpsertRequest {

    @NotBlank
    private String dictCode;
    @NotBlank
    private String dictName;
    @NotBlank
    private String status;
    private String remark;

    public String getDictCode() { return dictCode; }
    public void setDictCode(String dictCode) { this.dictCode = dictCode; }
    public String getDictName() { return dictName; }
    public void setDictName(String dictName) { this.dictName = dictName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
