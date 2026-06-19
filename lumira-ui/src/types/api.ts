export interface ApiResponse<T> {
  code: string;
  message: string;
  userMessage?: string;
  data?: T;
  requestId?: string;
  timestamp: string;
  path?: string;
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
  extraProfileValues?: Record<string, string> | null;
  locale?: string | null;
  sessionId?: string | null;
  permissionsVersion?: string;
  sessionVersion?: number;
  permissions?: string[];
}

export interface SecondFactorLoginOption {
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
  requiresSecondFactor?: boolean;
  secondFactorOptions?: SecondFactorLoginOption[];
  requiresCaptcha?: boolean | null;
  requiresPasswordChange?: boolean | null;
}

export interface LoginCapabilities {
  passwordLoginAvailable: boolean;
  smsLoginAvailable: boolean;
  emailLoginAvailable: boolean;
  wechatLoginAvailable?: boolean;
  passkeyLoginAvailable?: boolean;
  passkeyPasswordlessAvailable?: boolean;
  loginModeOrder?: string[];
}

export interface PasskeyOptions {
  challengeId: string;
  publicKey: Record<string, unknown>;
}

export interface PasskeyCredentialRecord {
  id: number;
  tenantId: number;
  userId: number;
  username?: string;
  credentialId: string;
  transports?: string;
  backupEligible?: boolean;
  backupState?: boolean;
  label?: string;
  createdAt?: string;
  lastUsedAt?: string | null;
}

export interface PasskeySettings {
  enabled: boolean;
  passwordlessEnabled: boolean;
  selfBindingEnabled: boolean;
  rpId: string;
  rpName: string;
  allowedOrigins: string[];
  challengeTtlSeconds: number;
}

export interface LoginCodeChallenge {
  loginType: 'sms' | 'email' | 'totp';
  factorName: string;
  challengeId: string;
  maskedContact?: string | null;
  promptMessage?: string | null;
  expiresInSeconds?: number | null;
  cooldownSeconds?: number | null;
  debugCode?: string | null;
}

export interface LoginEncryptionKey {
  algorithm: string;
  keyId: string;
  publicKey: string;
}

export interface SessionBootstrapPayload {
  currentUser: CurrentUser;
  securitySettings: SecuritySettings;
}

export interface WechatAuthorizeUrl {
  authorizeUrl: string;
  state: string;
}

export type CaptchaType = 'IMAGE' | 'SLIDER';

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
  verificationCodeExpireSeconds: number;
  verificationCodeCooldownSeconds: number;
  passwordMinLength: number;
  passwordRequireUppercase: boolean;
  passwordRequireLowercase: boolean;
  passwordRequireSpecialCharacter: boolean;
  passwordAllowConsecutiveCharacters: boolean;
}

export type PaymentProviderCode = 'alipay' | 'wechat_pay' | 'stripe' | 'paypal';

export interface PaymentProviderSettings {
  providerCode: PaymentProviderCode | string;
  providerName: string;
  enabled: boolean;
  configured: boolean;
  persisted?: boolean;
  environment: string;
  configuredFields: string[];
  lastTestedAt?: string | null;
  lastTestSuccess?: boolean | null;
  lastTestMessage?: string | null;
  appId?: string | null;
  merchantId?: string | null;
  merchantSerialNo?: string | null;
  platformCertSerialNo?: string | null;
  apiV3Key?: string | null;
  clientId?: string | null;
  clientSecret?: string | null;
  publishableKey?: string | null;
  secretKey?: string | null;
  privateKey?: string | null;
  publicKey?: string | null;
  apiBaseUrl?: string | null;
  notifyUrl?: string | null;
  returnUrl?: string | null;
  refundNotifyUrl?: string | null;
  successUrl?: string | null;
  cancelUrl?: string | null;
  webhookSecret?: string | null;
  webhookId?: string | null;
  currency?: string | null;
  extraConfig?: string | null;
  sandboxEnabled?: boolean | null;
}

export interface PaymentProviderTestResult {
  providerCode: string;
  providerName: string;
  success: boolean;
  message: string;
  checkedAt: string;
}

export interface PaymentCreateOrderRequest {
  providerCode: PaymentProviderCode | string;
  orderNo: string;
  subject: string;
  amountMinor: number;
  currency: string;
  clientIp?: string | null;
  notifyUrl?: string | null;
  returnUrl?: string | null;
  metadata?: Record<string, unknown> | null;
  idempotencyKey?: string | null;
}

export interface PaymentOrderRecord {
  orderNo: string;
  providerCode: string;
  providerOrderNo: string;
  subject: string;
  amountMinor: number;
  currency: string;
  status: string;
  paymentUrl?: string | null;
  clientIp?: string | null;
  notifyUrl?: string | null;
  returnUrl?: string | null;
  metadata?: Record<string, unknown>;
  failureCode?: string | null;
  failureMessage?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  paidAt?: string | null;
}

export interface PaymentCreateRefundRequest {
  refundNo: string;
  amountMinor: number;
  currency: string;
  reason: string;
  metadata?: Record<string, unknown> | null;
  idempotencyKey?: string | null;
}

export interface PaymentRefundRecord {
  refundNo: string;
  orderNo: string;
  providerCode: string;
  providerRefundNo: string;
  amountMinor: number;
  currency: string;
  status: string;
  reason: string;
  metadata?: Record<string, unknown>;
  failureCode?: string | null;
  failureMessage?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  refundedAt?: string | null;
}

export interface PaymentWebhookEventRecord {
  providerCode: string;
  eventId: string;
  eventType: string;
  signatureValid: boolean;
  processed: boolean;
  processMessage?: string | null;
  receivedAt?: string | null;
  processedAt?: string | null;
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
  loginBackgroundUrl?: string;
  githubLinkEnabled?: boolean;
  githubLinkUrl?: string;
  helpLinkEnabled?: boolean;
  helpLinkUrl?: string;
  companyName?: string;
  copyrightStartYear?: number;
  footerIcp?: string;
  footerPoliceBeian?: string;
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

export interface FloatingWindowSettings {
  apiDocsQrEnabled: boolean;
  apiDocsQrTitle: string;
  apiDocsQrImageUrl?: string;
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
  extraProfileValues?: Record<string, string> | null;
  locale?: string | null;
  simulatedRoleId?: number | null;
  availableRoles?: CurrentUserRoleOption[];
  sessionId: string;
  permissionsVersion?: string;
  sessionVersion?: number;
  permissions?: string[];
  roleIds?: number[];
  primaryDeptId?: number | null;
  deptIds?: number[];
  descendantDeptIds?: number[];
  dataScopes?: RoleDataScope[];
  requiresPasswordChange?: boolean | null;
  defaultHomePath?: string | null;
}

export interface CurrentUserRoleOption {
  id: number;
  roleCode: string;
  roleName: string;
  roleType: string;
  permissionCount?: number;
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

export interface AiEmployeeRecord {
  id: number;
  tenantId?: number | null;
  username: string;
  nickname: string;
  position?: string | null;
  avatarKey?: string | null;
  description?: string | null;
  greeting?: string | null;
  systemPrompt?: string | null;
  defaultLlmServiceId?: number | null;
  defaultLlmServiceTitle?: string | null;
  enabled?: boolean | null;
  sortOrder?: number | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface AiEmployeeDetailRecord extends AiEmployeeRecord {
  defaultSystemPromptTemplate?: string | null;
}

export type AiEmployeeCapabilityMode = 'visit' | 'allow' | 'deny';

export interface AiEmployeeCapabilityRecord {
  capabilityCode: string;
  capabilityName: string;
  category?: string | null;
  description?: string | null;
  riskLevel?: string | null;
  readOnly?: boolean | null;
  needConfirm?: boolean | null;
  permissionMode: AiEmployeeCapabilityMode;
}

export interface AiGovernanceOverviewRecord {
  employeeCount: number;
  enabledEmployeeCount: number;
  llmServiceCount: number;
  enabledLlmServiceCount: number;
  missingApiKeyServiceCount: number;
  skillCount: number;
  highRiskSkillCount: number;
  highRiskAllowedBindingCount: number;
  confirmationRequiredSkillCount: number;
  sampledAt?: string | null;
}

export interface AiConversationRecord {
  id: number;
  tenantId?: number | null;
  employeeId?: number | null;
  employeeName?: string | null;
  conversationCode: string;
  title?: string | null;
  preview?: string | null;
  status?: string | null;
  pinned?: boolean | null;
  isPinned?: boolean | null;
  latestMessageAt?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface AiConversationAttachmentRecord {
  id: number;
  fileId: number;
  originalFileName: string;
  fileExtension?: string | null;
  mimeType?: string | null;
  fileSizeBytes?: number | null;
  fileSizeLabel?: string | null;
  publicUrl?: string | null;
  previewUrl?: string | null;
  downloadUrl?: string | null;
  previewMode?: string | null;
}

export interface AiConversationMessageRecord {
  id: number;
  conversationId: number;
  role: string;
  content: string;
  attachments?: AiConversationAttachmentRecord[] | null;
  createTime?: string | null;
}

export interface AiConversationShareRecord {
  shareToken: string;
  conversationId: number;
  shareTitle?: string | null;
  expiresAt?: string | null;
  createTime?: string | null;
}

export interface AiConversationShareDetailRecord {
  share: AiConversationShareRecord;
  conversation: AiConversationRecord;
  messages: AiConversationMessageRecord[];
}

export interface AiConversationExportRecord {
  conversationId: number;
  title: string;
  format: 'markdown' | 'text';
  fileName: string;
  mimeType: string;
  content: string;
}

export interface AiKnowledgeBaseRecord {
  id: number;
  tenantId?: number | null;
  kbCode?: string | null;
  name: string;
  description?: string | null;
  status: string;
  visibilityScope?: string | null;
  ownerUserId?: number | null;
  documentCount?: number | null;
  chunkCount?: number | null;
  createdBy?: number | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface AiKnowledgeDocumentRecord {
  id: number;
  tenantId?: number | null;
  knowledgeBaseId: number;
  fileId?: number | null;
  title: string;
  originalFileName?: string | null;
  fileExtension?: string | null;
  mimeType?: string | null;
  fileSizeBytes?: number | null;
  status: string;
  parseError?: string | null;
  extractedCharCount?: number | null;
  chunkCount?: number | null;
  createdBy?: number | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface AiKnowledgeReferenceRecord {
  chunkId: number;
  knowledgeBaseId: number;
  knowledgeBaseName?: string | null;
  documentId: number;
  documentTitle?: string | null;
  fileId?: number | null;
  originalFileName?: string | null;
  chunkIndex?: number | null;
  content: string;
}

export interface AiLlmServiceRecord {
  id: number;
  tenantId?: number | null;
  provider: string;
  code: string;
  title: string;
  baseUrl?: string | null;
  defaultModel?: string | null;
  enabled?: boolean | null;
  timeoutMs?: number | null;
  temperature?: number | null;
  maxTokens?: number | null;
  apiKeyConfigured?: boolean | null;
  apiKeyMasked?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface AiPromptTemplateRecord {
  defaultSystemPromptTemplate: string;
}

export interface AiChatResponseRecord {
  conversationId?: number | null;
  conversationCode?: string | null;
  employeeId?: number | null;
  replyText: string;
  thinkingContent?: string | null;
  replyRole?: string | null;
  provider?: string | null;
  model?: string | null;
  references?: AiKnowledgeReferenceRecord[] | null;
  toolPlan?: AiToolPlanRecord | null;
  toolResult?: AiToolExecuteResultRecord | null;
  replyAt?: string | null;
}

export interface AiEmployeeUpsertPayload {
  username: string;
  nickname: string;
  position?: string | null;
  avatarKey?: string | null;
  description?: string | null;
  greeting?: string | null;
  systemPrompt?: string | null;
  defaultLlmServiceId?: number | null;
  sortOrder?: number | null;
}

export interface AiLlmServiceUpsertPayload {
  provider: string;
  code: string;
  title: string;
  baseUrl?: string | null;
  apiKey?: string | null;
  defaultModel?: string | null;
  enabled?: boolean | null;
  timeoutMs?: number | null;
  temperature?: number | null;
  maxTokens?: number | null;
}

export interface AiLlmServiceTestPayload {
  serviceId?: number | null;
  provider?: string | null;
  code?: string | null;
  title?: string | null;
  baseUrl?: string | null;
  apiKey?: string | null;
  defaultModel?: string | null;
  timeoutMs?: number | null;
  temperature?: number | null;
  maxTokens?: number | null;
}

export interface AiLlmServiceTestResult {
  success?: boolean | null;
  message?: string | null;
  provider?: string | null;
  model?: string | null;
  latencyMs?: number | null;
  replyText?: string | null;
}

export interface AiChatRequestPayload {
  employeeId?: number | null;
  employeeIds?: number[] | null;
  conversationId?: number | null;
  pendingToolCallId?: number | null;
  message: string;
  enableThinking?: boolean | null;
  attachments?: Array<{
    fileId: number;
  }> | null;
  skillCodes?: string[];
  knowledgeBaseIds?: number[];
  confirmed?: boolean | null;
}

export interface AiToolPlanRecord {
  id: number;
  tenantId?: number | null;
  conversationId?: number | null;
  employeeId?: number | null;
  toolCode: string;
  toolName?: string | null;
  actionType?: string | null;
  riskLevel?: string | null;
  summary?: string | null;
  permissionKey?: string | null;
  requiresConfirm?: boolean | null;
  supervisorVerdict?: string | null;
  supervisorMessage?: string | null;
  policyVerdict?: string | null;
  policyMessage?: string | null;
  status?: string | null;
  arguments?: Record<string, unknown> | null;
  expiresAt?: string | null;
  createTime?: string | null;
}

export interface AiToolExecuteResultRecord {
  toolCode: string;
  resultStatus: string;
  message?: string | null;
  data?: Record<string, unknown> | null;
  executedAt?: string | null;
}

export interface AiToolPolicyRecord {
  id: number;
  tenantId?: number | null;
  policyName: string;
  toolCode?: string | null;
  actionType?: string | null;
  riskLevel?: string | null;
  matchType?: string | null;
  matchValue?: string | null;
  verdict?: string | null;
  message?: string | null;
  enabled?: boolean | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface LocalizationLanguage {
  id: number;
  localeCode: string;
  languageName: string;
  nativeName?: string | null;
  fallbackLocale?: string | null;
  sortNo?: number | null;
  status: string;
  defaultLanguage?: boolean | null;
  entryCount?: number | null;
  translatedCount?: number | null;
  coverageRate?: string | number | null;
  publishedVersion?: number | null;
  lastPublishedAt?: string | null;
}

export interface LocalizationNamespace {
  id: number;
  namespaceCode: string;
  namespaceName: string;
  sourceType?: string | null;
  sourceRef?: string | null;
  sortNo?: number | null;
  status: string;
  entryCount?: number | null;
  translatedCount?: number | null;
  coverageRate?: string | number | null;
}

export interface LocalizationEntry {
  id: number;
  namespaceCode: string;
  namespaceName: string;
  messageKey: string;
  defaultMessage: string;
  sourceLocale: string;
  sourceType?: string | null;
  sourceRef?: string | null;
  status: string;
  translationStatus: 'TRANSLATED' | 'PENDING';
  currentTranslation?: string | null;
  usageCount?: number | null;
  translations?: Record<string, string>;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface LocalizationRelease {
  id: number;
  localeCode: string;
  releaseVersion: number;
  fallbackLocale?: string | null;
  note?: string | null;
  active?: boolean | null;
  publishedBy?: number | null;
  publishedAt?: string | null;
}

export interface LocalizationRuntimeBundle {
  localeCode: string;
  fallbackLocale?: string | null;
  releaseVersion: number;
  messages: Record<string, string>;
}

export interface LocalizationSyncResult {
  languageCount: number;
  namespaceCount: number;
  entryCount: number;
  translationCount: number;
  usageCount: number;
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
  schemaMode?: string;
  supportsHotDisable?: boolean;
  supportsDataPurge?: boolean;
  runtimeContributions?: string[];
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
  lifecycleStatus?: string;
  schemaStatus?: string;
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

export interface PluginRuntimeSecurityPolicy {
  maxGatewayBodyBytes: number;
  requireHttpPermission: boolean;
  allowedMethods: string[];
  blockedHeaders: string[];
}

export interface TenantPlugin {
  pluginCode: string;
  pluginName: string;
  version: string;
  manifestPath: string;
  sharedDeps?: string[];
  routes?: string[];
  menus?: MenuNode[];
  lifecycleStatus?: string;
  schemaStatus?: string;
  supportsHotDisable?: boolean;
  supportsDataPurge?: boolean;
  runtimeContributions?: string[];
}

export interface PluginStatus {
  pluginCode: string;
  pluginName: string;
  version?: string;
  tenantEnabled: boolean;
  lifecycleStatus: string;
  schemaStatus: string;
  builtin?: boolean;
  schemaMode?: string;
  supportsHotDisable?: boolean;
  supportsDataPurge?: boolean;
  runtimeContributions?: string[];
}

export interface SensitiveWordRecord {
  id: number;
  tenantId: number;
  word: string;
  normalizedWord: string;
  category?: string | null;
  severity?: string | null;
  enabled: boolean;
  createdBy?: number | null;
  createdAt?: string | null;
  updatedBy?: number | null;
  updatedAt?: string | null;
}

export interface SensitiveWordImportResult {
  total: number;
  imported: number;
  duplicated: number;
  invalid: number;
}

export interface SensitiveWordMatchItem {
  fieldPath?: string | null;
  word: string;
  maskedWord: string;
}

export interface SensitiveWordCheckResult {
  hit: boolean;
  matches: SensitiveWordMatchItem[];
}

export interface NotificationRecord {
  id: number;
  title: string;
  content: string;
  createdAt: string;
}

export type MessageNoticeType = 'MESSAGE';
export type MessageTargetScope = 'TENANT' | 'USER' | 'ROLE';
export type MessagePublishStatus = 'PUBLISHED' | 'RETRACTED';
export type MessageSourceType = 'MANUAL';
export type MessageChannel = 'INBOX' | 'EMAIL' | 'WECHAT_OFFICIAL';
export type MessageSendStatus = 'SUCCESS' | 'FAILED' | 'SKIPPED';

export interface MessageNoticeRecord {
  id: number;
  tenantId: number;
  messageType: MessageNoticeType;
  targetScope: MessageTargetScope;
  targetUserId?: number | null;
  targetUserName?: string | null;
  targetRoleId?: number | null;
  targetRoleName?: string | null;
  title: string;
  content: string;
  sourceType: MessageSourceType;
  publishStatus: MessagePublishStatus;
  publishedAt?: string;
  createdBy?: number | null;
  updatedBy?: number | null;
  createdAt: string;
  updatedAt?: string;
  readFlag?: boolean;
  readAt?: string | null;
}

export interface MessageUnreadCount {
  unreadCount: number;
}

export interface MessageWebSocketTenantRuntime {
  tenantId: number;
  connectionCount: number;
}

export interface MessageWebSocketUserRuntime {
  userId: number;
  connectionCount: number;
}

export interface MessageWebSocketRuntime {
  activeConnections: number;
  tenantCount: number;
  userCount: number;
  earliestConnectedAt?: string | null;
  sampledAt?: string | null;
  tenants?: MessageWebSocketTenantRuntime[];
  topUsers?: MessageWebSocketUserRuntime[];
}

export interface MessageDeliveryLogRecord {
  id: number;
  tenantId: number;
  noticeId?: number | null;
  channel: MessageChannel;
  targetScope: MessageTargetScope;
  targetUserId?: number | null;
  targetUserName?: string | null;
  targetEmail?: string | null;
  title: string;
  content: string;
  sendStatus: MessageSendStatus;
  errorMessage?: string | null;
  sentAt?: string | null;
  createdBy?: number | null;
  createdAt: string;
}

export interface PagedResult<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
  hasMore?: boolean;
  totalCapped?: boolean;
  nextCursorId?: number | null;
  nextCursorCreatedAt?: string | null;
}

export type FilePreviewMode = 'IMAGE' | 'PDF' | 'TEXT' | 'UNSUPPORTED';

export interface FileObjectRecord {
  id: number;
  tenantId?: number | null;
  uploadedBy?: number | null;
  uploadedByName?: string | null;
  originalFileName: string;
  storedFileName?: string | null;
  storageType?: string | null;
  bucket?: string | null;
  fileExtension: string;
  mimeType?: string | null;
  fileSizeBytes: number;
  fileSizeLabel?: string | null;
  storagePath?: string | null;
  publicUrl: string;
  previewUrl?: string | null;
  downloadUrl?: string | null;
  previewMode: FilePreviewMode;
  previewable?: boolean | null;
  category?: string | null;
  tags?: string | null;
  remark?: string | null;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

export type FileStorageProvider = 'LOCAL' | 'ALIYUN_OSS' | 'TENCENT_COS';
export type FileRenameStrategy = 'APPEND_RANDOM_ID' | 'RANDOM_STRING' | 'KEEP_ORIGINAL';

export interface FileStorageSpaceRecord {
  id: number;
  tenantId?: number | null;
  title: string;
  storageKey: string;
  provider: FileStorageProvider;
  rootPath?: string | null;
  bucketName?: string | null;
  endpoint?: string | null;
  region?: string | null;
  accessKeyId?: string | null;
  secretConfigured?: boolean | null;
  renameStrategy: FileRenameStrategy;
  maxFileSizeMb: number;
  allowedMimeTypes: string;
  defaultStorage?: boolean | null;
  retainFileOnRecordDelete?: boolean | null;
  anonymousAccessAllowed?: boolean | null;
  status: 'ENABLED' | 'DISABLED';
  fileCount?: number | null;
  totalSizeBytes?: number | null;
  totalSizeLabel?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface FileStorageSpacePayload {
  title: string;
  storageKey?: string;
  provider: FileStorageProvider;
  rootPath?: string;
  bucketName?: string;
  endpoint?: string;
  region?: string;
  accessKeyId?: string;
  accessKeySecret?: string;
  renameStrategy: FileRenameStrategy;
  maxFileSizeMb: number;
  allowedMimeTypes: string;
  defaultStorage?: boolean;
  retainFileOnRecordDelete?: boolean;
  anonymousAccessAllowed?: boolean;
  status?: 'ENABLED' | 'DISABLED';
}

export interface FileStorageSpaceTestResult {
  provider: FileStorageProvider;
  status: 'UP' | 'DOWN';
  message?: string | null;
  responseTimeMs?: number | null;
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
  resultStatus?: string;
  moduleName?: string;
  actionName?: string;
  operationType?: string;
  failReason?: string | null;
  requestId?: string | null;
  traceId?: string | null;
  detailMessage?: string | null;
  loginIp?: string | null;
  userAgent?: string | null;
  conversationId?: number | null;
  employeeId?: number | null;
  skillCode?: string | null;
  toolName?: string | null;
  permissionMode?: string | null;
  confirmRequired?: number | null;
  confirmResult?: number | null;
  requestPayloadJson?: string | null;
  responsePayloadJson?: string | null;
  createdAt: string;
}

export interface SecondFactorProviderStatus {
  factorCode: string;
  factorName: string;
  systemEnabled?: boolean | null;
  enabled?: boolean | null;
  bound?: boolean | null;
  emailRequired?: boolean | null;
  mobileRequired?: boolean | null;
  maskedContact?: string | null;
  statusMessage?: string | null;
}

export interface SecondFactorChallenge {
  factorCode: string;
  factorName: string;
  challengeId: string;
  maskedContact?: string | null;
  promptMessage?: string | null;
  setupUri?: string | null;
  setupSecret?: string | null;
  recoveryCodes?: string[];
  debugCode?: string | null;
}

export interface SecondFactorVerification {
  verified: boolean;
  tenantId?: number | null;
  userId?: number | null;
  username?: string | null;
  message?: string | null;
}

export interface SmtpSettings {
  enabled?: boolean;
  host: string;
  port: number;
  username: string;
  password?: string;
  from: string;
  authEnabled: boolean;
  startTlsEnabled: boolean;
  sslEnabled: boolean;
  configured?: boolean;
  passwordConfigured?: boolean;
}

export interface SmtpSettingsPayload {
  enabled?: boolean;
  host?: string;
  port?: number;
  username?: string;
  password?: string;
  from?: string;
  authEnabled?: boolean;
  startTlsEnabled?: boolean;
  sslEnabled?: boolean;
}

export interface WechatOfficialAccountSettings {
  enabled: boolean;
  appId: string;
  appSecret?: string;
  templateId: string;
  detailUrl: string;
  configured?: boolean;
  appSecretConfigured?: boolean;
}

export interface WechatOfficialAccountSettingsPayload {
  enabled?: boolean;
  appId?: string;
  appSecret?: string;
  templateId?: string;
  detailUrl?: string;
}

export interface SmsVerificationSettings {
  enabled: boolean;
  provider: string;
  signName: string;
  templateCode: string;
  accessKeyId: string;
  accessKeySecret?: string;
  endpoint: string;
  region: string;
  configured?: boolean;
  accessKeySecretConfigured?: boolean;
}

export interface SmsVerificationSettingsPayload {
  enabled?: boolean;
  provider?: string;
  signName?: string;
  templateCode?: string;
  accessKeyId?: string;
  accessKeySecret?: string;
  endpoint?: string;
  region?: string;
}

export interface WechatLoginSettings {
  enabled: boolean;
  appId: string;
  appSecret?: string;
  redirectUri: string;
  stateExpireMinutes: number;
  configured?: boolean;
  appSecretConfigured?: boolean;
}

export interface WechatLoginSettingsPayload {
  enabled?: boolean;
  appId?: string;
  appSecret?: string;
  redirectUri?: string;
  stateExpireMinutes?: number;
}

export interface VerificationSettings {
  enabled: boolean;
  emailLoginEnabled: boolean;
  passwordLoginEnabled?: boolean;
  loginModeOrder?: string[];
}

export interface VerificationSettingsPayload {
  enabled?: boolean;
  emailLoginEnabled?: boolean;
  passwordLoginEnabled?: boolean;
  loginModeOrder?: string[];
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
  hostTotalBytes?: number | null;
  hostUsedBytes?: number | null;
  hostFreeBytes?: number | null;
  hostUsagePercent?: number | null;
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

export interface ServiceInstanceStatus {
  serviceName: string;
  baseUrl: string;
  healthUrl: string;
  status: string;
  responseTimeMs?: number | null;
  version?: string | null;
  checkedAt?: string | null;
  errorMessage?: string | null;
}

export interface ServiceApiDocStatus {
  serviceName: string;
  url: string;
  status: string;
}

export interface ServiceMonitorSnapshot {
  cpu: ServiceMonitorCpu;
  memory: ServiceMonitorMemory;
  server: ServiceMonitorServer;
  jvm: ServiceMonitorJvm;
  services?: ServiceInstanceStatus[];
  apiDocs?: ServiceApiDocStatus[];
}

export interface PlatformUpdateCurrentVersion {
  version?: string | null;
  commitId?: string | null;
  branch?: string | null;
  buildTime?: string | null;
}

export interface PlatformUpdateLatestVersion {
  version?: string | null;
  commitId?: string | null;
  branch?: string | null;
  releasedAt?: string | null;
  title?: string | null;
  url?: string | null;
  serverImage?: string | null;
  frontendImage?: string | null;
  migrationRequired?: boolean | null;
  rollbackSupported?: boolean | null;
}

export interface PlatformUpdateManifest {
  app?: string | null;
  channel?: string | null;
  minVersion?: string | null;
  serverImage?: string | null;
  frontendImage?: string | null;
  migrationRequired?: boolean | null;
  rollbackSupported?: boolean | null;
  releaseNotes?: string | null;
}

export interface PlatformUpdateTask {
  id: number;
  taskType?: string | null;
  status?: string | null;
  targetVersion?: string | null;
  targetCommit?: string | null;
  serverImage?: string | null;
  frontendImage?: string | null;
  updaterTaskId?: string | null;
  backupPath?: string | null;
  logSummary?: string | null;
  errorMessage?: string | null;
  createdBy?: number | null;
  createdByName?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface PlatformUpdateStatus {
  current?: PlatformUpdateCurrentVersion | null;
  latest?: PlatformUpdateLatestVersion | null;
  manifest?: PlatformUpdateManifest | null;
  activeTask?: PlatformUpdateTask | null;
  updateAvailable?: boolean;
  status?: 'UP_TO_DATE' | 'UPDATE_AVAILABLE' | 'UNKNOWN' | 'CHECK_FAILED' | string;
  currentKnown?: boolean;
  latestKnown?: boolean;
  sourceReachable?: boolean;
  updaterAvailable?: boolean;
  comparisonBasis?: string | null;
  actionRequired?: string | null;
  sourceType?: string | null;
  sourceUrl?: string | null;
  checkedAt?: string | null;
  errorMessage?: string | null;
  notes?: string[];
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

export interface ProfileSummary {
  currentUser: CurrentUser;
  roleNames: string[];
  permissionCount: number;
  recentLoginLogs: AuditLogRecord[];
  profileFieldSettings: ProfileFieldSetting[];
  profileCompletion?: ProfileCompletionSummary | null;
  mobileBindAvailable?: boolean | null;
  emailBindAvailable?: boolean | null;
  mobileBindVerificationRequired?: boolean | null;
  emailBindVerificationRequired?: boolean | null;
}

export interface ProfileFieldSetting {
  fieldKey: string;
  fieldLabel: string;
  fieldDescription?: string | null;
  visible: boolean;
  weight?: number | null;
  groupKey?: string | null;
  groupLabel?: string | null;
  fieldType?: string | null;
  required?: boolean | null;
  placeholder?: string | null;
  sortNo?: number | null;
  custom?: boolean | null;
}

export interface ProfileCompletionSummary {
  score: number;
  maxScore: number;
  completionRate: number;
  totalWeight?: number | null;
  earnedWeight?: number | null;
  groups: ProfileCompletionGroup[];
  incompleteItems: ProfileCompletionItem[];
}

export interface ProfileCompletionGroup {
  groupKey: string;
  groupLabel: string;
  score: number;
  maxScore: number;
  completionRate: number;
  totalWeight?: number | null;
  earnedWeight?: number | null;
  items: ProfileCompletionItem[];
}

export interface ProfileCompletionItem {
  fieldKey: string;
  fieldLabel: string;
  fieldDescription?: string | null;
  groupKey?: string | null;
  groupLabel?: string | null;
  completed: boolean;
  weight?: number | null;
  scoreContribution?: number | null;
  valueText?: string | null;
  actionType?: string | null;
  actionAvailable?: boolean | null;
  actionTarget?: string | null;
  actionLabel?: string | null;
  actionHint?: string | null;
}

export interface UserRecord {
  id: number;
  userNo?: string | null;
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
  source?: string | null;
  registeredAt?: string | null;
  lastLoginAt?: string | null;
  roleNames?: string[];
  deptNames?: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface UserDetail extends UserRecord {
  roleIds?: number[];
  deptIds?: number[];
  primaryDeptId?: number | null;
}

export interface OnlineSessionRecord {
  sessionId: string;
  userId: number;
  username: string;
  nickname?: string | null;
  realName?: string | null;
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
  defaultHomePath?: string | null;
  permissionCount?: number;
  userCount?: number;
  defaultRegistrationRole?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface RoleDetail extends RoleRecord {
  permissionKeys: string[];
  dataScopes?: RoleDataScope[];
}

export type DataScopeType = 'ALL' | 'TENANT' | 'DEPT' | 'DEPT_AND_CHILD' | 'SELF' | 'CUSTOM';

export interface RoleDataScope {
  resourceCode: string;
  scopeType: DataScopeType;
  customDeptIds?: number[];
  customUserIds?: number[];
}

export interface DepartmentRecord {
  id: number;
  tenantId: number;
  parentId?: number | null;
  deptCode: string;
  deptName: string;
  sortNo?: number;
  status: string;
  userCount?: number;
  createdAt?: string;
  updatedAt?: string;
  children?: DepartmentRecord[];
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
  builtin?: boolean;
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
