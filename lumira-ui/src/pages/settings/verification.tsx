import { ManagementTable } from '@/features/management/ManagementTable';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { useResponsive } from '@/hooks/useResponsive';
import { DeleteOutlined, DownOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Dropdown, Form, Popconfirm, theme } from 'antd';
import type { FormInstance } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useAuthenticatorManagement } from './components/verification/hooks/useAuthenticatorManagement';
import { DEFAULT_TABLE_PAGE_SIZE } from '@/features/table/proTableRequest';
import { request } from '@/services/common/request';
import { API_OPTS } from '@/utils/errorMessage';

import type {
  PasskeySettings,
  SmsVerificationSettings,
  SmtpSettings,
  SmtpTestPayload,
  VerificationSettings,
  WechatLoginSettings,
} from '@/types/api';
import { databaseMessage } from '@/i18n/databaseMessage';

const SETTINGS_REQUEST_TIMEOUT_MS = 30000;
const SETTINGS_QUERY_RETRY_COUNT = 1;
const SETTINGS_QUERY_RETRY_DELAY_MS = 1000;

const useSystemVerificationPageAccess = () => {
  const { token } = theme.useToken();
  const actionPermission = useActionPermission();
  const responsive = useResponsive();
  const canViewVerification =
    actionPermission.can('system:verification:view') ||
    actionPermission.can('system:verification:manage') ||
    actionPermission.can('system:config:view');
  const canManageSettings = actionPermission.can('system:verification:manage') || actionPermission.can('system:config:update');

  const [verificationForm] = Form.useForm<VerificationSettings>();
  const [smsSettingsForm] = Form.useForm<SmsVerificationSettings>();
  const [smtpSettingsForm] = Form.useForm<SmtpSettings>();
  const [smtpTestForm] = Form.useForm<SmtpTestPayload>();
  const [wechatSettingsForm] = Form.useForm<WechatLoginSettings>();
  const [passkeySettingsForm] = Form.useForm<PasskeySettings & { allowedOriginsText?: string }>();

  const verificationSettingsQuery = useQuery({
    queryKey: ['verification-settings'],
    queryFn: async () =>
      request<VerificationSettings>('/v1/system/verification/settings', {
        method: 'GET',
        timeoutMs: SETTINGS_REQUEST_TIMEOUT_MS,
        ...API_OPTS.NO_REDIRECT,
      }),
    enabled: canViewVerification,
    retry: SETTINGS_QUERY_RETRY_COUNT,
    retryDelay: SETTINGS_QUERY_RETRY_DELAY_MS,
  });
  const smsSettingsQuery = useQuery({
    queryKey: ['sms-verification-settings'],
    queryFn: async () =>
      request<SmsVerificationSettings>('/v1/system/verification/sms-settings', {
        method: 'GET',
        timeoutMs: SETTINGS_REQUEST_TIMEOUT_MS,
        ...API_OPTS.NO_REDIRECT,
      }),
    enabled: canViewVerification,
    retry: SETTINGS_QUERY_RETRY_COUNT,
    retryDelay: SETTINGS_QUERY_RETRY_DELAY_MS,
  });
  const smtpSettingsQuery = useQuery({
    queryKey: ['smtp-settings'],
    queryFn: async () =>
      request<SmtpSettings>('/v1/system/smtp-settings', {
        method: 'GET',
        timeoutMs: SETTINGS_REQUEST_TIMEOUT_MS,
        ...API_OPTS.NO_REDIRECT,
      }),
    enabled: canViewVerification,
    retry: SETTINGS_QUERY_RETRY_COUNT,
    retryDelay: SETTINGS_QUERY_RETRY_DELAY_MS,
  });
  const wechatSettingsQuery = useQuery({
    queryKey: ['wechat-login-settings'],
    queryFn: async () =>
      request<WechatLoginSettings>('/v1/system/verification/wechat-settings', {
        method: 'GET',
        timeoutMs: SETTINGS_REQUEST_TIMEOUT_MS,
        ...API_OPTS.NO_REDIRECT,
      }),
    enabled: canViewVerification,
    retry: SETTINGS_QUERY_RETRY_COUNT,
    retryDelay: SETTINGS_QUERY_RETRY_DELAY_MS,
  });
  const passkeySettingsQuery = useQuery({
    queryKey: ['passkey-settings'],
    queryFn: async () =>
      request<PasskeySettings>('/v1/system/verification/passkey-settings', {
        method: 'GET',
        timeoutMs: SETTINGS_REQUEST_TIMEOUT_MS,
        ...API_OPTS.NO_REDIRECT,
      }),
    enabled: canViewVerification,
    retry: SETTINGS_QUERY_RETRY_COUNT,
    retryDelay: SETTINGS_QUERY_RETRY_DELAY_MS,
  });

  const verificationLoading =
    verificationSettingsQuery.isLoading || smsSettingsQuery.isLoading || smtpSettingsQuery.isLoading || wechatSettingsQuery.isLoading || passkeySettingsQuery.isLoading;

  return {
    tokenColorSuccess: token.colorSuccess,
    tokenColorTextSecondary: token.colorTextSecondary,
    actionPermission,
    responsive,
    canManageSettings,
    verificationForm,
    smsSettingsForm,
    smtpSettingsForm,
    smtpTestForm,
    wechatSettingsForm,
    passkeySettingsForm,
    verificationSettingsQuery,
    smsSettingsQuery,
    smtpSettingsQuery,
    wechatSettingsQuery,
    passkeySettingsQuery,
    verificationLoading,
  };
};

const t = databaseMessage;

const resolveDrawerTitle = (mode: 'basic' | 'totp' | 'sms' | 'email' | 'wechat' | 'passkey' | null) => {
  switch (mode) {
    case 'sms':
      return t('ui.settings.verification.smsSettings');
    case 'email':
      return t('ui.settings.verification.emailSettings');
    case 'wechat':
      return t('ui.settings.verification.wechatSettings');
    case 'passkey':
      return t('ui.settings.verification.passkey');
    case 'totp':
      return t('ui.settings.verification.verificationSettings');
    case 'basic':
      return t('ui.settings.verification.basicSettings');
    default:
      return t('ui.settings.verification.verificationManagement');
  }
};

type VerificationDrawerMode = 'basic' | 'totp' | 'sms' | 'email' | 'wechat' | 'passkey' | null;

const DormantVerificationForms = ({
  mode,
  verificationForm,
  smsSettingsForm,
  smtpSettingsForm,
  smtpTestForm,
  wechatSettingsForm,
  passkeySettingsForm,
}: {
  mode: VerificationDrawerMode;
  verificationForm: FormInstance<VerificationSettings>;
  smsSettingsForm: FormInstance<SmsVerificationSettings>;
  smtpSettingsForm: FormInstance<SmtpSettings>;
  smtpTestForm: FormInstance<SmtpTestPayload>;
  wechatSettingsForm: FormInstance<WechatLoginSettings>;
  passkeySettingsForm: FormInstance<PasskeySettings & { allowedOriginsText?: string }>;
}) => (
  <>
    {mode !== 'totp' ? <Form form={verificationForm} component={false} /> : null}
    {mode !== 'sms' ? <Form form={smsSettingsForm} component={false} /> : null}
    {mode !== 'email' ? <Form form={smtpSettingsForm} component={false} /> : null}
    {mode !== 'email' ? <Form form={smtpTestForm} component={false} /> : null}
    {mode !== 'wechat' ? <Form form={wechatSettingsForm} component={false} /> : null}
    {mode !== 'passkey' ? <Form form={passkeySettingsForm} component={false} /> : null}
  </>
);

const SystemVerificationPage = () => {
  const {
    tokenColorSuccess,
    tokenColorTextSecondary,
    responsive,
    canManageSettings,
    verificationForm,
    smsSettingsForm,
    smtpSettingsForm,
    smtpTestForm,
    wechatSettingsForm,
    passkeySettingsForm,
    verificationSettingsQuery,
    smsSettingsQuery,
    smtpSettingsQuery,
    wechatSettingsQuery,
    passkeySettingsQuery,
    verificationLoading,
  } = useSystemVerificationPageAccess();

  const { tablePack, drawerPack } = useAuthenticatorManagement({
    canManageSettings,
    isMobile: responsive.isMobile,
    tokenColorSuccess,
    tokenColorTextSecondary,
    verificationForm,
    smsSettingsForm,
    smtpSettingsForm,
    smtpTestForm,
    wechatSettingsForm,
    passkeySettingsForm,
    verificationSettingsData: verificationSettingsQuery.data,
    smsSettingsData: smsSettingsQuery.data,
    smtpSettingsData: smtpSettingsQuery.data,
    wechatSettingsData: wechatSettingsQuery.data,
    passkeySettingsData: passkeySettingsQuery.data,
    verificationLoading,
    onVerificationSettingsRefetch: () => verificationSettingsQuery.refetch(),
    onSmsSettingsRefetch: () => smsSettingsQuery.refetch(),
    onSmtpSettingsRefetch: () => smtpSettingsQuery.refetch(),
    onWechatSettingsRefetch: () => wechatSettingsQuery.refetch(),
    onPasskeySettingsRefetch: () => passkeySettingsQuery.refetch(),
  });

  const drawerProps = {
    title: resolveDrawerTitle(drawerPack.drawerProps.configDrawerMode),
    open: Boolean(drawerPack.drawerProps.configDrawerMode),
    onClose: () => drawerPack.drawerProps.closeConfigDrawer(),
    footerActions: drawerPack.drawerProps.resolveDrawerFooterActions(),
    children: drawerPack.drawerProps.configDrawerMode ? drawerPack.drawerProps.renderConfigDrawerContent() : null,
  };
  const drawerMode = drawerPack.drawerProps.configDrawerMode;

  const verificationTableProps = {
    rowKey: 'key' as const,
    columns: tablePack.tableProps.authenticatorColumns,
    isMobile: tablePack.tableProps.isMobile,
    search: false as const,
    loading: tablePack.tableProps.verificationLoading,
    dataSource: tablePack.tableProps.authenticatorRows,
    pagination: { pageSize: DEFAULT_TABLE_PAGE_SIZE, showSizeChanger: true },
    onRow: (record: (typeof tablePack.tableProps.authenticatorRows)[number]) => ({
      draggable: tablePack.tableProps.canManageSettings && !tablePack.tableProps.reorderingAuthenticators,
      onDragStart: tablePack.tableProps.onAuthenticatorDragStart(record),
      onDragOver: tablePack.tableProps.onAuthenticatorDragOver(record),
      onDrop: tablePack.tableProps.onAuthenticatorDrop(record),
      onDragEnd: tablePack.tableProps.onAuthenticatorDragEnd,
      style: {
        cursor: tablePack.tableProps.canManageSettings && !tablePack.tableProps.reorderingAuthenticators ? 'grab' : undefined,
        opacity: tablePack.tableProps.draggedAuthenticatorKey === record.key ? 0.45 : 1,
      },
    }),
    rowSelection: {
      selectedRowKeys: tablePack.tableProps.selectedAuthenticatorKeys,
      onChange: tablePack.tableProps.setSelectedAuthenticatorKeys,
    },
  };

  return (
    <ManagementPage title={t('ui.settings.verification.verificationManagement')}>
      <ManagementPageBody>
        <ManagementTable
          {...verificationTableProps}
          toolBarRender={() => [
            <Popconfirm
              key="verification-delete"
              title={t('ui.settings.verification.deleteAuthenticator')}
              description={t('ui.settings.verification.deletableAuthenticatorsWillBeDisabledTheBasicPassword')}
              okText={t('ui.settings.verification.confirm')}
              cancelText={t('ui.settings.verification.cancel')}
              onConfirm={() => void tablePack.toolbarProps.onDeleteSelectedAuthenticators()}
            >
              <Button disabled={!tablePack.tableProps.canManageSettings} icon={<DeleteOutlined />}>
                {t('ui.settings.verification.delete')}
              </Button>
            </Popconfirm>,
            <Dropdown
              key="verification-add"
              trigger={['click']}
              menu={{ items: tablePack.toolbarProps.addAuthenticatorItems }}
              placement="bottomRight"
            >
              <Button type="primary" disabled={!tablePack.tableProps.canManageSettings || !tablePack.toolbarProps.addAuthenticatorItems?.length} icon={<PlusOutlined />}>
                {t('ui.settings.verification.add')} <DownOutlined />
              </Button>
            </Dropdown>,
          ]}
        />
      </ManagementPageBody>
      <DormantVerificationForms
        mode={drawerMode}
        verificationForm={verificationForm}
        smsSettingsForm={smsSettingsForm}
        smtpSettingsForm={smtpSettingsForm}
        smtpTestForm={smtpTestForm}
        wechatSettingsForm={wechatSettingsForm}
        passkeySettingsForm={passkeySettingsForm}
      />
      <ManagementDrawer {...drawerProps} />
    </ManagementPage>
  );
};

export default SystemVerificationPage;
