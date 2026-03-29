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
