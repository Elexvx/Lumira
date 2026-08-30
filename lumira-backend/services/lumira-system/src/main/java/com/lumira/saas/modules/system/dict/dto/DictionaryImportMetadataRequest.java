package com.lumira.saas.modules.system.dict.dto;

import jakarta.validation.constraints.NotBlank;

public class DictionaryImportMetadataRequest {
    @NotBlank
    private String dictCode;
    @NotBlank
    private String dictName;
    private String status = "ENABLED";
    private String remark;
    private String structureType = "FLAT";
    private String expectedSha256;

    public String getDictCode() { return dictCode; }
    public void setDictCode(String dictCode) { this.dictCode = dictCode; }
    public String getDictName() { return dictName; }
    public void setDictName(String dictName) { this.dictName = dictName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getStructureType() { return structureType; }
    public void setStructureType(String structureType) { this.structureType = structureType; }
    public String getExpectedSha256() { return expectedSha256; }
    public void setExpectedSha256(String expectedSha256) { this.expectedSha256 = expectedSha256; }
}
