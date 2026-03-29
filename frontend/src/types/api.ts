export interface ApiResponse<T> {
  code: string;
  message: string;
  userMessage?: string;
  data: T;
  requestId?: string;
  timestamp: string;
  path?: string;
}

export interface TenantSummary {
  tenantId: number;
  tenantCode: string;
  tenantName: string;
  tenantShortName?: string;
  status: string;
}

export interface MyTenant extends TenantSummary {
  isDefault: boolean;
}

export interface AuthUser {
  userId: number;
  username: string;
  nickname?: string;
  realName?: string;
  avatarUrl?: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
  tenants: MyTenant[];
  currentTenant?: TenantSummary | null;
}

export interface RefreshTokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  sessionVersion?: number;
  permissionsVersion?: string;
}

export interface CurrentUser {
  userId: number;
  username: string;
  nickname?: string;
  realName?: string;
  avatarUrl?: string;
  currentTenant?: TenantSummary | null;
  sessionId: string;
  permissionsVersion?: string;
  sessionVersion?: number;
  permissions?: string[];
}

export interface CurrentTenantResponse {
  hasCurrentTenant: boolean;
  currentTenant?: TenantSummary | null;
}

export interface SwitchTenantResponse {
  currentTenant: TenantSummary;
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  sessionVersion?: number;
  permissionsVersion?: string;
}

export interface MenuNode {
  id: number;
  parentId?: number;
  menuCode: string;
  name: string;
  path: string;
  component?: string;
  icon?: string;
  permissionKey?: string;
  pluginCode?: string;
  sortNo?: number;
  children?: MenuNode[];
}

export interface PluginDefinition {
  pluginCode: string;
  pluginName: string;
  pluginType: string;
  description?: string;
  author?: string;
  pluginApiVersion: string;
  status: string;
  builtinFlag: number;
  sortNo: number;
}

export interface PluginVersion {
  pluginCode: string;
  version: string;
  installStatus: string;
  loadStatus: string;
  healthStatus: string;
  isActive: number;
  rollbackable: number;
  minPlatformVersion: string;
  frontendManifestPath?: string;
  validationReportJson?: string;
  installedAt?: string;
  createdAt?: string;
}

export interface PluginUploadResult {
  pluginCode: string;
  pluginName: string;
  version: string;
  installStatus: string;
  validationReportJson: string;
}

export interface PluginRuntimeLog {
  id: number;
  tenantId?: number;
  pluginCode: string;
  pluginVersion?: string;
  operationType: string;
  lifecycleStatus: string;
  resultStatus: string;
  detailMessage?: string;
  requestId?: string;
  traceId?: string;
  failureStack?: string;
  createdAt: string;
}

export interface TenantPlugin {
  pluginCode: string;
  pluginName: string;
  version: string;
  manifestPath: string;
  sharedDeps?: string[];
  routes?: string[];
  menus?: MenuNode[];
}

export interface PagedResult<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface AuditLogRecord {
  id: number;
  tenantId?: number | null;
  userId?: number | null;
  username?: string | null;
  loginType?: string;
  loginResult?: string;
  logType?: string;
  logResult?: string;
  moduleName?: string;
  actionName?: string;
  operationType?: string;
  failReason?: string | null;
  requestId?: string | null;
  traceId?: string | null;
  detailMessage?: string | null;
  loginIp?: string | null;
  userAgent?: string | null;
  createdAt: string;
}

export interface DashboardSummary {
  currentUser: CurrentUser;
  currentTenant: TenantSummary | null;
  tenantPlugins: TenantPlugin[];
  menuCount: number;
  permissionCount: number;
  recentLoginLogs: AuditLogRecord[];
  recentOperationLogs: AuditLogRecord[];
  shortcuts: Array<{
    title: string;
    description: string;
    path: string;
    permission?: string;
  }>;
}

export interface TenantOverview {
  currentTenant: TenantSummary | null;
  myTenants: MyTenant[];
  tenantPlugins: TenantPlugin[];
  switchHistory: AuditLogRecord[];
}

export interface ProfileSummary {
  currentUser: CurrentUser;
  currentTenant: TenantSummary | null;
  myTenants: MyTenant[];
  roleNames: string[];
  permissionCount: number;
  recentLoginLogs: AuditLogRecord[];
}

export interface UserRecord {
  id: number;
  username: string;
  mobile?: string | null;
  nickname?: string | null;
  realName?: string | null;
  avatarUrl?: string | null;
  status: string;
  tenantNames?: string[];
  roleNames?: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface UserDetail extends UserRecord {
  email?: string | null;
  currentTenantId?: number | null;
  roleIds?: number[];
  tenantIds?: number[];
}

export interface RoleRecord {
  id: number;
  tenantId: number;
  roleCode: string;
  roleName: string;
  roleType: string;
  permissionCount?: number;
  userCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface RoleDetail extends RoleRecord {
  permissionKeys: string[];
}

export interface PermissionRecord {
  permissionKey: string;
  permissionName: string;
  permissionGroup?: string;
  sourceType?: string;
}

export interface MenuRecord {
  id: number;
  tenantId: number;
  parentId?: number | null;
  menuCode: string;
  menuName: string;
  menuType: string;
  path?: string | null;
  component?: string | null;
  icon?: string | null;
  sortNo?: number;
  permissionKey?: string | null;
  status: string;
  children?: MenuRecord[];
}

export interface DictTypeRecord {
  id: number;
  tenantId?: number | null;
  dictCode: string;
  dictName: string;
  status: string;
  isSystem: number;
  remark?: string | null;
}

export interface DictItemRecord {
  id: number;
  dictTypeId: number;
  itemLabel: string;
  itemValue: string;
  sortNo: number;
  status: string;
  remark?: string | null;
}

export interface SystemConfigRecord {
  id: number;
  tenantId?: number | null;
  configKey: string;
  configName: string;
  configValue: string;
  configScope: string;
  isSystem: number;
  remark?: string | null;
}

export interface DashboardPagePayload {
  [key: string]: unknown;
}
