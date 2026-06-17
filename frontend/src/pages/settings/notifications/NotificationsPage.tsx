import { type ActionType } from '@ant-design/pro-components';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import type { ProColumns } from '@ant-design/pro-components';
import { DatePicker, Form, Input, InputNumber, Select, Switch, Typography, theme, Button, Dropdown, Popconfirm, Descriptions, Space, Tag } from 'antd';
import { CheckOutlined, DeleteOutlined, DownOutlined, PlusOutlined } from '@ant-design/icons';
import { useMemo, useRef } from 'react';
import { useDetailDescriptionsProps } from '@/features/detail/config';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { TableActionBar } from '@/features/table/TableActionBar';
import { buildTableRequest, DEFAULT_TABLE_PAGE_SIZE } from '@/features/table/proTableRequest';
import { requestMessageArchive, requestMessageDeliveryLogs } from '@/services/message/api';
import { useNotificationCenter } from './hooks/useNotificationCenter';
import type { MessageDeliveryLogRecord, MessageNoticeRecord } from '@/types/api';
import type { SmtpSettings, SmtpTestPayload, WechatOfficialAccountSettings } from '@/types/api';
import type { MessageChannel } from '@/types/api';
import type { FormProps } from 'antd';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

type NotificationPublishTargetScope = 'TENANT' | 'USER' | 'ROLE';

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
  TENANT: t('全员', 'All users'),
  USER: t('指定用户', 'Specific users'),
  ROLE: t('角色分组', 'Role groups'),
};

const CHANNEL_LABELS: Record<string, { color: string; text: string }> = {
  INBOX: { color: 'blue', text: t('站内信', 'Inbox') },
  EMAIL: { color: 'purple', text: t('邮箱', 'Email') },
  WECHAT_OFFICIAL: { color: 'green', text: t('微信', 'WeChat') },
};

const PUBLISH_STATUS_LABELS: Record<string, { color: string; text: string }> = {
  PUBLISHED: { color: 'green', text: t('已发布', 'Published') },
  RETRACTED: { color: 'default', text: t('已撤回', 'Retracted') },
};

const SEND_STATUS_LABELS: Record<string, { color: string; text: string }> = {
  SUCCESS: { color: 'green', text: t('成功', 'Success') },
  FAILED: { color: 'red', text: t('失败', 'Failed') },
  SKIPPED: { color: 'orange', text: t('跳过', 'Skipped') },
};

const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString(isEnglishLocale() ? 'en-US' : 'zh-CN', { hour12: false });
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

type MessageArchiveQuery = Record<string, unknown> & {
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

const archiveMessages = (params: MessageArchiveQuery = {}) =>
  requestMessageArchive({
    method: 'GET',
    params,
    autoRedirectOnUnauthorized: false,
    silent: true,
  });

const adaptArchiveResult = async (params: MessageArchiveQuery) => {
  const result = await archiveMessages(params);
  const currentPage = Number(params.pageNo || 1);
  const pageSize = Number(params.pageSize || 10);
  const hasMore = result.hasMore === true;
  const boundedTotal = result.total ?? 0;
  const estimatedTotal = hasMore ? Math.max(boundedTotal, currentPage * pageSize + result.records.length + 1) : boundedTotal;
  return { ...result, total: estimatedTotal };
};

const adaptDeliveryLogResult = async (params: MessageArchiveQuery, sorter?: Record<string, unknown>) => {
  const mergedParams = { ...params, ...(sorter ? resolveSortParams(sorter) : {}) } as MessageArchiveQuery;
  const result = await deliveryLogs(mergedParams);
  const currentPage = Number(params.pageNo || 1);
  const pageSize = Number(params.pageSize || 10);
  const hasMore = result.hasMore === true;
  const boundedTotal = result.total ?? 0;
  const estimatedTotal = hasMore ? Math.max(boundedTotal, currentPage * pageSize + result.records.length + 1) : boundedTotal;
  return { ...result, total: estimatedTotal };
};

const deliveryLogs = (params: MessageArchiveQuery = {}) =>
  requestMessageDeliveryLogs({
    method: 'GET',
    params,
    autoRedirectOnUnauthorized: false,
    silent: true,
  });

const NotificationInboxInfo = ({ descriptionsProps }: { descriptionsProps: Parameters<typeof Descriptions>[0] }) => (
  <Descriptions {...descriptionsProps} bordered column={1}>
    <Descriptions.Item label={t('通知标识', 'Notification key')}>inbox_notice</Descriptions.Item>
    <Descriptions.Item label={t('状态', 'Status')}>{t('已启用', 'Enabled')}</Descriptions.Item>
    <Descriptions.Item label={t('能力', 'Capabilities')}>{t('消息中心、实时推送、已读状态、撤回', 'Message center, realtime push, read state, retract')}</Descriptions.Item>
    <Descriptions.Item label={t('说明', 'Description')}>{t('站内信是系统默认通知渠道，无需额外配置。', 'Inbox is the default notification channel and does not require extra configuration.')}</Descriptions.Item>
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
      <Form.Item name="host" label={t('SMTP 主机', 'SMTP host')} rules={[{ required: true, message: t('请输入 SMTP 主机', 'Please enter the SMTP host') }]}>
        <Input placeholder={t('例如：smtp.example.com', 'e.g. smtp.example.com')} />
      </Form.Item>
      <Form.Item name="port" label={t('SMTP 端口', 'SMTP port')} rules={[{ required: true, message: t('请输入 SMTP 端口', 'Please enter the SMTP port') }]}>
        <InputNumber min={1} max={65535} style={{ width: '100%' }} />
      </Form.Item>
      <Form.Item name="username" label={t('SMTP 用户名', 'SMTP username')}>
        <Input placeholder={t('SMTP 登录用户名', 'SMTP login username')} autoComplete="off" />
      </Form.Item>
      <Form.Item name="password" label={t('SMTP 密码', 'SMTP password')}>
        <Input.Password placeholder={t('留空则保持现有密码', 'Leave blank to keep the current password')} autoComplete="new-password" />
      </Form.Item>
      <Form.Item name="from" label={t('发件人地址', 'From address')} rules={[{ required: true, type: 'email', message: t('请输入有效发件人邮箱', 'Please enter a valid sender email') }]}>
        <Input placeholder={t('例如：notice@example.com', 'e.g. notice@example.com')} />
      </Form.Item>
      <Form.Item name="authEnabled" label={t('SMTP 认证', 'SMTP auth')} valuePropName="checked">
        <Switch />
      </Form.Item>
      <Form.Item name="startTlsEnabled" label={t('STARTTLS', 'STARTTLS')} valuePropName="checked">
        <Switch />
      </Form.Item>
      <Form.Item name="sslEnabled" label={t('SSL', 'SSL')} valuePropName="checked">
        <Switch />
      </Form.Item>
    </Form>
    <Form {...smtpTestFormProps}>
      <Typography.Title level={5} style={{ marginTop: 0 }}>
        {t('测试发送', 'Test send')}
      </Typography.Title>
      <Form.Item name="toEmail" label={t('测试收件邮箱', 'Test recipient email')} rules={[{ required: true, type: 'email', message: t('请输入有效邮箱地址', 'Please enter a valid email address') }]}>
        <Input placeholder={t('请输入测试收件邮箱', 'Enter the test recipient email')} />
      </Form.Item>
      <Form.Item name="subject" label={t('测试主题', 'Test subject')}>
        <Input />
      </Form.Item>
      <Form.Item name="content" label={t('测试内容', 'Test content')}>
        <Input.TextArea rows={4} />
      </Form.Item>
      <Button loading={testingSmtpSettings} disabled={!canManageSmtp} onClick={onTestSmtpSettings}>
        {t('发送测试邮件', 'Send test email')}
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
    <Form.Item name="appId" label={t('AppID', 'AppID')} rules={[{ required: true, message: t('请输入微信公众号 AppID', 'Please enter the official account AppID') }]}>
      <Input placeholder={t('微信公众号或服务号 AppID', 'Official account or service account AppID')} />
    </Form.Item>
    <Form.Item
      name="appSecret"
      label={t('AppSecret', 'AppSecret')}
      rules={!wechatOfficialAppSecretConfigured ? [{ required: true, message: t('请输入微信公众号 AppSecret', 'Please enter the official account AppSecret') }] : undefined}
      extra={wechatOfficialAppSecretConfigured ? t('当前密钥已脱敏显示，留空则保持现有密钥', 'The current secret is masked. Leave blank to keep the existing one.') : t('用于获取公众号 access_token', 'Used to obtain the official account access_token')}
    >
      <Input.Password placeholder={t('留空则保持现有密钥', 'Leave blank to keep the existing secret')} autoComplete="new-password" />
    </Form.Item>
    <Form.Item name="templateId" label={t('模板消息 ID', 'Template message ID')} rules={[{ required: true, message: t('请输入模板消息 ID', 'Please enter the template message ID') }]}>
      <Input placeholder={t('公众号后台配置的模板消息 ID', 'Template message ID configured in the official account admin console')} />
    </Form.Item>
    <Form.Item name="detailUrl" label={t('通知详情链接', 'Notification detail URL')} extra={t('模板消息点击后打开的页面，可留空', 'Page opened after clicking the template message, optional')}>
      <Input placeholder={t('例如：https://saas.elexvx.com/messages', 'e.g. https://test.example.com/messages')} />
    </Form.Item>
  </Form>
);

const buildArchiveColumns = ({
  canRetractMessage,
  handleOpenDetail,
  handleRetract,
}: {
  canRetractMessage: boolean;
  handleOpenDetail: (record: MessageNoticeRecord) => void;
  handleRetract: (record: MessageNoticeRecord) => Promise<void>;
}): ProColumns<MessageNoticeRecord>[] => {
  const columns = [
    {
      title: t('关键字', 'Keyword'),
      dataIndex: 'keyword',
      hideInTable: true,
      renderFormItem: () => <DatePicker.RangePicker showTime style={{ width: '100%' }} />,
    },
    {
      title: t('标题', 'Title'),
      dataIndex: 'title',
      ellipsis: true,
      copyable: true,
      search: false,
      render: (_: unknown, record: MessageNoticeRecord) => <Typography.Text strong>{record.title}</Typography.Text>,
    },
    {
      title: t('目标范围', 'Target scope'),
      dataIndex: 'targetScope',
      width: 'var(--saas-spacing-120)',
      valueEnum: { TENANT: { text: t('全员', 'All users') }, USER: { text: t('指定用户', 'Specific users') }, ROLE: { text: t('角色分组', 'Role groups') } },
      renderFormItem: () => <Select allowClear options={[{ label: t('全员', 'All users'), value: 'TENANT' }, { label: t('指定用户', 'Specific users'), value: 'USER' }, { label: t('角色分组', 'Role groups'), value: 'ROLE' }]} placeholder={t('全部', 'All')} />,
      render: (_: unknown, record: MessageNoticeRecord) => renderTag(TARGET_SCOPE_LABELS[record.targetScope] || record.targetScope, 'geekblue'),
    },
    {
      title: t('目标用户', 'Target user'),
      dataIndex: 'targetUserName',
      width: 'var(--saas-spacing-160)',
      search: false,
      responsive: ['lg', 'xl', 'xxl'],
      render: (_: unknown, record: MessageNoticeRecord) => (record.targetScope === 'USER' ? record.targetUserName || (record.targetUserId ? String(record.targetUserId) : '-') : '-'),
    },
    {
      title: t('目标角色', 'Target role'),
      dataIndex: 'targetRoleName',
      width: 'var(--saas-spacing-160)',
      search: false,
      responsive: ['lg', 'xl', 'xxl'],
      render: (_: unknown, record: MessageNoticeRecord) => (record.targetScope === 'ROLE' ? record.targetRoleName || (record.targetRoleId ? String(record.targetRoleId) : '-') : '-'),
    },
    {
      title: t('状态', 'Status'),
      dataIndex: 'publishStatus',
      width: 'var(--saas-spacing-110)',
      valueEnum: { PUBLISHED: { text: t('已发布', 'Published') }, RETRACTED: { text: t('已撤回', 'Retracted') } },
      renderFormItem: () => <Select allowClear options={[{ label: t('已发布', 'Published'), value: 'PUBLISHED' }, { label: t('已撤回', 'Retracted'), value: 'RETRACTED' }]} placeholder={t('全部', 'All')} />,
      render: (_: unknown, record: MessageNoticeRecord) => renderEnumTag(record.publishStatus, PUBLISH_STATUS_LABELS),
    },
    {
      title: t('发布时间', 'Published at'),
      dataIndex: 'publishedAt',
      width: 'var(--saas-spacing-180)',
      search: false,
      sorter: true,
      render: (_: unknown, record: MessageNoticeRecord) => formatDateTime(record.publishedAt || record.createdAt),
    },
    {
      title: t('发布时间范围', 'Published at range'),
      dataIndex: 'publishedAtRange',
      hideInTable: true,
      renderFormItem: () => <DatePicker.RangePicker showTime style={{ width: '100%' }} />,
      search: buildRangeSearch(),
    },
    {
      title: t('操作', 'Actions'),
      valueType: 'option',
      width: 'var(--saas-spacing-160)',
      render: (_: unknown, record: MessageNoticeRecord) => (
        <TableActionBar
          isMobile={false}
          items={[
            { key: 'detail', label: t('详情', 'Details'), onClick: () => handleOpenDetail(record) },
            { key: 'retract', label: t('撤回', 'Retract'), danger: true, hidden: record.publishStatus === 'RETRACTED' || !canRetractMessage, onClick: () => void handleRetract(record) },
          ]}
        />
      ),
    },
  ] as unknown as ProColumns<MessageNoticeRecord>[];

  return columns;
};

const buildLogColumns = (): ProColumns<MessageDeliveryLogRecord>[] => {
  const columns = [
    {
      title: t('关键字', 'Keyword'),
      dataIndex: 'keyword',
      hideInTable: true,
      renderFormItem: () => <DatePicker.RangePicker showTime style={{ width: '100%' }} />,
    },
    {
      title: t('标题', 'Title'),
      dataIndex: 'title',
      ellipsis: true,
      copyable: true,
      search: false,
      render: (_: unknown, record: MessageDeliveryLogRecord) => <Typography.Text strong>{record.title}</Typography.Text>,
    },
    {
      title: t('渠道', 'Channel'),
      dataIndex: 'channel',
      width: 'var(--saas-spacing-110)',
      valueEnum: { INBOX: { text: t('站内信', 'Inbox') }, EMAIL: { text: t('邮箱', 'Email') }, WECHAT_OFFICIAL: { text: t('微信', 'WeChat') } },
      renderFormItem: () => <Select allowClear options={[{ label: t('站内信', 'Inbox'), value: 'INBOX' }, { label: t('邮箱', 'Email'), value: 'EMAIL' }, { label: t('微信', 'WeChat'), value: 'WECHAT_OFFICIAL' }]} placeholder={t('全部', 'All')} />,
      render: (_: unknown, record: MessageDeliveryLogRecord) => renderEnumTag(record.channel, CHANNEL_LABELS),
    },
    {
      title: t('状态', 'Status'),
      dataIndex: 'sendStatus',
      width: 'var(--saas-spacing-110)',
      valueEnum: { SUCCESS: { text: t('成功', 'Success') }, FAILED: { text: t('失败', 'Failed') }, SKIPPED: { text: t('跳过', 'Skipped') } },
      renderFormItem: () => <Select allowClear options={[{ label: t('成功', 'Success'), value: 'SUCCESS' }, { label: t('失败', 'Failed'), value: 'FAILED' }, { label: t('跳过', 'Skipped'), value: 'SKIPPED' }]} placeholder={t('全部', 'All')} />,
      render: (_: unknown, record: MessageDeliveryLogRecord) => renderEnumTag(record.sendStatus, SEND_STATUS_LABELS),
    },
    {
      title: t('目标范围', 'Target scope'),
      dataIndex: 'targetScope',
      width: 'var(--saas-spacing-120)',
      renderFormItem: () => <Select allowClear options={[{ label: t('全员', 'All users'), value: 'TENANT' }, { label: t('指定用户', 'Specific users'), value: 'USER' }, { label: t('角色分组', 'Role groups'), value: 'ROLE' }]} placeholder={t('全部', 'All')} />,
      render: (_: unknown, record: MessageDeliveryLogRecord) => renderTag(TARGET_SCOPE_LABELS[record.targetScope] || record.targetScope, 'geekblue'),
    },
    {
      title: t('收件人', 'Recipient'),
      dataIndex: 'targetUserName',
      width: 'var(--saas-spacing-160)',
      search: false,
      responsive: ['lg', 'xl', 'xxl'],
      render: (_: unknown, record: MessageDeliveryLogRecord) => record.targetUserName || (record.targetUserId ? String(record.targetUserId) : '-'),
    },
    {
      title: t('接收标识', 'Recipient identifier'),
      dataIndex: 'targetEmail',
      width: 'var(--saas-spacing-220)',
      ellipsis: true,
      search: false,
      responsive: ['lg', 'xl', 'xxl'],
      render: (_: unknown, record: MessageDeliveryLogRecord) => record.targetEmail || '-',
    },
    {
      title: t('错误信息', 'Error message'),
      dataIndex: 'errorMessage',
      ellipsis: true,
      search: false,
      responsive: ['xl', 'xxl'],
      render: (_: unknown, record: MessageDeliveryLogRecord) => record.errorMessage || '-',
    },
    {
      title: t('发送时间', 'Sent at'),
      dataIndex: 'createdAt',
      width: 'var(--saas-spacing-180)',
      search: false,
      sorter: true,
      render: (_: unknown, record: MessageDeliveryLogRecord) => formatDateTime(record.sentAt || record.createdAt),
    },
    {
      title: t('发送时间范围', 'Sent at range'),
      dataIndex: 'publishedAtRange',
      hideInTable: true,
      renderFormItem: () => <DatePicker.RangePicker showTime style={{ width: '100%' }} />,
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
  setArchiveOpen,
  setLogOpen,
  handleDisableChannel,
  handleOpenChannelDrawer,
}: {
  isDesktop: boolean;
  isMobile: boolean;
  canManageSmtp: boolean;
  tokenColorSuccess: string;
  togglingChannelKey: NotificationChannelRecord['key'] | null;
  setArchiveOpen: (open: boolean) => void;
  setLogOpen: (open: boolean) => void;
  handleDisableChannel: (record: NotificationChannelRecord) => void;
  handleOpenChannelDrawer: (record: NotificationChannelRecord) => void;
}): ProColumns<NotificationChannelRecord>[] => [
  { title: t('序号', 'No.'), dataIndex: 'order', width: 'var(--saas-spacing-80)', search: false },
  { title: t('通知标识', 'Notification key'), dataIndex: 'identifier', width: 'var(--saas-spacing-180)', copyable: true, search: false },
  {
    title: t('通知类型', 'Notification type'),
    dataIndex: 'type',
    width: 'var(--saas-spacing-140)',
    search: false,
    render: (_: unknown, record: NotificationChannelRecord) => renderTag(record.type, CHANNEL_LABELS[record.key]?.color || 'blue'),
  },
  { title: t('标题', 'Title'), dataIndex: 'title', width: 'var(--saas-spacing-160)', search: false, render: (_: unknown, record: NotificationChannelRecord) => <Typography.Text strong>{record.title}</Typography.Text> },
  { title: t('描述', 'Description'), dataIndex: 'description', width: 'var(--saas-spacing-220)', search: false, ellipsis: true },
  {
    title: t('启用', 'Enabled'),
    dataIndex: 'enabled',
    width: 'var(--saas-spacing-120)',
    align: 'center',
    search: false,
    render: (_: unknown, record: NotificationChannelRecord) => (record.enabled ? <CheckOutlined style={{ color: tokenColorSuccess }} /> : null),
  },
  {
    title: t('操作', 'Actions'),
    valueType: 'option',
    width: 'var(--saas-spacing-240)',
    fixed: isDesktop ? 'right' : undefined,
    render: (_: unknown, record: NotificationChannelRecord) => (
      <TableActionBar
        isMobile={isMobile}
        inlineCount={isMobile ? 0 : 3}
        items={[
          {
            key: 'toggle',
            label: t('禁用', 'Disable'),
            danger: true,
            disabled: !canManageSmtp || record.key === 'INBOX',
            loading: togglingChannelKey === record.key,
            onClick: () => void handleDisableChannel(record),
          },
          {
            key: 'config',
            label: t('配置', 'Configure'),
            onClick: () => handleOpenChannelDrawer(record),
          },
          {
            key: 'logs',
            label: t('日志', 'Logs'),
            onClick: () => setLogOpen(true),
          },
          {
            key: 'archive',
            label: t('归档', 'Archive'),
            hidden: record.key !== 'INBOX',
            onClick: () => setArchiveOpen(true),
          },
          {
            key: 'delete',
            label: t('删除', 'Delete'),
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
  const archiveActionRef = useRef<ActionType | undefined>(undefined);
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
    onReloadArchive: () => archiveActionRef.current?.reload(),
    onReloadLog: () => logActionRef.current?.reload(),
  });
  const publishTargetScope = Form.useWatch('targetScope', notificationCenter.publishFormProps.form) as NotificationPublishTargetScope | undefined;
  const channelColumns = useMemo(
    () =>
      buildChannelColumns({
        isDesktop: responsive.isDesktop,
        isMobile: responsive.isMobile,
        canManageSmtp,
        tokenColorSuccess: token.colorSuccess,
        togglingChannelKey: notificationCenter.togglingChannelKey,
        setArchiveOpen: notificationCenter.setArchiveOpen,
        setLogOpen: notificationCenter.setLogOpen,
        handleDisableChannel: notificationCenter.handleDisableChannel,
        handleOpenChannelDrawer: notificationCenter.handleOpenChannelDrawer,
      }),
    [
      canManageSmtp,
      notificationCenter.handleDisableChannel,
      notificationCenter.handleOpenChannelDrawer,
      notificationCenter.setArchiveOpen,
      notificationCenter.setLogOpen,
      notificationCenter.togglingChannelKey,
      responsive.isDesktop,
      responsive.isMobile,
      token.colorSuccess,
    ],
  );
  const archiveColumns = useMemo(
    () =>
      buildArchiveColumns({
        canRetractMessage,
        handleOpenDetail: notificationCenter.handleOpenDetail,
        handleRetract: notificationCenter.handleRetract,
      }),
    [canRetractMessage, notificationCenter.handleOpenDetail, notificationCenter.handleRetract],
  );
  const logColumns = useMemo(() => buildLogColumns(), []);
  const handlePublishTargetScopeChange = (nextScope: NotificationPublishTargetScope) => {
    if (nextScope === 'TENANT') {
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
    <ManagementPage title={t('通知中心', 'Notification center')}>
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
              title={t('删除通知渠道', 'Delete notification channels')}
              description={t('选中的通知渠道会被停用，站内信会保留。', 'The selected channels will be disabled. Inbox will be kept.')}
              okText={t('确认', 'Confirm')}
              cancelText={t('取消', 'Cancel')}
              onConfirm={() => void notificationCenter.handleDeleteSelectedChannels()}
            >
              <Button disabled={!canManageSmtp} icon={<DeleteOutlined />}>
                {t('删除', 'Delete')}
              </Button>
            </Popconfirm>,
            <Dropdown key="add" trigger={['click']} menu={{ items: notificationCenter.addChannelItems }} placement="bottomRight">
              <Button type="primary" disabled={!notificationCenter.addChannelItems?.length} icon={<PlusOutlined />}>
                {t('添加', 'Add')} <DownOutlined />
              </Button>
            </Dropdown>,
          ]}
        />
      </ManagementPageBody>

      <ManagementDrawer
        title={notificationCenter.channelRecord ? t(`配置${notificationCenter.channelRecord.title}`, `Configure ${notificationCenter.channelRecord.title}`) : t('配置通知渠道', 'Configure notification channel')}
        open={notificationCenter.channelDrawerOpen}
        onClose={notificationCenter.closeChannelDrawer}
        footerActions={
          notificationCenter.channelRecord?.key === 'EMAIL'
            ? [
                { key: 'cancel', label: t('取消', 'Cancel'), onClick: notificationCenter.closeChannelDrawer },
                { key: 'save', label: t('保存', 'Save'), type: 'primary' as const, loading: notificationCenter.savingSmtpSettings, disabled: !canManageSmtp, onClick: notificationCenter.handleSaveSmtpSettings },
              ]
            : notificationCenter.channelRecord?.key === 'WECHAT_OFFICIAL'
              ? [
                  { key: 'cancel', label: t('取消', 'Cancel'), onClick: notificationCenter.closeChannelDrawer },
                  { key: 'save', label: t('保存', 'Save'), type: 'primary' as const, loading: notificationCenter.savingWechatOfficialSettings, disabled: !canManageSmtp, onClick: notificationCenter.handleSaveWechatOfficialSettings },
                ]
              : undefined
        }
      >
        {notificationCenter.channelRecord?.key === 'INBOX' ? (
          <NotificationInboxInfo descriptionsProps={detailDescriptionsProps} />
        ) : notificationCenter.channelRecord?.key === 'EMAIL' ? (
          <NotificationEmailChannelSettingsForm
            canManageSmtp={canManageSmtp}
            loadingSmtpSettings={notificationCenter.loadingSmtpSettings}
            testingSmtpSettings={notificationCenter.testingSmtpSettings}
            smtpFormProps={notificationCenter.smtpFormProps}
            smtpTestFormProps={notificationCenter.smtpTestFormProps}
            onTestSmtpSettings={notificationCenter.handleTestSmtpSettings}
            sectionGap={sectionGap}
          />
        ) : (
          <NotificationWechatOfficialChannelSettingsForm
            canManageSmtp={canManageSmtp}
            loadingWechatOfficialSettings={notificationCenter.loadingWechatOfficialSettings}
            wechatOfficialAppSecretConfigured={notificationCenter.wechatOfficialAppSecretConfigured}
            formProps={notificationCenter.wechatOfficialFormProps}
          />
        )}
      </ManagementDrawer>

      <ManagementDrawer title={t('通知发送日志', 'Notification delivery logs')} open={notificationCenter.logOpen} onClose={() => notificationCenter.setLogOpen(false)} destroyOnHidden>
        <ManagementTable<MessageDeliveryLogRecord>
          actionRef={logActionRef}
          rowKey="id"
          columns={logColumns}
          isMobile={responsive.isMobile}
          pagination={{ showSizeChanger: true, pageSize: 10 }}
          search={searchConfig}
          request={buildTableRequest((params, sorter) => adaptDeliveryLogResult(params as MessageArchiveQuery, sorter))}
          toolBarRender={false}
        />
      </ManagementDrawer>

      <ManagementDrawer title={t('通知归档', 'Notification archive')} open={notificationCenter.archiveOpen} onClose={() => notificationCenter.setArchiveOpen(false)} destroyOnHidden>
        <ManagementTable<MessageNoticeRecord>
          actionRef={archiveActionRef}
          rowKey="id"
          columns={archiveColumns}
          isMobile={responsive.isMobile}
          pagination={{ showSizeChanger: true, pageSize: 10 }}
          search={searchConfig}
          request={buildTableRequest((params, sorter) =>
            adaptArchiveResult({ ...params, ...resolveSortParams(sorter) } as MessageArchiveQuery),
          )}
          toolBarRender={false}
        />
      </ManagementDrawer>

      <ManagementDrawer
        title={notificationCenter.detailRecord ? t(`通知详情 · ${notificationCenter.detailRecord.title}`, `Notification details · ${notificationCenter.detailRecord.title}`) : t('通知详情', 'Notification details')}
        open={notificationCenter.detailOpen}
        onClose={notificationCenter.handleCloseDetail}
        extra={
          notificationCenter.detailRecord && notificationCenter.detailRecord.publishStatus === 'PUBLISHED' ? (
            <Button danger disabled={!canRetractMessage} onClick={() => void notificationCenter.handleRetract(notificationCenter.detailRecord!)}>
              {t('撤回', 'Retract')}
            </Button>
          ) : null
        }
      >
        {notificationCenter.detailRecord ? (
          <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
            <Descriptions {...detailDescriptionsProps} bordered>
              <Descriptions.Item label={t('标题', 'Title')}>{notificationCenter.detailRecord.title}</Descriptions.Item>
              <Descriptions.Item label={t('目标范围', 'Target scope')}>{TARGET_SCOPE_LABELS[notificationCenter.detailRecord.targetScope] || notificationCenter.detailRecord.targetScope}</Descriptions.Item>
              <Descriptions.Item label={t('目标用户', 'Target user')}>
                {notificationCenter.detailRecord.targetScope === 'USER'
                  ? notificationCenter.detailRecord.targetUserName || (notificationCenter.detailRecord.targetUserId ? String(notificationCenter.detailRecord.targetUserId) : '-')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('目标角色', 'Target role')}>
                {notificationCenter.detailRecord.targetScope === 'ROLE'
                  ? notificationCenter.detailRecord.targetRoleName || (notificationCenter.detailRecord.targetRoleId ? String(notificationCenter.detailRecord.targetRoleId) : '-')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('状态', 'Status')}>{PUBLISH_STATUS_LABELS[notificationCenter.detailRecord.publishStatus]?.text || notificationCenter.detailRecord.publishStatus}</Descriptions.Item>
              <Descriptions.Item label={t('发布时间', 'Published at')}>{formatDateTime(notificationCenter.detailRecord.publishedAt || notificationCenter.detailRecord.createdAt)}</Descriptions.Item>
              <Descriptions.Item label={t('当前阅读状态', 'Read status')}>{notificationCenter.detailRecord.readFlag ? t('已读', 'Read') : t('未读', 'Unread')}</Descriptions.Item>
              <Descriptions.Item label={t('创建人 ID', 'Creator ID')}>{notificationCenter.detailRecord.createdBy ?? '-'}</Descriptions.Item>
              <Descriptions.Item label={t('更新人 ID', 'Updater ID')}>{notificationCenter.detailRecord.updatedBy ?? '-'}</Descriptions.Item>
              <Descriptions.Item label={t('已读时间', 'Read at')}>{formatDateTime(notificationCenter.detailRecord.readAt)}</Descriptions.Item>
              <Descriptions.Item label={t('更新时间', 'Updated at')}>{formatDateTime(notificationCenter.detailRecord.updatedAt)}</Descriptions.Item>
            </Descriptions>
            <div>
              <Typography.Title level={5} style={{ marginTop: 0 }}>
                {t('内容', 'Content')}
              </Typography.Title>
              <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>{notificationCenter.detailRecord.content}</Typography.Paragraph>
            </div>
          </Space>
        ) : null}
      </ManagementDrawer>

      <ManagementDrawer
        title={t('手动发布通知', 'Manually publish notification')}
        open={notificationCenter.publishOpen}
        onClose={notificationCenter.closePublishDrawer}
        footerActions={[
          { key: 'cancel', label: t('取消', 'Cancel'), onClick: notificationCenter.closePublishDrawer },
          {
            key: 'publish',
            label: t('发送', 'Send'),
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
          <Form.Item name="channels" label={t('通知渠道', 'Notification channels')} rules={[{ required: true, message: t('请选择通知渠道', 'Please select at least one channel') }]}>
            <Select
              mode="multiple"
              options={[
                { label: t('站内信', 'Inbox'), value: 'INBOX' },
                { label: t('邮箱', 'Email'), value: 'EMAIL' },
                { label: t('微信', 'WeChat'), value: 'WECHAT_OFFICIAL' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="title"
            label={t('通知标题', 'Notification title')}
            rules={[{ required: true, message: t('请输入通知标题', 'Please enter a notification title') }, { max: 128, message: t('标题长度不能超过 128 个字符', 'The title cannot exceed 128 characters') }]}
          >
            <Input placeholder={t('例如：系统维护提醒', 'e.g. System maintenance notice')} />
          </Form.Item>
          <Form.Item
            name="content"
            label={t('通知内容', 'Notification content')}
            rules={[{ required: true, message: t('请输入通知内容', 'Please enter notification content') }, { max: 2000, message: t('内容长度不能超过 2000 个字符', 'The content cannot exceed 2000 characters') }]}
          >
            <Input.TextArea rows={8} placeholder={t('请输入要发送给用户的通知内容', 'Enter the content to send to users')} />
          </Form.Item>
          <Form.Item name="targetScope" label={t('目标范围', 'Target scope')} rules={[{ required: true, message: t('请选择目标范围', 'Please select a target scope') }]}>
            <Select options={[{ label: t('全员', 'All users'), value: 'TENANT' }, { label: t('指定用户', 'Specific users'), value: 'USER' }, { label: t('角色分组', 'Role groups'), value: 'ROLE' }]} />
          </Form.Item>
          {publishTargetScope === 'USER' ? (
            <Form.Item
              name="targetUserId"
              label={t('目标用户名', 'Target username')}
              rules={[
                {
                  validator: async (_, value) => {
                    if (publishTargetScope === 'USER' && !value) {
                      throw new Error(t('请选择目标用户名', 'Please select a target username'));
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
                placeholder={t('输入用户名搜索', 'Search by username')}
                searchValue={notificationCenter.userSearch}
                onSearch={(value) => notificationCenter.setUserSearch(value.trim())}
                options={notificationCenter.userOptions.map((item) => {
                  const displayName = item.realName || item.nickname;
                  return { label: displayName ? `${item.username} · ${displayName}` : item.username, value: item.id };
                })}
                notFoundContent={notificationCenter.userLoading ? t('加载中...', 'Loading...') : t('暂无匹配用户', 'No matching users')}
              />
            </Form.Item>
          ) : null}
          {publishTargetScope === 'ROLE' ? (
            <Form.Item
              name="targetRoleId"
              label={t('角色分组', 'Role groups')}
              rules={[
                {
                  validator: async (_, value) => {
                    if (publishTargetScope === 'ROLE' && !value) {
                      throw new Error(t('请选择角色分组', 'Please select a role group'));
                    }
                  },
                },
              ]}
            >
              <Select
                allowClear
                showSearch
                loading={notificationCenter.roleLoading}
                placeholder={t('请选择角色分组', 'Select a role group')}
                filterOption={(input, option) => String(option?.label || '').toLowerCase().includes(input.toLowerCase())}
                options={notificationCenter.roleOptions.map((item) => ({ label: item.roleName, value: item.id }))}
                notFoundContent={notificationCenter.roleLoading ? t('加载中...', 'Loading...') : t('暂无角色', 'No roles')}
              />
            </Form.Item>
          ) : null}
        </Form>
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default NotificationsPage;
