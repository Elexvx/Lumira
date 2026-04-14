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
  createdAt?: string;
  updatedAt?: string;
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
  mobile?: string | null;
  email?: string | null;
  birthMonth?: string | null;
  gender?: string | null;
  region?: string | null;
  availableTime?: string | null;
  idCardNumber?: string | null;
}

export interface SecondFactorLoginOption {
  pluginCode: string;
  pluginName: string;
  factorCode: string;
  factorName: string;
  challengeId: string;
  maskedContact?: string | null;
  promptMessage?: string | null;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
  tenants: MyTenant[];
  currentTenant?: TenantSummary | null;
  requiresSecondFactor?: boolean;
  secondFactorPluginCode?: string | null;
  secondFactorPluginName?: string | null;
  secondFactorChallengeId?: string | null;
  secondFactorOptions?: SecondFactorLoginOption[];
  requiresCaptcha?: boolean | null;
}

export interface LoginEncryptionKey {
  algorithm: string;
  keyId: string;
  publicKey: string;
}

export type CaptchaType = 'IMAGE';

export interface SecuritySettings {
  idleTimeoutSeconds: number;
  accessTokenExpireSeconds: number;
  refreshTokenExpireSeconds: number;
  allowMultiDeviceLogin: boolean;
  captchaEnabled: boolean;
  captchaType: CaptchaType;
  loginDefenseWindowMinutes: number;
  loginMaxValidationAttempts: number;
  loginMaxFailureCount: number;
  passwordMinLength: number;
  passwordRequireUppercase: boolean;
  passwordRequireLowercase: boolean;
  passwordRequireSpecialCharacter: boolean;
  passwordAllowConsecutiveCharacters: boolean;
}

export interface CaptchaChallenge {
  captchaId: string;
  captchaType: CaptchaType;
  imageUrl?: string | null;
  bgUrl?: string | null;
  puzzleUrl?: string | null;
  bgWidth?: number | null;
  bgHeight?: number | null;
  puzzleWidth?: number | null;
  puzzleHeight?: number | null;
  puzzleLeft?: number | null;
  puzzleTop?: number | null;
  expiresInSeconds?: number | null;
}

export interface CaptchaVerifyResult {
  captchaId: string;
  captchaProof: string;
  expiresInSeconds?: number | null;
}

export interface BrandingSettings {
  websiteName: string;
  websiteFaviconUrl?: string;
  websiteLogoUrl?: string;
  githubLinkUrl?: string;
  helpLinkUrl?: string;
  companyName?: string;
  copyrightStartYear?: number;
  footerIcp?: string;
  footerCopyright?: string;
}

export interface HealthResponse {
  status: string;
}

export interface AgreementSettings {
  userAgreementMarkdown: string;
  privacyAgreementMarkdown: string;
}

export interface WatermarkSettings {
  enabled: boolean;
  mode: 'TEXT' | 'IMAGE';
  textLines: string[];
  imageUrl?: string;
  fontColor: string;
  fontSize: number;
  fontWeight: string;
  rotate: number;
  gapX: number;
  gapY: number;
  offsetX: number;
  offsetY: number;
  zIndex: number;
  opacity: number;
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
  mobile?: string | null;
  email?: string | null;
  birthMonth?: string | null;
  gender?: string | null;
  region?: string | null;
  availableTime?: string | null;
  idCardNumber?: string | null;
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

export interface NotificationRecord {
  id: number;
  title: string;
  content: string;
  createdAt: string;
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

export interface SecondFactorProviderStatus {
  pluginCode: string;
  pluginName?: string | null;
  factorCode?: string | null;
  factorName?: string | null;
  enabled?: boolean | null;
  bound?: boolean | null;
  emailRequired?: boolean | null;
  maskedContact?: string | null;
  statusMessage?: string | null;
}

export interface SecondFactorChallenge {
  pluginCode: string;
  pluginName: string;
  factorCode: string;
  factorName: string;
  challengeId: string;
  maskedContact?: string | null;
  promptMessage?: string | null;
  setupUri?: string | null;
  setupSecret?: string | null;
  recoveryCodes?: string[];
}

export interface SecondFactorVerification {
  verified: boolean;
  tenantId?: number | null;
  userId?: number | null;
  username?: string | null;
  message?: string | null;
}

export interface SmtpSettings {
  host: string;
  port: number;
  username: string;
  password?: string;
  from: string;
  authEnabled: boolean;
  startTlsEnabled: boolean;
  sslEnabled: boolean;
  configured?: boolean;
}

export interface SmtpSettingsPayload {
  host?: string;
  port?: number;
  username?: string;
  password?: string;
  from?: string;
  authEnabled?: boolean;
  startTlsEnabled?: boolean;
  sslEnabled?: boolean;
}

export interface SmtpTestPayload {
  toEmail: string;
  subject?: string;
  content?: string;
}

export interface SmtpTestResult {
  success: boolean;
  message: string;
  toEmail: string;
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

export interface ServiceMonitorCpu {
  coreCount: number;
  processUsagePercent?: number | null;
  systemUsagePercent?: number | null;
  idlePercent?: number | null;
  loadAverage?: number | null;
}

export interface ServiceMonitorMemory {
  totalBytes: number;
  usedBytes: number;
  freeBytes: number;
  usagePercent?: number | null;
  heapMaxBytes?: number | null;
  heapUsedBytes: number;
  heapCommittedBytes: number;
  nonHeapUsedBytes: number;
}

export interface ServiceMonitorServer {
  serverName: string;
  serverIp: string;
  osName?: string | null;
  osArch?: string | null;
  osVersion?: string | null;
  projectPath?: string | null;
  installPath?: string | null;
  userHome?: string | null;
  tempDir?: string | null;
}

export interface ServiceMonitorJvm {
  vmName?: string | null;
  vmVersion?: string | null;
  vmVendor?: string | null;
  javaVersion?: string | null;
  javaHome?: string | null;
  pid?: number | null;
  startTime?: string | null;
  uptimeSeconds?: number | null;
  threadCount?: number | null;
  daemonThreadCount?: number | null;
  peakThreadCount?: number | null;
  inputArguments?: string[];
}

export interface ServiceMonitorSnapshot {
  cpu: ServiceMonitorCpu;
  memory: ServiceMonitorMemory;
  server: ServiceMonitorServer;
  jvm: ServiceMonitorJvm;
}

export interface RedisMonitorOverview {
  version?: string | null;
  mode?: string | null;
  port?: number | null;
  connectedClients?: number | null;
  uptimeSeconds?: number | null;
  uptimeDays?: number | null;
  keyCount?: number | null;
  totalConnectionsReceived?: number | null;
  totalCommandsProcessed?: number | null;
  instantaneousOpsPerSec?: number | null;
  memoryUsedBytes?: number | null;
  memoryPeakBytes?: number | null;
  memoryMaxBytes?: number | null;
  memoryUsagePercent?: number | null;
  hits?: number | null;
  misses?: number | null;
  hitRate?: number | null;
}

export interface RedisMonitorCommandStat {
  command: string;
  calls: number;
  totalUsec: number;
  avgUsec: number;
  rejectedCalls: number;
  failedCalls: number;
}

export interface RedisMonitorKeyspace {
  database: string;
  keys: number;
  expires: number;
  avgTtl: number;
}

export interface RedisMonitorClient {
  addressPort?: string | null;
  name?: string | null;
  age?: number | null;
  idle?: number | null;
  flags?: string | null;
  databaseId?: number | null;
  lastCommand?: string | null;
}

export interface RedisMonitorSnapshot {
  overview: RedisMonitorOverview;
  commandStats: RedisMonitorCommandStat[];
  keyspaces: RedisMonitorKeyspace[];
  clients: RedisMonitorClient[];
  sampleTime?: string | null;
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
  profileFieldSettings: ProfileFieldSetting[];
}

export interface ProfileFieldSetting {
  fieldKey: string;
  fieldLabel: string;
  fieldDescription?: string | null;
  visible: boolean;
}

export interface UserRecord {
  id: number;
  username: string;
  mobile?: string | null;
  idCardNumber?: string | null;
  nickname?: string | null;
  realName?: string | null;
  avatarUrl?: string | null;
  email?: string | null;
  birthMonth?: string | null;
  gender?: string | null;
  region?: string | null;
  availableTime?: string | null;
  status: string;
  tenantNames?: string[];
  roleNames?: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface UserDetail extends UserRecord {
  currentTenantId?: number | null;
  roleIds?: number[];
  tenantIds?: number[];
}

export interface OnlineSessionRecord {
  sessionId: string;
  userId: number;
  username: string;
  nickname?: string | null;
  realName?: string | null;
  currentTenantId?: number | null;
  loginTime?: string | null;
  lastActivityAt?: string | null;
  expireTime?: string | null;
  clientType?: string | null;
  loginIp?: string | null;
  userAgent?: string | null;
}

export interface OnlineSessionEventRecord {
  action: string;
  tenantId?: number | null;
  userId?: number | null;
  sessionId?: string | null;
  operatorUsername?: string | null;
  occurredAt?: string | null;
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

export interface PermissionActionRecord {
  permissionKey: string;
  permissionName: string;
  permissionGroup?: string;
  sourceType?: string;
}

export type PermissionTreeNodeType = 'CATALOG' | 'PAGE' | 'ALIAS';

export interface PermissionTreeRecord {
  nodeType: PermissionTreeNodeType;
  pageKey: string;
  pageName: string;
  routePath?: string;
  icon?: string;
  permissionKey?: string;
  permissionGroup?: string;
  sourceType?: string;
  selectable: boolean;
  children?: PermissionTreeRecord[];
  actionPermissions?: PermissionActionRecord[];
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
