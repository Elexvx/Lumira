package com.lumira.saas.modules.system.menu.dto;

import jakarta.validation.constraints.NotNull;

public class MenuOrderItem {

    @NotNull
    private Long id;
    private Long parentId;
    @NotNull
    private Integer sortNo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
