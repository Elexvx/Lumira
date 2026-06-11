package com.lumira.saas.modules.system.dashboard.vo;

import com.lumira.saas.modules.auth.vo.CurrentUserVO;
import com.lumira.saas.modules.plugin.vo.PluginVO;
import com.lumira.saas.modules.system.audit.vo.AuditLogVO;

import java.util.List;

public class DashboardSummaryVO {

    private CurrentUserVO currentUser;
    private List<PluginVO.TenantPluginVO> tenantPlugins;
    private Integer menuCount;
    private Integer permissionCount;
    private List<AuditLogVO> recentLoginLogs;
    private List<AuditLogVO> recentOperationLogs;
    private List<ShortcutVO> shortcuts;

    public CurrentUserVO getCurrentUser() { return currentUser; }
    public void setCurrentUser(CurrentUserVO currentUser) { this.currentUser = currentUser; }
    public List<PluginVO.TenantPluginVO> getTenantPlugins() { return tenantPlugins; }
    public void setTenantPlugins(List<PluginVO.TenantPluginVO> tenantPlugins) { this.tenantPlugins = tenantPlugins; }
    public Integer getMenuCount() { return menuCount; }
    public void setMenuCount(Integer menuCount) { this.menuCount = menuCount; }
    public Integer getPermissionCount() { return permissionCount; }
    public void setPermissionCount(Integer permissionCount) { this.permissionCount = permissionCount; }
    public List<AuditLogVO> getRecentLoginLogs() { return recentLoginLogs; }
    public void setRecentLoginLogs(List<AuditLogVO> recentLoginLogs) { this.recentLoginLogs = recentLoginLogs; }
    public List<AuditLogVO> getRecentOperationLogs() { return recentOperationLogs; }
    public void setRecentOperationLogs(List<AuditLogVO> recentOperationLogs) { this.recentOperationLogs = recentOperationLogs; }
    public List<ShortcutVO> getShortcuts() { return shortcuts; }
    public void setShortcuts(List<ShortcutVO> shortcuts) { this.shortcuts = shortcuts; }
}
