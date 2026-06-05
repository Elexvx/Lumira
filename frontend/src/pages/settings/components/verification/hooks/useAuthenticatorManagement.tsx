import type { FormInstance } from 'antd';
import { useCallback, useMemo, useState, type DragEvent, type Key } from 'react';
import { message, Space, Tag, Typography } from 'antd';
import { CheckOutlined, HolderOutlined } from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import type {
  PasskeySettings,
  SmsVerificationSettings,
  SmtpSettings,
  SmtpTestPayload,
  VerificationSettings,
  WechatLoginSettings,
} from '@/types/api';
import { TableActionBar } from '@/features/table/TableActionBar';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { useAuthenticatorConfigDrawer } from './useAuthenticatorConfigDrawer';
import { request } from '@/services/common/request';
import { API_OPTS } from '@/utils/errorMessage';

type AuthenticatorCode = 'passkey_login' | 'sms_login' | 'email_login' | 'wechat_login' | 'password_login';
type ConfigDrawerMode = 'totp' | 'sms' | 'email' | 'wechat' | 'passkey' | 'basic';

interface AuthenticatorRecord {
  key: AuthenticatorCode;
  order: number;
  identifier: string;
  type: string;
  title: string;
  description: string;
  enabled: boolean;
}

const SMS_ACCESS_KEY_SECRET_MASK = '********';

type AuthenticatorDeletionDeps = {
  canManageSettings: boolean;
  verificationForm: {
    setFieldValue: (name: keyof VerificationSettings, value: unknown) => void;
  };
  smsSettingsForm: {
    getFieldsValue: (all?: boolean) => SmsVerificationSettings;
  };
  passkeySettingsForm: {
    setFieldValue: (name: keyof (PasskeySettings & { allowedOriginsText?: string }), value: unknown) => void;
  };
  wechatSettingsForm: {
    setFieldValue: (name: keyof WechatLoginSettings, value: unknown) => void;
  };
  passkeySettingsData?: PasskeySettings;
  smsSettingsData?: SmsVerificationSettings;
  verificationSettingsData?: VerificationSettings;
  wechatSettingsData?: WechatLoginSettings;
  onSmsSettingsRefetch: () => Promise<unknown>;
  handleSaveVerificationSettings: () => Promise<void>;
  handleSaveWechatSettings: () => Promise<void>;
  handleSavePasskeySettings: (params: { forceEnabled: boolean; closeDrawer: boolean }) => Promise<void>;
};

const getEnabledAuthenticatorKeys = (
  passkeySettingsData?: PasskeySettings,
  smsSettingsData?: SmsVerificationSettings,
  verificationSettingsData?: VerificationSettings,
  wechatSettingsData?: WechatLoginSettings,
) => [
  passkeySettingsData?.enabled ? 'passkey_login' : null,
  smsSettingsData?.enabled ? 'sms_login' : null,
  verificationSettingsData?.emailLoginEnabled ? 'email_login' : null,
  wechatSettingsData?.enabled ? 'wechat_login' : null,
  (verificationSettingsData?.passwordLoginEnabled ?? true) ? 'password_login' : null,
].filter(Boolean) as AuthenticatorCode[];

const canRemoveAuthenticators = (selectedKeys: AuthenticatorCode[], enabledKeys: AuthenticatorCode[]) => {
  if (enabledKeys.length <= 1 && enabledKeys.some((key) => selectedKeys.includes(key))) {
    return false;
  }
  if (enabledKeys.length > 0 && enabledKeys.every((key) => selectedKeys.includes(key))) {
    return false;
  }
  return true;
};

const disableSmsAuthenticator = async ({
  canManageSettings,
  smsSettingsForm,
  onSmsSettingsRefetch,
}: Pick<AuthenticatorDeletionDeps, 'canManageSettings' | 'smsSettingsForm' | 'onSmsSettingsRefetch'>) => {
  if (!canManageSettings) {
    return;
  }
  const currentValues = smsSettingsForm.getFieldsValue(true);
  const accessKeySecret = currentValues.accessKeySecret === SMS_ACCESS_KEY_SECRET_MASK ? undefined : currentValues.accessKeySecret;
  await request<SmsVerificationSettings>('/v1/system/verification/sms-settings', {
    method: 'PUT',
    data: {
      ...currentValues,
      enabled: false,
      accessKeySecret,
    },
    ...API_OPTS.NO_REDIRECT,
  });
  message.success('短信认证器已停用');
  await onSmsSettingsRefetch();
};

const resolveLoginModeFromAuthenticatorKey = (key: AuthenticatorCode) => {
  if (key === 'passkey_login') {
    return 'passkey';
  }
  if (key === 'sms_login') {
    return 'sms';
  }
  if (key === 'email_login') {
    return 'email';
  }
  if (key === 'wechat_login') {
    return 'wechat';
  }
  return 'password';
};

const buildAddAuthenticatorItems = ({
  canManageSettings,
  passkeyEnabled,
  smsEnabled,
  emailEnabled,
  wechatEnabled,
  passwordEnabled,
  onEnableAuthenticator,
}: {
  canManageSettings: boolean;
  passkeyEnabled: boolean;
  smsEnabled: boolean;
  emailEnabled: boolean;
  wechatEnabled: boolean;
  passwordEnabled: boolean;
  onEnableAuthenticator: (mode: 'passkey' | 'sms' | 'email' | 'wechat' | 'password') => void;
}) =>
  [
    { key: 'passkey', label: '通行密钥', enabled: passkeyEnabled, mode: 'passkey' as const },
    { key: 'sms', label: '短信', enabled: smsEnabled, mode: 'sms' as const },
    { key: 'email', label: '邮箱', enabled: emailEnabled, mode: 'email' as const },
    { key: 'wechat', label: '微信', enabled: wechatEnabled, mode: 'wechat' as const },
    { key: 'password', label: '密码', enabled: passwordEnabled, mode: 'password' as const },
  ]
    .filter((item) => !item.enabled)
    .map((item) => ({
      key: item.key,
      label: item.label,
      disabled: !canManageSettings,
      onClick: () => void onEnableAuthenticator(item.mode),
    }));

const deleteAuthenticatorByKey = async (key: AuthenticatorCode, deps: AuthenticatorDeletionDeps) => {
  const enabledKeys = getEnabledAuthenticatorKeys(deps.passkeySettingsData, deps.smsSettingsData, deps.verificationSettingsData, deps.wechatSettingsData);
  if (!canRemoveAuthenticators([key], enabledKeys)) {
    message.warning('至少需要保留一种可用登录方式');
    return;
  }

  if (key === 'sms_login') {
    await disableSmsAuthenticator({
      canManageSettings: deps.canManageSettings,
      smsSettingsForm: deps.smsSettingsForm,
      onSmsSettingsRefetch: deps.onSmsSettingsRefetch,
    });
    return;
  }
  if (key === 'email_login') {
    deps.verificationForm.setFieldValue('emailLoginEnabled', false);
    await deps.handleSaveVerificationSettings();
    return;
  }
  if (key === 'passkey_login') {
    deps.passkeySettingsForm.setFieldValue('enabled', false);
    await deps.handleSavePasskeySettings({ forceEnabled: false, closeDrawer: false });
    return;
  }
  if (key === 'wechat_login') {
    deps.wechatSettingsForm.setFieldValue('enabled', false);
    await deps.handleSaveWechatSettings();
    return;
  }
  deps.verificationForm.setFieldValue('passwordLoginEnabled', false);
  await deps.handleSaveVerificationSettings();
};

const deleteSelectedAuthenticators = async (selectedAuthenticatorKeys: Key[], deps: AuthenticatorDeletionDeps) => {
  if (!selectedAuthenticatorKeys.length) {
    message.info('请先选择要删除的认证器');
    return;
  }

  const selectedKeys = new Set(selectedAuthenticatorKeys as AuthenticatorCode[]);
  const enabledKeys = getEnabledAuthenticatorKeys(deps.passkeySettingsData, deps.smsSettingsData, deps.verificationSettingsData, deps.wechatSettingsData);
  if (!canRemoveAuthenticators(Array.from(selectedKeys), enabledKeys)) {
    message.warning('至少需要保留一种可用登录方式');
    return;
  }

  const deletionOrder: AuthenticatorCode[] = ['sms_login', 'email_login', 'passkey_login', 'wechat_login', 'password_login'];
  const keysToDelete = deletionOrder.filter((key): key is AuthenticatorCode => selectedKeys.has(key));
  for (const key of keysToDelete) {
    await deleteAuthenticatorByKey(key, deps);
  }
};

const DEFAULT_LOGIN_MODE_ORDER = ['passkey', 'sms', 'email', 'wechat', 'password'] as const;

const normalizeLoginModeOrder = (order?: string[]) => {
  const result: Array<(typeof DEFAULT_LOGIN_MODE_ORDER)[number]> = [];
  (order || []).forEach((item) => {
    if ((item === 'passkey' || item === 'sms' || item === 'email' || item === 'wechat' || item === 'password') && !result.includes(item)) {
      result.push(item);
    }
  });
  DEFAULT_LOGIN_MODE_ORDER.forEach((item) => {
    if (!result.includes(item)) {
      result.push(item);
    }
  });
  return result;
};

const resolveAuthenticatorDrawerMode = (key: AuthenticatorCode): ConfigDrawerMode => {
  if (key === 'sms_login') {
    return 'sms';
  }
  if (key === 'passkey_login') {
    return 'passkey';
  }
  if (key === 'email_login') {
    return 'email';
  }
  if (key === 'wechat_login') {
    return 'wechat';
  }
  return 'basic';
};

const buildAuthenticatorRowMap = ({
  passkeyEnabled,
  smsEnabled,
  emailEnabled,
  wechatEnabled,
  passwordEnabled,
}: {
  passkeyEnabled: boolean;
  smsEnabled: boolean;
  emailEnabled: boolean;
  wechatEnabled: boolean;
  passwordEnabled: boolean;
}): Record<(typeof DEFAULT_LOGIN_MODE_ORDER)[number], Omit<AuthenticatorRecord, 'order'>> => ({
  passkey: {
    key: 'passkey_login',
    identifier: '通行密钥',
    type: '通行密钥',
    title: '通行密钥',
    description: '使用系统钥匙串或密码管理器进行 WebAuthn 验证',
    enabled: passkeyEnabled,
  },
  sms: {
    key: 'sms_login',
    identifier: '短信',
    type: '短信',
    title: '短信验证',
    description: '使用短信验证码登录',
    enabled: smsEnabled,
  },
  email: {
    key: 'email_login',
    identifier: '邮箱',
    type: '邮箱',
    title: '邮箱验证码',
    description: '使用邮箱验证码登录',
    enabled: emailEnabled,
  },
  wechat: {
    key: 'wechat_login',
    identifier: '微信',
    type: '微信',
    title: '微信扫码登录',
    description: '使用微信扫码登录，未注册用户可自动创建账号',
    enabled: wechatEnabled,
  },
  password: {
    key: 'password_login',
    identifier: '密码',
    type: '密码',
    title: '账号密码登录',
    description: '使用账号密码登录',
    enabled: passwordEnabled,
  },
});

const buildAuthenticatorRows = ({
  configuredLoginModeOrder,
  passkeyEnabled,
  smsEnabled,
  emailEnabled,
  wechatEnabled,
  passwordEnabled,
}: {
  configuredLoginModeOrder?: string[];
  passkeyEnabled: boolean;
  smsEnabled: boolean;
  emailEnabled: boolean;
  wechatEnabled: boolean;
  passwordEnabled: boolean;
}): AuthenticatorRecord[] => {
  const rowsByMode = buildAuthenticatorRowMap({
    passkeyEnabled,
    smsEnabled,
    emailEnabled,
    wechatEnabled,
    passwordEnabled,
  });

  return normalizeLoginModeOrder(configuredLoginModeOrder)
    .filter((mode) => rowsByMode[mode].enabled)
    .map((mode, index) => ({
      ...rowsByMode[mode],
      order: index + 1,
    }));
};

const buildAuthenticatorOrderColumn = ({
  tokenColorTextSecondary,
  isMobile,
}: {
  tokenColorTextSecondary: string;
  isMobile: boolean;
}): ProColumns<AuthenticatorRecord> => ({
  title: '',
  dataIndex: 'order',
  width: 'var(--saas-spacing-96)',
  search: false,
  render: (_, record) => (
    <Space size={resolveResponsiveValue(APP_SPACING.sectionGap, isMobile)}>
      <HolderOutlined style={{ color: tokenColorTextSecondary }} />
      <Typography.Text>{record.order}</Typography.Text>
    </Space>
  ),
});

const buildAuthenticatorIdentityColumn = (): ProColumns<AuthenticatorRecord> => ({
  title: '认证标识',
  dataIndex: 'identifier',
  width: 'var(--saas-spacing-180)',
  search: false,
});

const buildAuthenticatorTypeColumn = (): ProColumns<AuthenticatorRecord> => ({
  title: '认证类型',
  dataIndex: 'type',
  width: 'var(--saas-spacing-160)',
  search: false,
  render: (_, record) => <Tag>{record.type}</Tag>,
});

const buildAuthenticatorTitleColumn = (): ProColumns<AuthenticatorRecord> => ({
  title: '标题',
  dataIndex: 'title',
  width: 'var(--saas-spacing-180)',
  search: false,
});

const buildAuthenticatorStatusColumn = ({
  tokenColorSuccess,
}: {
  tokenColorSuccess: string;
}): ProColumns<AuthenticatorRecord> => ({
  title: '启用',
  dataIndex: 'enabled',
  width: 'var(--saas-spacing-120)',
  align: 'center',
  search: false,
  render: (_, record) => (record.enabled ? <CheckOutlined style={{ color: tokenColorSuccess }} /> : null),
});

type BuildAuthenticatorColumnsParams = {
  canManageSettings: boolean;
  isMobile: boolean;
  tokenColorSuccess: string;
  tokenColorTextSecondary: string;
  togglingAuthenticatorKey: AuthenticatorCode | null;
  onToggleAuthenticator: (record: AuthenticatorRecord) => void;
  onOpenConfigDrawer: (mode: ConfigDrawerMode) => void;
  onDeleteAuthenticator: (key: AuthenticatorCode) => void;
};

type AuthenticatorColumnsActionsParams = Pick<
  BuildAuthenticatorColumnsParams,
  'canManageSettings' | 'isMobile' | 'togglingAuthenticatorKey' | 'onToggleAuthenticator' | 'onOpenConfigDrawer' | 'onDeleteAuthenticator'
>;

type AuthenticatorActionItem = {
  key: string;
  label: string;
  danger?: boolean;
  disabled?: boolean;
  loading?: boolean;
  onClick: () => void;
};

type BuildAuthenticatorActionItemsParams = {
  canManageSettings: boolean;
  togglingAuthenticatorKey: AuthenticatorCode | null;
  onToggleAuthenticator: (record: AuthenticatorRecord) => void;
  onOpenConfigDrawer: (mode: ConfigDrawerMode) => void;
  onDeleteAuthenticator: (key: AuthenticatorCode) => void;
};

const buildAuthenticatorToggleActionItem = (
  record: AuthenticatorRecord,
  params: BuildAuthenticatorActionItemsParams,
): AuthenticatorActionItem => ({
  key: 'toggle',
  label: record.enabled ? '禁用' : '启用',
  danger: record.enabled,
  disabled: !params.canManageSettings,
  loading: params.togglingAuthenticatorKey === record.key,
  onClick: () => void params.onToggleAuthenticator(record),
});

const buildAuthenticatorConfigActionItem = (
  record: AuthenticatorRecord,
  params: BuildAuthenticatorActionItemsParams,
): AuthenticatorActionItem => ({
  key: 'config',
  label: '配置',
  disabled: !params.canManageSettings,
  onClick: () => params.onOpenConfigDrawer(resolveAuthenticatorDrawerMode(record.key)),
});

const buildAuthenticatorDeleteActionItem = (
  record: AuthenticatorRecord,
  params: BuildAuthenticatorActionItemsParams,
): AuthenticatorActionItem => ({
  key: 'delete',
  label: '删除',
  danger: true,
  disabled: !params.canManageSettings,
  onClick: () => void params.onDeleteAuthenticator(record.key),
});

const buildAuthenticatorActionItems = (
  record: AuthenticatorRecord,
  params: BuildAuthenticatorActionItemsParams,
): AuthenticatorActionItem[] => [
  buildAuthenticatorToggleActionItem(record, params),
  buildAuthenticatorConfigActionItem(record, params),
  buildAuthenticatorDeleteActionItem(record, params),
];

const buildAuthenticatorActionColumn = ({
  canManageSettings,
  isMobile,
  togglingAuthenticatorKey,
  onToggleAuthenticator,
  onOpenConfigDrawer,
  onDeleteAuthenticator,
}: AuthenticatorColumnsActionsParams): ProColumns<AuthenticatorRecord> => ({
  title: '操作',
  valueType: 'option',
  fixed: 'right',
  width: 'var(--saas-spacing-240)',
  render: (_, record) => (
    <TableActionBar
      isMobile={isMobile}
      inlineCount={isMobile ? 0 : 3}
      items={buildAuthenticatorActionItems(record, {
        canManageSettings,
        togglingAuthenticatorKey,
        onToggleAuthenticator,
        onOpenConfigDrawer,
        onDeleteAuthenticator,
      })}
    />
  ),
});

const buildAuthenticatorColumns = ({
  canManageSettings,
  isMobile,
  tokenColorSuccess,
  tokenColorTextSecondary,
  togglingAuthenticatorKey,
  onToggleAuthenticator,
  onOpenConfigDrawer,
  onDeleteAuthenticator,
}: BuildAuthenticatorColumnsParams): ProColumns<AuthenticatorRecord>[] => [
  buildAuthenticatorOrderColumn({ tokenColorTextSecondary, isMobile }),
  buildAuthenticatorIdentityColumn(),
  buildAuthenticatorTypeColumn(),
  buildAuthenticatorTitleColumn(),
  buildAuthenticatorStatusColumn({ tokenColorSuccess }),
  buildAuthenticatorActionColumn({
    canManageSettings,
    isMobile,
    togglingAuthenticatorKey,
    onToggleAuthenticator,
    onOpenConfigDrawer,
    onDeleteAuthenticator,
  }),
];

interface UseAuthenticatorManagementParams {
  canManageSettings: boolean;
  isMobile: boolean;
  tokenColorSuccess: string;
  tokenColorTextSecondary: string;
  verificationForm: FormInstance<VerificationSettings>;
  smsSettingsForm: FormInstance<SmsVerificationSettings>;
  smtpSettingsForm: FormInstance<SmtpSettings>;
  smtpTestForm: FormInstance<SmtpTestPayload>;
  wechatSettingsForm: FormInstance<WechatLoginSettings>;
  passkeySettingsForm: FormInstance<PasskeySettings & { allowedOriginsText?: string }>;
  verificationSettingsData?: VerificationSettings;
  smsSettingsData?: SmsVerificationSettings;
  smtpSettingsData?: SmtpSettings & { passwordConfigured?: boolean };
  wechatSettingsData?: WechatLoginSettings;
  passkeySettingsData?: PasskeySettings;
  verificationLoading: boolean;
  onVerificationSettingsRefetch: () => Promise<unknown>;
  onSmsSettingsRefetch: () => Promise<unknown>;
  onSmtpSettingsRefetch: () => Promise<unknown>;
  onWechatSettingsRefetch: () => Promise<unknown>;
  onPasskeySettingsRefetch: () => Promise<unknown>;
}

export const useAuthenticatorManagement = ({
  canManageSettings,
  isMobile,
  tokenColorSuccess,
  tokenColorTextSecondary,
  verificationForm,
  smsSettingsForm,
  smtpSettingsForm,
  smtpTestForm,
  wechatSettingsForm,
  passkeySettingsForm,
  verificationSettingsData,
  smsSettingsData,
  smtpSettingsData,
  wechatSettingsData,
  passkeySettingsData,
  verificationLoading,
  onVerificationSettingsRefetch,
  onSmsSettingsRefetch,
  onSmtpSettingsRefetch,
  onWechatSettingsRefetch,
  onPasskeySettingsRefetch,
}: UseAuthenticatorManagementParams) => {
  const [selectedAuthenticatorKeys, setSelectedAuthenticatorKeys] = useState<Key[]>([]);
  const [reorderingAuthenticators, setReorderingAuthenticators] = useState(false);

  const {
    drawerState,
    saveState,
    drawerFooter,
    drawerContent,
  } = useAuthenticatorConfigDrawer({
    canManageSettings,
    verificationForm,
    smsSettingsForm,
    wechatSettingsForm,
    passkeySettingsForm,
    verificationSettingsData,
    smsSettingsData,
    wechatSettingsData,
    passkeySettingsData,
    verificationLoading,
    onVerificationSettingsRefetch,
    onSmsSettingsRefetch,
    smtpSettingsForm,
    smtpTestForm,
    smtpSettingsData,
    onSmtpSettingsRefetch,
    onWechatSettingsRefetch,
    onPasskeySettingsRefetch,
  });
  const configuredLoginModeOrder = useMemo(
    () =>
      verificationSettingsData?.loginModeOrder ??
      (verificationForm.getFieldValue('loginModeOrder') as string[] | undefined) ??
      ['passkey', 'sms', 'email', 'wechat', 'password'],
    [verificationForm, verificationSettingsData?.loginModeOrder],
  );
  const authenticatorRows = useMemo<AuthenticatorRecord[]>(
    () =>
      buildAuthenticatorRows({
        configuredLoginModeOrder,
        passkeyEnabled: Boolean(passkeySettingsData?.enabled),
        smsEnabled: Boolean(smsSettingsData?.enabled),
        emailEnabled: Boolean(verificationSettingsData?.emailLoginEnabled),
        wechatEnabled: Boolean(wechatSettingsData?.enabled),
        passwordEnabled: Boolean(verificationSettingsData?.passwordLoginEnabled ?? true),
      }),
    [configuredLoginModeOrder, passkeySettingsData?.enabled, smsSettingsData?.enabled, verificationSettingsData?.emailLoginEnabled, verificationSettingsData?.passwordLoginEnabled, wechatSettingsData?.enabled],
  );

  const deletionDeps = useMemo(
    () => ({
      canManageSettings,
      verificationForm,
      smsSettingsForm,
      passkeySettingsForm,
      wechatSettingsForm,
      passkeySettingsData,
      smsSettingsData,
      verificationSettingsData,
      wechatSettingsData,
      onSmsSettingsRefetch,
      handleSaveVerificationSettings: saveState.handleSaveVerificationSettings,
      handleSaveWechatSettings: saveState.handleSaveWechatSettings,
      handleSavePasskeySettings: saveState.handleSavePasskeySettings,
    }),
    [
      canManageSettings,
      onSmsSettingsRefetch,
      passkeySettingsData,
      passkeySettingsForm,
      saveState.handleSavePasskeySettings,
      saveState.handleSaveVerificationSettings,
      saveState.handleSaveWechatSettings,
      smsSettingsData,
      smsSettingsForm,
      verificationForm,
      verificationSettingsData,
      wechatSettingsData,
      wechatSettingsForm,
    ],
  );
  const handleDeleteAuthenticator = useCallback(
    async (key: Parameters<typeof deleteAuthenticatorByKey>[0]) => {
      await deleteAuthenticatorByKey(key, deletionDeps);
    },
    [deletionDeps],
  );
  const handleDeleteSelectedAuthenticators = useCallback(async () => {
    await deleteSelectedAuthenticators(selectedAuthenticatorKeys, deletionDeps);
  }, [deletionDeps, selectedAuthenticatorKeys]);

  const [draggedAuthenticatorKey, setDraggedAuthenticatorKey] = useState<AuthenticatorCode | null>(null);
  const handleAuthenticatorDragStart = useCallback(
    (record: AuthenticatorRecord) => (event: DragEvent<HTMLTableRowElement>) => {
      if (!canManageSettings || reorderingAuthenticators) {
        event.preventDefault();
        return;
      }
      event.dataTransfer.effectAllowed = 'move';
      event.dataTransfer.setData('text/plain', record.key);
      setDraggedAuthenticatorKey(record.key);
    },
    [canManageSettings, reorderingAuthenticators],
  );
  const handleAuthenticatorDragOver = useCallback(
    (record: AuthenticatorRecord) => (event: DragEvent<HTMLTableRowElement>) => {
      if (!draggedAuthenticatorKey || draggedAuthenticatorKey === record.key || !canManageSettings || reorderingAuthenticators) {
        return;
      }
      event.preventDefault();
    },
    [canManageSettings, draggedAuthenticatorKey, reorderingAuthenticators],
  );
  const handleAuthenticatorDrop = useCallback(
    (record: AuthenticatorRecord) => async (event: DragEvent<HTMLTableRowElement>) => {
      event.preventDefault();
      if (!draggedAuthenticatorKey || draggedAuthenticatorKey === record.key || !canManageSettings || reorderingAuthenticators) {
        setDraggedAuthenticatorKey(null);
        return;
      }
      const currentIndex = authenticatorRows.findIndex((row) => row.key === draggedAuthenticatorKey);
      const targetIndex = authenticatorRows.findIndex((row) => row.key === record.key);
      if (currentIndex < 0 || targetIndex < 0) {
        setDraggedAuthenticatorKey(null);
        return;
      }
      const nextRows = [...authenticatorRows];
      const [dragged] = nextRows.splice(currentIndex, 1);
      nextRows.splice(targetIndex, 0, dragged);
      setDraggedAuthenticatorKey(null);
      const loginModeOrder = nextRows.map((row) => resolveLoginModeFromAuthenticatorKey(row.key));
      setReorderingAuthenticators(true);
      try {
        const result = await request<VerificationSettings>('/v1/system/verification/settings', {
          method: 'PUT',
          data: { loginModeOrder },
          ...API_OPTS.NO_REDIRECT,
        });
        verificationForm.setFieldsValue(result);
        message.success('登录方式顺序已更新');
        await onVerificationSettingsRefetch();
      } finally {
        setReorderingAuthenticators(false);
      }
    },
    [authenticatorRows, canManageSettings, draggedAuthenticatorKey, onVerificationSettingsRefetch, reorderingAuthenticators, verificationForm],
  );
  const handleAuthenticatorDragEnd = useCallback(() => {
    setDraggedAuthenticatorKey(null);
  }, []);

  const [togglingAuthenticatorKey, setTogglingAuthenticatorKey] = useState<AuthenticatorCode | null>(null);
  const handleEnableAuthenticator = useCallback(
    (mode: 'sms' | 'email' | 'passkey' | 'wechat' | 'password') => {
      if (!canManageSettings) {
        return;
      }
      if (mode === 'sms') {
        smsSettingsForm.setFieldValue('enabled', true);
        drawerState.openConfigDrawer('sms');
        return;
      }
      if (mode === 'email') {
        verificationForm.setFieldValue('emailLoginEnabled', true);
        drawerState.openConfigDrawer('email');
        return;
      }
      if (mode === 'passkey') {
        passkeySettingsForm.setFieldsValue({ enabled: true, passwordlessEnabled: true });
        drawerState.openConfigDrawer('passkey');
        return;
      }
      if (mode === 'wechat') {
        wechatSettingsForm.setFieldValue('enabled', true);
        drawerState.openConfigDrawer('wechat');
        return;
      }
      verificationForm.setFieldValue('passwordLoginEnabled', true);
      drawerState.openConfigDrawer('basic');
    },
    [canManageSettings, drawerState, passkeySettingsForm, smsSettingsForm, verificationForm, wechatSettingsForm],
  );
  const handleToggleAuthenticator = useCallback(
    async (record: AuthenticatorRecord) => {
      if (!canManageSettings) {
        return;
      }
      setTogglingAuthenticatorKey(record.key);
      try {
        if (record.enabled) {
          await handleDeleteAuthenticator(record.key);
          return;
        }
        handleEnableAuthenticator(resolveLoginModeFromAuthenticatorKey(record.key));
      } finally {
        setTogglingAuthenticatorKey(null);
      }
    },
    [canManageSettings, handleDeleteAuthenticator, handleEnableAuthenticator],
  );

  const addAuthenticatorItems = useMemo(
    () =>
      buildAddAuthenticatorItems({
        canManageSettings,
        passkeyEnabled: Boolean(passkeySettingsData?.enabled),
        smsEnabled: Boolean(smsSettingsData?.enabled),
        emailEnabled: Boolean(verificationSettingsData?.emailLoginEnabled),
        wechatEnabled: Boolean(wechatSettingsData?.enabled),
        passwordEnabled: Boolean(verificationSettingsData?.passwordLoginEnabled ?? true),
        onEnableAuthenticator: handleEnableAuthenticator,
      }),
    [
      canManageSettings,
      handleEnableAuthenticator,
      passkeySettingsData?.enabled,
      smsSettingsData?.enabled,
      verificationSettingsData?.emailLoginEnabled,
      verificationSettingsData?.passwordLoginEnabled,
      wechatSettingsData?.enabled,
    ],
  );

  const authenticatorColumns = useMemo(
    () =>
      buildAuthenticatorColumns({
        canManageSettings,
        isMobile,
        tokenColorSuccess,
        tokenColorTextSecondary,
        togglingAuthenticatorKey,
        onToggleAuthenticator: handleToggleAuthenticator,
        onOpenConfigDrawer: drawerState.openConfigDrawer,
        onDeleteAuthenticator: handleDeleteAuthenticator,
      }),
    [
      canManageSettings,
      drawerState.openConfigDrawer,
      handleDeleteAuthenticator,
      handleToggleAuthenticator,
      isMobile,
      tokenColorSuccess,
      tokenColorTextSecondary,
      togglingAuthenticatorKey,
    ],
  );

  const tableProps = {
    canManageSettings,
    isMobile,
    verificationLoading,
    selectedAuthenticatorKeys,
    setSelectedAuthenticatorKeys,
    draggedAuthenticatorKey,
    reorderingAuthenticators,
    authenticatorRows,
    authenticatorColumns,
    onAuthenticatorDragStart: handleAuthenticatorDragStart,
    onAuthenticatorDragOver: handleAuthenticatorDragOver,
    onAuthenticatorDrop: handleAuthenticatorDrop,
    onAuthenticatorDragEnd: handleAuthenticatorDragEnd,
  };
  const toolbarProps = {
    addAuthenticatorItems,
    onDeleteSelectedAuthenticators: handleDeleteSelectedAuthenticators,
  };

  return {
    tablePack: {
      tableProps,
      toolbarProps,
      selectionPack: {
        selectedAuthenticatorKeys,
        setSelectedAuthenticatorKeys,
      },
      orderingPack: {
        draggedAuthenticatorKey,
        reorderingAuthenticators,
        authenticatorRows,
        handleAuthenticatorDragStart,
        handleAuthenticatorDragOver,
        handleAuthenticatorDrop,
        handleAuthenticatorDragEnd,
      },
      overviewPack: {
        togglingAuthenticatorKey,
        authenticatorColumns,
        addAuthenticatorItems,
      },
    },
    drawerPack: {
      drawerProps: {
        ...drawerState,
        ...drawerFooter,
        ...drawerContent,
      },
    },
  };
};
