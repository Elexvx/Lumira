package com.lumira.saas.modules.system.dict.vo;

public class DictItemVO {

    private Long id;
    private Long dictTypeId;
    private String itemLabel;
    private String itemValue;
    private Integer sortNo;
    private String status;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDictTypeId() { return dictTypeId; }
    public void setDictTypeId(Long dictTypeId) { this.dictTypeId = dictTypeId; }
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
}
