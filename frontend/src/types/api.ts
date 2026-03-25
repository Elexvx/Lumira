export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
  requestId?: string;
  timestamp: string;
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
