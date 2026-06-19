package com.lumira.saas.modules.system.menu.dto;

import jakarta.validation.constraints.NotBlank;

public class MenuUpsertRequest {

    private Long parentId;
    @NotBlank
    private String menuCode;
    @NotBlank
    private String menuName;
    @NotBlank
    private String menuType;
    private String path;
    private String component;
    private String icon;
    private Integer sortNo;
    private String permissionKey;
    @NotBlank
    private String status;

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getMenuCode() { return menuCode; }
    public void setMenuCode(String menuCode) { this.menuCode = menuCode; }
    public String getMenuName() { return menuName; }
    public void setMenuName(String menuName) { this.menuName = menuName; }
    public String getMenuType() { return menuType; }
    public void setMenuType(String menuType) { this.menuType = menuType; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public String getPermissionKey() { return permissionKey; }
    public void setPermissionKey(String permissionKey) { this.permissionKey = permissionKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
