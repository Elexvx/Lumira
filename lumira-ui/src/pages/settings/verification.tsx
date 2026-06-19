import { ManagementTable } from '@/features/management/ManagementTable';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { useResponsive } from '@/hooks/useResponsive';
import { DeleteOutlined, DownOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Dropdown, Form, Popconfirm, theme } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useAuthenticatorManagement } from './components/verification/hooks/useAuthenticatorManagement';
import { DEFAULT_TABLE_PAGE_SIZE } from '@/features/table/proTableRequest';
import { request } from '@/services/common/request';
import { API_OPTS } from '@/utils/errorMessage';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';
import type {
  PasskeySettings,
  SmsVerificationSettings,
  SmtpSettings,
  SmtpTestPayload,
  VerificationSettings,
  WechatLoginSettings,
} from '@/types/api';

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
        ...API_OPTS.NO_REDIRECT,
      }),
    enabled: canViewVerification,
  });
  const smsSettingsQuery = useQuery({
    queryKey: ['sms-verification-settings'],
    queryFn: async () =>
      request<SmsVerificationSettings>('/v1/system/verification/sms-settings', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      }),
    enabled: canViewVerification,
  });
  const smtpSettingsQuery = useQuery({
    queryKey: ['smtp-settings'],
    queryFn: async () =>
      request<SmtpSettings>('/v1/system/smtp-settings', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      }),
    enabled: canViewVerification,
  });
  const wechatSettingsQuery = useQuery({
    queryKey: ['wechat-login-settings'],
    queryFn: async () =>
      request<WechatLoginSettings>('/v1/system/verification/wechat-settings', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      }),
    enabled: canViewVerification,
  });
  const passkeySettingsQuery = useQuery({
    queryKey: ['passkey-settings'],
    queryFn: async () =>
      request<PasskeySettings>('/v1/system/verification/passkey-settings', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      }),
    enabled: canViewVerification,
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

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const resolveDrawerTitle = (mode: 'basic' | 'totp' | 'sms' | 'email' | 'wechat' | 'passkey' | null) => {
  switch (mode) {
    case 'sms':
      return t('短信配置', 'SMS settings');
    case 'email':
      return t('邮箱配置', 'Email settings');
    case 'wechat':
      return t('微信配置', 'WeChat settings');
    case 'passkey':
      return t('通行密钥', 'Passkey');
    case 'totp':
      return t('验证设置', 'Verification settings');
    case 'basic':
      return t('基础配置', 'Basic settings');
    default:
      return t('验证管理', 'Verification management');
  }
};

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
    <ManagementPage title={t('验证管理', 'Verification management')}>
      <ManagementPageBody>
        <ManagementTable
          {...verificationTableProps}
          toolBarRender={() => [
            <Popconfirm
              key="verification-delete"
              title={t('删除认证器', 'Delete authenticator')}
              description={t('可删除的认证器会被停用，基础密码认证器会保留。', 'Deletable authenticators will be disabled. The basic password authenticator will be kept.')}
              okText={t('确认', 'Confirm')}
              cancelText={t('取消', 'Cancel')}
              onConfirm={() => void tablePack.toolbarProps.onDeleteSelectedAuthenticators()}
            >
              <Button disabled={!tablePack.tableProps.canManageSettings} icon={<DeleteOutlined />}>
                {t('删除', 'Delete')}
              </Button>
            </Popconfirm>,
            <Dropdown
              key="verification-add"
              trigger={['click']}
              menu={{ items: tablePack.toolbarProps.addAuthenticatorItems }}
              placement="bottomRight"
            >
              <Button type="primary" disabled={!tablePack.tableProps.canManageSettings || !tablePack.toolbarProps.addAuthenticatorItems?.length} icon={<PlusOutlined />}>
                {t('添加', 'Add')} <DownOutlined />
              </Button>
            </Dropdown>,
          ]}
        />
      </ManagementPageBody>
      <ManagementDrawer {...drawerProps} />
    </ManagementPage>
  );
};

export default SystemVerificationPage;
