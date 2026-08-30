package com.lumira.saas.modules.system.dict.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class DictItemUpsertRequest {

    @NotBlank
    private String itemLabel;
    @NotBlank
    private String itemValue;
    @NotNull
    private Integer sortNo;
    @NotBlank
    private String status;
    private String remark;
    private String parentItemValue;
    private Integer levelNo;
    private Boolean leaf;

    public String getItemLabel() { return itemLabel; }
    public void setItemLabel(String itemLabel) { this.itemLabel = itemLabel; }
    public String getItemValue() { return itemValue; }
    public void setItemValue(String itemValue) { this.itemValue = itemValue; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getParentItemValue() { return parentItemValue; }
    public void setParentItemValue(String parentItemValue) { this.parentItemValue = parentItemValue; }
    public Integer getLevelNo() { return levelNo; }
    public void setLevelNo(Integer levelNo) { this.levelNo = levelNo; }
    public Boolean getLeaf() { return leaf; }
    public void setLeaf(Boolean leaf) { this.leaf = leaf; }
}
