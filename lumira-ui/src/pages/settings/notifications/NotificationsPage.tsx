import { type ActionType } from '@ant-design/pro-components';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { StandardDateTimeRangePicker } from '@/components/date/StandardDateTimeRangePicker';
import type { ProColumns } from '@ant-design/pro-components';
import { Form, Input, InputNumber, Select, Switch, Typography, theme, Button, Dropdown, Popconfirm, Descriptions, Space, Tag } from 'antd';
import { CheckOutlined, DeleteOutlined, DownOutlined, PlusOutlined } from '@ant-design/icons';
import { useMemo, useRef } from 'react';
import { useDetailDescriptionsProps } from '@/features/detail/config';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { TableActionBar } from '@/features/table/TableActionBar';
import { buildTableRequest, DEFAULT_TABLE_PAGE_SIZE } from '@/features/table/proTableRequest';
import { requestMessageDeliveryLogs } from '@/services/message/api';
import { useNotificationCenter } from './hooks/useNotificationCenter';
import type { MessageDeliveryLogRecord } from '@/types/api';
import type { SmtpSettings, SmtpTestPayload, WechatOfficialAccountSettings } from '@/types/api';
import type { MessageChannel } from '@/types/api';
import type { FormProps } from 'antd';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';

import { databaseMessage } from '@/i18n/databaseMessage';
import { resolveRuntimeLocale } from '@/i18n/locale';
import { UserAvatar } from '@/components/UserAvatar';

const t = databaseMessage;

type NotificationPublishTargetScope = 'PLATFORM' | 'USER' | 'ROLE';

type NotificationChannelKey = 'INBOX' | 'EMAIL' | 'WECHAT_OFFICIAL';

interface NotificationChannelRecord {
  key: NotificationChannelKey;
  order: number;
  identifier: string;
  type: string;
  title: string;
  description: string;
  enabled: boolean;
  configured: boolean;
}

const TARGET_SCOPE_LABELS: Record<string, string> = {
  PLATFORM: t('ui.settings.notifications.notifications.allUsers'),
  USER: t('ui.settings.notifications.notifications.specificUsers'),
  ROLE: t('ui.settings.notifications.notifications.roleGroups'),
};

const CHANNEL_LABELS: Record<string, { color: string; text: string }> = {
  INBOX: { color: 'blue', text: t('ui.settings.notifications.notifications.inbox') },
  EMAIL: { color: 'purple', text: t('ui.settings.notifications.notifications.email') },
  WECHAT_OFFICIAL: { color: 'green', text: t('ui.settings.notifications.notifications.wechat') },
};

const PUBLISH_STATUS_LABELS: Record<string, { color: string; text: string }> = {
  PUBLISHED: { color: 'green', text: t('ui.settings.notifications.notifications.published') },
  RETRACTED: { color: 'default', text: t('ui.settings.notifications.notifications.retracted') },
};

const SEND_STATUS_LABELS: Record<string, { color: string; text: string }> = {
  SUCCESS: { color: 'green', text: t('ui.settings.notifications.notifications.success') },
  FAILED: { color: 'red', text: t('ui.settings.notifications.notifications.failed') },
  SKIPPED: { color: 'orange', text: t('ui.settings.notifications.notifications.skipped') },
};

const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString(resolveRuntimeLocale(), { hour12: false });
};

const renderTag = (label?: string | null, color = 'default') => {
  if (!label) {
    return '-';
  }
  return <Tag color={color}>{label}</Tag>;
};

const renderEnumTag = (value: string | boolean | undefined | null, labels: Record<string, { color: string; text: string }>) => {
  if (value === undefined || value === null) {
    return '-';
  }
  const item = labels[String(value)];
  return item ? <Tag color={item.color}>{item.text}</Tag> : String(value);
};

const buildRangeSearch = () => ({
  transform: (value: unknown) => {
    if (!Array.isArray(value) || value.length !== 2) {
      return {};
    }
    const [start, end] = value as [{ format: (pattern: string) => string }, { format: (pattern: string) => string }];
    return {
      publishedAtStart: start?.format?.('YYYY-MM-DDTHH:mm:ss'),
      publishedAtEnd: end?.format?.('YYYY-MM-DDTHH:mm:ss'),
    };
  },
});

const resolveSortParams = (sorter?: Record<string, unknown>) => {
  if (!sorter) {
    return {};
  }
  const [entry] = Object.entries(sorter).filter(([, order]) => order === 'ascend' || order === 'descend');
  if (!entry) {
    return {};
  }
  const [sortField, sortOrder] = entry;
  return {
    sortField,
    sortOrder: sortOrder === 'ascend' ? 'ASC' : 'DESC',
  };
};

type MessageLogQuery = Record<string, unknown> & {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  targetScope?: string;
  publishStatus?: string;
  channel?: string;
  sendStatus?: string;
  publishedAtStart?: string;
  publishedAtEnd?: string;
  sortField?: string;
  sortOrder?: string;
};

const adaptDeliveryLogResult = async (params: MessageLogQuery, sorter?: Record<string, unknown>) => {
  const mergedParams = { ...params, ...(sorter ? resolveSortParams(sorter) : {}) } as MessageLogQuery;
  const result = await deliveryLogs(mergedParams);
  const currentPage = Number(params.pageNo || 1);
  const pageSize = Number(params.pageSize || 10);
  const hasMore = result.hasMore === true;
  const boundedTotal = result.total ?? 0;
  const estimatedTotal = hasMore ? Math.max(boundedTotal, currentPage * pageSize + result.records.length + 1) : boundedTotal;
  return { ...result, total: estimatedTotal };
};

const deliveryLogs = (params: MessageLogQuery = {}) =>
  requestMessageDeliveryLogs({
    method: 'GET',
    params,
    autoRedirectOnUnauthorized: false,
    silent: true,
  });

const deliveryLogTableRequest = buildTableRequest((params, sorter) =>
  adaptDeliveryLogResult(params as MessageLogQuery, sorter),
);

const NotificationInboxInfo = ({ descriptionsProps }: { descriptionsProps: Parameters<typeof Descriptions>[0] }) => (
  <Descriptions {...descriptionsProps} bordered column={1}>
    <Descriptions.Item label={t('ui.settings.notifications.notifications.notificationKey')}>inbox_notice</Descriptions.Item>
    <Descriptions.Item label={t('ui.settings.notifications.notifications.status')}>{t('ui.settings.notifications.notifications.enabled')}</Descriptions.Item>
    <Descriptions.Item label={t('ui.settings.notifications.notifications.capabilities')}>{t('ui.settings.notifications.notifications.messageCenterRealtimePushReadStateRetract')}</Descriptions.Item>
    <Descriptions.Item label={t('ui.settings.notifications.notifications.description')}>{t('ui.settings.notifications.notifications.inboxIsTheDefaultNotificationChannelAndDoes')}</Descriptions.Item>
  </Descriptions>
);

const NotificationEmailChannelSettingsForm = ({
  canManageSmtp,
  loadingSmtpSettings,
  testingSmtpSettings,
  smtpFormProps,
  smtpTestFormProps,
  onTestSmtpSettings,
  sectionGap,
}: {
  canManageSmtp: boolean;
  loadingSmtpSettings: boolean;
  testingSmtpSettings: boolean;
  smtpFormProps: FormProps<SmtpSettings>;
  smtpTestFormProps: FormProps<SmtpTestPayload>;
  onTestSmtpSettings: () => void;
  sectionGap: number | [number, number];
}) => (
  <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
    <Form {...smtpFormProps} disabled={loadingSmtpSettings || !canManageSmtp}>
      <Form.Item name="host" label={t('ui.settings.notifications.notifications.smtpHost')} rules={[{ required: true, message: t('ui.settings.notifications.notifications.pleaseEnterTheSmtpHost') }]}>
        <Input placeholder={t('ui.settings.notifications.notifications.eGSmtpExampleCom')} />
      </Form.Item>
      <Form.Item name="port" label={t('ui.settings.notifications.notifications.smtpPort')} rules={[{ required: true, message: t('ui.settings.notifications.notifications.pleaseEnterTheSmtpPort') }]}>
        <InputNumber min={1} max={65535} style={{ width: '100%' }} />
      </Form.Item>
      <Form.Item name="username" label={t('ui.settings.notifications.notifications.smtpUsername')}>
        <Input placeholder={t('ui.settings.notifications.notifications.smtpLoginUsername')} autoComplete="off" />
      </Form.Item>
      <Form.Item name="password" label={t('ui.settings.notifications.notifications.smtpPassword')}>
        <Input.Password placeholder={t('ui.settings.notifications.notifications.leaveBlankToKeepTheCurrentPassword')} autoComplete="new-password" />
      </Form.Item>
      <Form.Item name="from" label={t('ui.settings.notifications.notifications.fromAddress')} rules={[{ required: true, type: 'email', message: t('ui.settings.notifications.notifications.pleaseEnterAValidSenderEmail') }]}>
        <Input placeholder={t('ui.settings.notifications.notifications.eGNoticeExampleCom')} />
      </Form.Item>
      <Form.Item name="authEnabled" label={t('ui.settings.notifications.notifications.smtpAuth')} valuePropName="checked">
        <Switch />
      </Form.Item>
      <Form.Item name="startTlsEnabled" label={t('ui.settings.notifications.notifications.starttls')} valuePropName="checked">
        <Switch />
      </Form.Item>
      <Form.Item name="sslEnabled" label={t('ui.settings.notifications.notifications.ssl')} valuePropName="checked">
        <Switch />
      </Form.Item>
    </Form>
    <Form {...smtpTestFormProps}>
      <Typography.Title level={5} style={{ marginTop: 0 }}>
        {t('ui.settings.notifications.notifications.testSend')}
      </Typography.Title>
      <Form.Item name="toEmail" label={t('ui.settings.notifications.notifications.testRecipientEmail')} rules={[{ required: true, type: 'email', message: t('ui.settings.notifications.notifications.pleaseEnterAValidEmailAddress') }]}>
        <Input placeholder={t('ui.settings.notifications.notifications.enterTheTestRecipientEmail')} />
      </Form.Item>
      <Form.Item name="subject" label={t('ui.settings.notifications.notifications.testSubject')}>
        <Input />
      </Form.Item>
      <Form.Item name="content" label={t('ui.settings.notifications.notifications.testContent')}>
        <Input.TextArea rows={4} />
      </Form.Item>
      <Button loading={testingSmtpSettings} disabled={!canManageSmtp} onClick={onTestSmtpSettings}>
        {t('ui.settings.notifications.notifications.sendTestEmail')}
      </Button>
    </Form>
  </Space>
);

const NotificationWechatOfficialChannelSettingsForm = ({
  canManageSmtp,
  loadingWechatOfficialSettings,
  wechatOfficialAppSecretConfigured,
  formProps,
}: {
  canManageSmtp: boolean;
  loadingWechatOfficialSettings: boolean;
  wechatOfficialAppSecretConfigured: boolean;
  formProps: FormProps<WechatOfficialAccountSettings>;
}) => (
  <Form {...formProps} disabled={loadingWechatOfficialSettings || !canManageSmtp}>
    <Form.Item name="appId" label={t('ui.settings.notifications.notifications.appid')} rules={[{ required: true, message: t('ui.settings.notifications.notifications.pleaseEnterTheOfficialAccountAppid') }]}>
      <Input placeholder={t('ui.settings.notifications.notifications.officialAccountOrServiceAccountAppid')} />
    </Form.Item>
    <Form.Item
      name="appSecret"
      label={t('ui.settings.notifications.notifications.appsecret')}
      rules={!wechatOfficialAppSecretConfigured ? [{ required: true, message: t('ui.settings.notifications.notifications.pleaseEnterTheOfficialAccountAppsecret') }] : undefined}
      extra={wechatOfficialAppSecretConfigured ? t('ui.settings.notifications.notifications.theCurrentSecretIsMaskedLeaveBlankTo') : t('ui.settings.notifications.notifications.usedToObtainTheOfficialAccountAccessToken')}
    >
      <Input.Password placeholder={t('ui.settings.notifications.notifications.leaveBlankToKeepTheExistingSecret')} autoComplete="new-password" />
    </Form.Item>
    <Form.Item name="templateId" label={t('ui.settings.notifications.notifications.templateMessageId')} rules={[{ required: true, message: t('ui.settings.notifications.notifications.pleaseEnterTheTemplateMessageId') }]}>
      <Input placeholder={t('ui.settings.notifications.notifications.templateMessageIdConfiguredInTheOfficialAccount')} />
    </Form.Item>
    <Form.Item name="detailUrl" label={t('ui.settings.notifications.notifications.notificationDetailUrl')} extra={t('ui.settings.notifications.notifications.pageOpenedAfterClickingTheTemplateMessageOptional')}>
      <Input placeholder={t('ui.settings.notifications.notifications.eGHttpsTestExampleComMessages')} />
    </Form.Item>
  </Form>
);

const buildLogColumns = (): ProColumns<MessageDeliveryLogRecord>[] => {
  const columns = [
    {
      title: t('ui.settings.notifications.notifications.keyword'),
      dataIndex: 'keyword',
      hideInTable: true,
      renderFormItem: () => <StandardDateTimeRangePicker style={{ width: '100%' }} />,
    },
    {
      title: t('ui.settings.notifications.notifications.title'),
      dataIndex: 'title',
      ellipsis: true,
      copyable: true,
      search: false,
      render: (_: unknown, record: MessageDeliveryLogRecord) => <Typography.Text strong>{record.title}</Typography.Text>,
    },
    {
      title: t('ui.settings.notifications.notifications.channel'),
      dataIndex: 'channel',
      width: 'var(--saas-spacing-110)',
      valueEnum: { INBOX: { text: t('ui.settings.notifications.notifications.inbox') }, EMAIL: { text: t('ui.settings.notifications.notifications.email') }, WECHAT_OFFICIAL: { text: t('ui.settings.notifications.notifications.wechat') } },
      renderFormItem: () => <Select allowClear options={[{ label: t('ui.settings.notifications.notifications.inbox'), value: 'INBOX' }, { label: t('ui.settings.notifications.notifications.email'), value: 'EMAIL' }, { label: t('ui.settings.notifications.notifications.wechat'), value: 'WECHAT_OFFICIAL' }]} placeholder={t('ui.settings.notifications.notifications.all')} />,
      render: (_: unknown, record: MessageDeliveryLogRecord) => renderEnumTag(record.channel, CHANNEL_LABELS),
    },
    {
      title: t('ui.settings.notifications.notifications.status'),
      dataIndex: 'sendStatus',
      width: 'var(--saas-spacing-110)',
      valueEnum: { SUCCESS: { text: t('ui.settings.notifications.notifications.success') }, FAILED: { text: t('ui.settings.notifications.notifications.failed') }, SKIPPED: { text: t('ui.settings.notifications.notifications.skipped') } },
      renderFormItem: () => <Select allowClear options={[{ label: t('ui.settings.notifications.notifications.success'), value: 'SUCCESS' }, { label: t('ui.settings.notifications.notifications.failed'), value: 'FAILED' }, { label: t('ui.settings.notifications.notifications.skipped'), value: 'SKIPPED' }]} placeholder={t('ui.settings.notifications.notifications.all')} />,
      render: (_: unknown, record: MessageDeliveryLogRecord) => renderEnumTag(record.sendStatus, SEND_STATUS_LABELS),
    },
    {
      title: t('ui.settings.notifications.notifications.targetScope'),
      dataIndex: 'targetScope',
      width: 'var(--saas-spacing-120)',
      renderFormItem: () => <Select allowClear options={[{ label: t('ui.settings.notifications.notifications.allUsers'), value: 'PLATFORM' }, { label: t('ui.settings.notifications.notifications.specificUsers'), value: 'USER' }, { label: t('ui.settings.notifications.notifications.roleGroups'), value: 'ROLE' }]} placeholder={t('ui.settings.notifications.notifications.all')} />,
      render: (_: unknown, record: MessageDeliveryLogRecord) => renderTag(TARGET_SCOPE_LABELS[record.targetScope] || record.targetScope, 'geekblue'),
    },
    {
      title: t('ui.settings.notifications.notifications.recipient'),
      dataIndex: 'targetUserName',
      width: 'var(--saas-spacing-160)',
      search: false,
      responsive: ['lg', 'xl', 'xxl'],
      render: (_: unknown, record: MessageDeliveryLogRecord) => record.targetUserName || (record.targetUserId ? String(record.targetUserId) : '-'),
    },
    {
      title: t('ui.settings.notifications.notifications.recipientIdentifier'),
      dataIndex: 'targetEmail',
      width: 'var(--saas-spacing-220)',
      ellipsis: true,
      search: false,
      responsive: ['lg', 'xl', 'xxl'],
      render: (_: unknown, record: MessageDeliveryLogRecord) => record.targetEmail || '-',
    },
    {
      title: t('ui.settings.notifications.notifications.errorMessage'),
      dataIndex: 'errorMessage',
      ellipsis: true,
      search: false,
      responsive: ['xl', 'xxl'],
      render: (_: unknown, record: MessageDeliveryLogRecord) => record.errorMessage || '-',
    },
    {
      title: t('ui.settings.notifications.notifications.sentAt'),
      dataIndex: 'createdAt',
      width: 'var(--saas-spacing-180)',
      search: false,
      sorter: true,
      render: (_: unknown, record: MessageDeliveryLogRecord) => formatDateTime(record.sentAt || record.createdAt),
    },
    {
      title: t('ui.settings.notifications.notifications.sentAtRange'),
      dataIndex: 'publishedAtRange',
      hideInTable: true,
      renderFormItem: () => <StandardDateTimeRangePicker style={{ width: '100%' }} />,
      search: buildRangeSearch(),
    },
  ] as unknown as ProColumns<MessageDeliveryLogRecord>[];

  return columns;
};

const buildChannelColumns = ({
  isDesktop,
  isMobile,
  canManageSmtp,
  tokenColorSuccess,
  togglingChannelKey,
  setLogOpen,
  handleDisableChannel,
  handleOpenChannelDrawer,
}: {
  isDesktop: boolean;
  isMobile: boolean;
  canManageSmtp: boolean;
  tokenColorSuccess: string;
  togglingChannelKey: NotificationChannelRecord['key'] | null;
  setLogOpen: (open: boolean) => void;
  handleDisableChannel: (record: NotificationChannelRecord) => void;
  handleOpenChannelDrawer: (record: NotificationChannelRecord, mode?: 'detail' | 'edit') => void;
}): ProColumns<NotificationChannelRecord>[] => [
  { title: t('ui.settings.notifications.notifications.no'), dataIndex: 'order', width: 'var(--saas-spacing-80)', search: false },
  { title: t('ui.settings.notifications.notifications.notificationKey'), dataIndex: 'identifier', width: 'var(--saas-spacing-180)', copyable: true, search: false },
  {
    title: t('ui.settings.notifications.notifications.notificationType'),
    dataIndex: 'type',
    width: 'var(--saas-spacing-140)',
    search: false,
    render: (_: unknown, record: NotificationChannelRecord) => renderTag(record.type, CHANNEL_LABELS[record.key]?.color || 'blue'),
  },
  { title: t('ui.settings.notifications.notifications.title'), dataIndex: 'title', width: 'var(--saas-spacing-160)', search: false, render: (_: unknown, record: NotificationChannelRecord) => <Typography.Text strong>{record.title}</Typography.Text> },
  { title: t('ui.settings.notifications.notifications.description.ade57022'), dataIndex: 'description', width: 'var(--saas-spacing-220)', search: false, ellipsis: true },
  {
    title: t('ui.settings.notifications.notifications.enabled.02606e23'),
    dataIndex: 'enabled',
    width: 'var(--saas-spacing-120)',
    align: 'center',
    search: false,
    render: (_: unknown, record: NotificationChannelRecord) => (record.enabled ? <CheckOutlined style={{ color: tokenColorSuccess }} /> : null),
  },
  {
    title: t('ui.settings.notifications.notifications.actions'),
    valueType: 'option',
    width: 'var(--saas-spacing-280)',
    fixed: isDesktop ? 'right' : undefined,
    render: (_: unknown, record: NotificationChannelRecord) => (
      <TableActionBar
        isMobile={isMobile}
        inlineCount={isMobile ? 0 : 4}
        items={[
          {
            key: 'toggle',
            label: t('ui.settings.notifications.notifications.disable'),
            danger: true,
            disabled: !canManageSmtp || record.key === 'INBOX',
            loading: togglingChannelKey === record.key,
            onClick: () => void handleDisableChannel(record),
          },
          {
            key: 'detail',
            label: t('ui.settings.notifications.notifications.details'),
            onClick: () => handleOpenChannelDrawer(record, 'detail'),
          },
          {
            key: 'edit',
            label: t('ui.settings.notifications.notifications.edit'),
            disabled: !canManageSmtp || record.key === 'INBOX',
            onClick: () => handleOpenChannelDrawer(record, 'edit'),
          },
          {
            key: 'logs',
            label: t('ui.settings.notifications.notifications.logs'),
            onClick: () => setLogOpen(true),
          },
          {
            key: 'delete',
            label: t('ui.settings.notifications.notifications.delete'),
            danger: true,
            disabled: !canManageSmtp || record.key === 'INBOX',
            onClick: () => void handleDisableChannel(record),
          },
        ]}
      />
    ),
  },
];

const NotificationsPage = () => {
  const { token } = theme.useToken();
  const logActionRef = useRef<ActionType | undefined>(undefined);
  const { actionPermission, responsive, searchConfig } = usePagePermissionActions();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile);
  const detailDescriptionsProps = useDetailDescriptionsProps({
    column: responsive.isMobile ? 1 : 2,
  });
  const canManualPublish =
    actionPermission.can('message:message:write') ||
    actionPermission.can('system:notification:write');
  const canManageSmtp =
    actionPermission.can('system:config:update') ||
    actionPermission.can('system:verification:manage');
  const canRetractMessage =
    actionPermission.can('message:message:retract') ||
    actionPermission.can('system:notification:write');
  const requestOptions = useMemo(
    () => ({
      autoRedirectOnUnauthorized: false,
      silent: true,
    }),
    [],
  );

  const notificationCenter = useNotificationCenter({
    canManualPublish,
    canManageSmtp,
    canRetractMessage,
    requestOptions,
    onReloadLog: () => logActionRef.current?.reload(),
  });
  const channelDrawerEditing = notificationCenter.channelDrawerMode === 'edit';
  const publishTargetScope = Form.useWatch('targetScope', notificationCenter.publishFormProps.form) as NotificationPublishTargetScope | undefined;
  const channelColumns = useMemo(
    () =>
      buildChannelColumns({
        isDesktop: responsive.isDesktop,
        isMobile: responsive.isMobile,
        canManageSmtp,
        tokenColorSuccess: token.colorSuccess,
        togglingChannelKey: notificationCenter.togglingChannelKey,
        setLogOpen: notificationCenter.setLogOpen,
        handleDisableChannel: notificationCenter.handleDisableChannel,
        handleOpenChannelDrawer: notificationCenter.handleOpenChannelDrawer,
      }),
    [
      canManageSmtp,
      notificationCenter.handleDisableChannel,
      notificationCenter.handleOpenChannelDrawer,
      notificationCenter.setLogOpen,
      notificationCenter.togglingChannelKey,
      responsive.isDesktop,
      responsive.isMobile,
      token.colorSuccess,
    ],
  );
  const logColumns = useMemo(() => buildLogColumns(), []);
  const handlePublishTargetScopeChange = (nextScope: NotificationPublishTargetScope) => {
    if (nextScope === 'PLATFORM') {
      notificationCenter.publishFormProps.form?.setFieldsValue({ targetUserId: undefined, targetRoleId: undefined });
      notificationCenter.setUserOptions([]);
      notificationCenter.setUserLoading(false);
    } else if (nextScope === 'USER') {
      notificationCenter.publishFormProps.form?.setFieldsValue({ targetRoleId: undefined });
      notificationCenter.setRoleLoading(false);
    } else if (nextScope === 'ROLE') {
      notificationCenter.publishFormProps.form?.setFieldsValue({ targetUserId: undefined });
      notificationCenter.setUserOptions([]);
      notificationCenter.setUserLoading(false);
    }
  };

  return (
    <ManagementPage title={t('ui.settings.notifications.notifications.notificationCenter')}>
      <ManagementPageBody>
        <ManagementTable
          rowKey="key"
          columns={channelColumns}
          dataSource={notificationCenter.channelRecords}
          isMobile={responsive.isMobile}
          pagination={{ pageSize: DEFAULT_TABLE_PAGE_SIZE, showSizeChanger: true }}
          search={false}
          tableAlertRender={false}
          rowSelection={{
            selectedRowKeys: notificationCenter.selectedChannelKeys,
            onChange: (selectedRowKeys) =>
              notificationCenter.setSelectedChannelKeys(selectedRowKeys as unknown as MessageChannel[]),
          }}
          toolBarRender={() => [
            <Popconfirm
              key="delete"
              title={t('ui.settings.notifications.notifications.deleteNotificationChannels')}
              description={t('ui.settings.notifications.notifications.theSelectedChannelsWillBeDisabledInboxWill')}
              okText={t('ui.settings.notifications.notifications.confirm')}
              cancelText={t('ui.settings.notifications.notifications.cancel')}
              onConfirm={() => void notificationCenter.handleDeleteSelectedChannels()}
            >
              <Button disabled={!canManageSmtp} icon={<DeleteOutlined />}>
                {t('ui.settings.notifications.notifications.delete')}
              </Button>
            </Popconfirm>,
            <Dropdown key="add" trigger={['click']} menu={{ items: notificationCenter.addChannelItems }} placement="bottomRight">
              <Button type="primary" disabled={!notificationCenter.addChannelItems?.length} icon={<PlusOutlined />}>
                {t('ui.settings.notifications.notifications.add')} <DownOutlined />
              </Button>
            </Dropdown>,
          ]}
        />
      </ManagementPageBody>

      <ManagementDrawer
        title={
          notificationCenter.channelRecord
            ? channelDrawerEditing
              ? t('ui.settings.notifications.notifications.edit.bdf573c5', { title: notificationCenter.channelRecord.title })
              : t('ui.settings.notifications.notifications.details.4a6dd71c', { title: notificationCenter.channelRecord.title })
            : t('ui.settings.notifications.notifications.notificationChannelDetails')
        }
        open={notificationCenter.channelDrawerOpen}
        onClose={notificationCenter.closeChannelDrawer}
        footerActions={
          channelDrawerEditing && notificationCenter.channelRecord?.key === 'EMAIL'
            ? [
                { key: 'cancel', label: t('ui.settings.notifications.notifications.cancel'), onClick: notificationCenter.closeChannelDrawer },
                { key: 'save', label: t('ui.settings.notifications.notifications.save'), type: 'primary' as const, loading: notificationCenter.savingSmtpSettings, disabled: !canManageSmtp, onClick: notificationCenter.handleSaveSmtpSettings },
              ]
            : channelDrawerEditing && notificationCenter.channelRecord?.key === 'WECHAT_OFFICIAL'
              ? [
                  { key: 'cancel', label: t('ui.settings.notifications.notifications.cancel'), onClick: notificationCenter.closeChannelDrawer },
                  { key: 'save', label: t('ui.settings.notifications.notifications.save'), type: 'primary' as const, loading: notificationCenter.savingWechatOfficialSettings, disabled: !canManageSmtp, onClick: notificationCenter.handleSaveWechatOfficialSettings },
                ]
              : undefined
        }
      >
        {notificationCenter.channelRecord?.key === 'INBOX' ? (
          <NotificationInboxInfo descriptionsProps={detailDescriptionsProps} />
        ) : notificationCenter.channelRecord?.key === 'EMAIL' ? (
          <NotificationEmailChannelSettingsForm
            canManageSmtp={channelDrawerEditing && canManageSmtp}
            loadingSmtpSettings={notificationCenter.loadingSmtpSettings}
            testingSmtpSettings={notificationCenter.testingSmtpSettings}
            smtpFormProps={notificationCenter.smtpFormProps}
            smtpTestFormProps={notificationCenter.smtpTestFormProps}
            onTestSmtpSettings={notificationCenter.handleTestSmtpSettings}
            sectionGap={sectionGap}
          />
        ) : (
          <NotificationWechatOfficialChannelSettingsForm
            canManageSmtp={channelDrawerEditing && canManageSmtp}
            loadingWechatOfficialSettings={notificationCenter.loadingWechatOfficialSettings}
            wechatOfficialAppSecretConfigured={notificationCenter.wechatOfficialAppSecretConfigured}
            formProps={notificationCenter.wechatOfficialFormProps}
          />
        )}
      </ManagementDrawer>

      <ManagementDrawer title={t('ui.settings.notifications.notifications.notificationDeliveryLogs')} open={notificationCenter.logOpen} onClose={() => notificationCenter.setLogOpen(false)} destroyOnHidden>
        <ManagementTable<MessageDeliveryLogRecord>
          actionRef={logActionRef}
          rowKey="id"
          columns={logColumns}
          isMobile={responsive.isMobile}
          pagination={{ showSizeChanger: true, pageSize: 10 }}
          search={searchConfig}
          request={deliveryLogTableRequest}
          toolBarRender={false}
        />
      </ManagementDrawer>

      <ManagementDrawer
        title={notificationCenter.detailRecord ? t('ui.settings.notifications.notifications.notificationDetails', { title: notificationCenter.detailRecord.title }) : t('ui.settings.notifications.notifications.notificationDetails.65411537')}
        open={notificationCenter.detailOpen}
        onClose={notificationCenter.handleCloseDetail}
        extra={
          notificationCenter.detailRecord && notificationCenter.detailRecord.publishStatus === 'PUBLISHED' ? (
            <Button danger disabled={!canRetractMessage} onClick={() => void notificationCenter.handleRetract(notificationCenter.detailRecord!)}>
              {t('ui.settings.notifications.notifications.retract')}
            </Button>
          ) : null
        }
      >
        {notificationCenter.detailRecord ? (
          <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
            <Descriptions {...detailDescriptionsProps} bordered>
              <Descriptions.Item label={t('ui.settings.notifications.notifications.title')}>{notificationCenter.detailRecord.title}</Descriptions.Item>
              <Descriptions.Item label={t('ui.settings.notifications.notifications.targetScope')}>{TARGET_SCOPE_LABELS[notificationCenter.detailRecord.targetScope] || notificationCenter.detailRecord.targetScope}</Descriptions.Item>
              <Descriptions.Item label={t('ui.settings.notifications.notifications.targetUser')}>
                {notificationCenter.detailRecord.targetScope === 'USER'
                  ? notificationCenter.detailRecord.targetUserName || (notificationCenter.detailRecord.targetUserId ? String(notificationCenter.detailRecord.targetUserId) : '-')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('ui.settings.notifications.notifications.targetRole')}>
                {notificationCenter.detailRecord.targetScope === 'ROLE'
                  ? notificationCenter.detailRecord.targetRoleName || (notificationCenter.detailRecord.targetRoleId ? String(notificationCenter.detailRecord.targetRoleId) : '-')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('ui.settings.notifications.notifications.status')}>{PUBLISH_STATUS_LABELS[notificationCenter.detailRecord.publishStatus]?.text || notificationCenter.detailRecord.publishStatus}</Descriptions.Item>
              <Descriptions.Item label={t('ui.settings.notifications.notifications.publishedAt')}>{formatDateTime(notificationCenter.detailRecord.publishedAt || notificationCenter.detailRecord.createdAt)}</Descriptions.Item>
              <Descriptions.Item label={t('ui.settings.notifications.notifications.readStatus')}>{notificationCenter.detailRecord.readFlag ? t('ui.settings.notifications.notifications.read') : t('ui.settings.notifications.notifications.unread')}</Descriptions.Item>
              <Descriptions.Item label={t('ui.settings.notifications.notifications.creatorId')}>{notificationCenter.detailRecord.createdBy ?? '-'}</Descriptions.Item>
              <Descriptions.Item label={t('ui.settings.notifications.notifications.updaterId')}>{notificationCenter.detailRecord.updatedBy ?? '-'}</Descriptions.Item>
              <Descriptions.Item label={t('ui.settings.notifications.notifications.readAt')}>{formatDateTime(notificationCenter.detailRecord.readAt)}</Descriptions.Item>
              <Descriptions.Item label={t('ui.settings.notifications.notifications.updatedAt')}>{formatDateTime(notificationCenter.detailRecord.updatedAt)}</Descriptions.Item>
            </Descriptions>
            <div>
              <Typography.Title level={5} style={{ marginTop: 0 }}>
                {t('ui.settings.notifications.notifications.content')}
              </Typography.Title>
              <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>{notificationCenter.detailRecord.content}</Typography.Paragraph>
            </div>
          </Space>
        ) : null}
      </ManagementDrawer>

      <ManagementDrawer
        title={t('ui.settings.notifications.notifications.manuallyPublishNotification')}
        open={notificationCenter.publishOpen}
        onClose={notificationCenter.closePublishDrawer}
        footerActions={[
          { key: 'cancel', label: t('ui.settings.notifications.notifications.cancel'), onClick: notificationCenter.closePublishDrawer },
          {
            key: 'publish',
            label: t('ui.settings.notifications.notifications.send'),
            type: 'primary',
            loading: notificationCenter.publishing,
            disabled: !canManualPublish,
            onClick: () => void notificationCenter.handlePublish(),
          },
        ]}
      >
        <Form
          {...notificationCenter.publishFormProps}
          onValuesChange={(changedValues) => {
            if (Object.prototype.hasOwnProperty.call(changedValues, 'targetScope')) {
              handlePublishTargetScopeChange(changedValues.targetScope as NotificationPublishTargetScope);
            }
          }}
        >
          <Form.Item name="channels" label={t('ui.settings.notifications.notifications.notificationChannels')} rules={[{ required: true, message: t('ui.settings.notifications.notifications.pleaseSelectAtLeastOneChannel') }]}>
            <Select
              mode="multiple"
              options={[
                { label: t('ui.settings.notifications.notifications.inbox'), value: 'INBOX' },
                { label: t('ui.settings.notifications.notifications.email'), value: 'EMAIL' },
                { label: t('ui.settings.notifications.notifications.wechat'), value: 'WECHAT_OFFICIAL' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="title"
            label={t('ui.settings.notifications.notifications.notificationTitle')}
            rules={[{ required: true, message: t('ui.settings.notifications.notifications.pleaseEnterANotificationTitle') }, { max: 128, message: t('ui.settings.notifications.notifications.theTitleCannotExceed128Characters') }]}
          >
            <Input placeholder={t('ui.settings.notifications.notifications.eGSystemMaintenanceNotice')} />
          </Form.Item>
          <Form.Item
            name="content"
            label={t('ui.settings.notifications.notifications.notificationContent')}
            rules={[{ required: true, message: t('ui.settings.notifications.notifications.pleaseEnterNotificationContent') }, { max: 2000, message: t('ui.settings.notifications.notifications.theContentCannotExceed2000Characters') }]}
          >
            <Input.TextArea rows={8} placeholder={t('ui.settings.notifications.notifications.enterTheContentToSendToUsers')} />
          </Form.Item>
          <Form.Item name="targetScope" label={t('ui.settings.notifications.notifications.targetScope')} rules={[{ required: true, message: t('ui.settings.notifications.notifications.pleaseSelectATargetScope') }]}>
            <Select options={[{ label: t('ui.settings.notifications.notifications.allUsers'), value: 'PLATFORM' }, { label: t('ui.settings.notifications.notifications.specificUsers'), value: 'USER' }, { label: t('ui.settings.notifications.notifications.roleGroups'), value: 'ROLE' }]} />
          </Form.Item>
          {publishTargetScope === 'USER' ? (
            <Form.Item
              name="targetUserId"
              label={t('ui.settings.notifications.notifications.targetUsername')}
              rules={[
                {
                  validator: async (_, value) => {
                    if (publishTargetScope === 'USER' && !value) {
                      throw new Error(t('ui.settings.notifications.notifications.pleaseSelectATargetUsername'));
                    }
                  },
                },
              ]}
            >
              <Select
                allowClear
                showSearch
                filterOption={false}
                loading={notificationCenter.userLoading}
                placeholder={t('ui.settings.notifications.notifications.searchByUsername')}
                searchValue={notificationCenter.userSearch}
                onSearch={(value) => notificationCenter.setUserSearch(value.trim())}
                options={notificationCenter.userOptions.map((item) => {
                  const displayName = item.realName || item.nickname;
                  return {
                    label: (
                      <Space size="small" wrap={false}>
                        <UserAvatar
                          size="small"
                          avatarUrl={item.avatarUrl}
                          userId={item.id}
                          userUuid={item.userUuid || item.uid}
                          username={item.username}
                        />
                        <span>{displayName ? `${item.username} · ${displayName}` : item.username}</span>
                      </Space>
                    ),
                    value: item.id,
                  };
                })}
                notFoundContent={notificationCenter.userLoading ? t('ui.settings.notifications.notifications.loading') : t('ui.settings.notifications.notifications.noMatchingUsers')}
              />
            </Form.Item>
          ) : null}
          {publishTargetScope === 'ROLE' ? (
            <Form.Item
              name="targetRoleId"
              label={t('ui.settings.notifications.notifications.roleGroups')}
              rules={[
                {
                  validator: async (_, value) => {
                    if (publishTargetScope === 'ROLE' && !value) {
                      throw new Error(t('ui.settings.notifications.notifications.pleaseSelectARoleGroup'));
                    }
                  },
                },
              ]}
            >
              <Select
                allowClear
                showSearch
                loading={notificationCenter.roleLoading}
                placeholder={t('ui.settings.notifications.notifications.selectARoleGroup')}
                filterOption={(input, option) => String(option?.label || '').toLowerCase().includes(input.toLowerCase())}
                options={notificationCenter.roleOptions.map((item) => ({ label: item.roleName, value: item.id }))}
                notFoundContent={notificationCenter.roleLoading ? t('ui.settings.notifications.notifications.loading') : t('ui.settings.notifications.notifications.noRoles')}
              />
            </Form.Item>
          ) : null}
        </Form>
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default NotificationsPage;
