import { type ActionType } from '@ant-design/pro-components';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementTable } from '@/features/management/ManagementTable';
import type { ProColumns } from '@ant-design/pro-components';
import { DatePicker, Form, Input, InputNumber, Select, Switch, Typography, theme, Button, Dropdown, Popconfirm, Descriptions, Space, Tag } from 'antd';
import { CheckOutlined, DeleteOutlined, DownOutlined, PlusOutlined } from '@ant-design/icons';
import { useMemo, useRef } from 'react';
import { useDetailDescriptionsProps } from '@/features/detail/config';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { TableActionBar } from '@/features/table/TableActionBar';
import { buildTableRequest } from '@/features/table/proTableRequest';
import { request } from '@/services/common/request';
import { useNotificationCenter } from './hooks/useNotificationCenter';
import type { MessageDeliveryLogRecord, MessageNoticeRecord } from '@/types/api';
import type { SmtpSettings, SmtpTestPayload, WechatOfficialAccountSettings } from '@/types/api';
import type { MessageChannel } from '@/types/api';
import type { FormProps } from 'antd';

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
  TENANT: '全员',
  USER: '指定用户',
  ROLE: '角色分组',
};

const CHANNEL_LABELS: Record<string, { color: string; text: string }> = {
  INBOX: { color: 'blue', text: '站内信' },
  EMAIL: { color: 'purple', text: '邮箱' },
  WECHAT_OFFICIAL: { color: 'green', text: '微信' },
};

const PUBLISH_STATUS_LABELS: Record<string, { color: string; text: string }> = {
  PUBLISHED: { color: 'green', text: '已发布' },
  RETRACTED: { color: 'default', text: '已撤回' },
};

const SEND_STATUS_LABELS: Record<string, { color: string; text: string }> = {
  SUCCESS: { color: 'green', text: '成功' },
  FAILED: { color: 'red', text: '失败' },
  SKIPPED: { color: 'orange', text: '跳过' },
};

const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('zh-CN', { hour12: false });
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
  request<{ records: MessageNoticeRecord[]; total: number }>('/v1/message/archive', {
    method: 'GET',
    params,
    autoRedirectOnUnauthorized: false,
    silent: true,
  });

const deliveryLogs = (params: MessageArchiveQuery = {}) =>
  request<{ records: MessageDeliveryLogRecord[]; total: number }>('/v1/message/delivery-logs', {
    method: 'GET',
    params,
    autoRedirectOnUnauthorized: false,
    silent: true,
  });

const NotificationInboxInfo = ({ descriptionsProps }: { descriptionsProps: Parameters<typeof Descriptions>[0] }) => (
  <Descriptions {...descriptionsProps} bordered>
    <Descriptions.Item label="通知标识">inbox_notice</Descriptions.Item>
    <Descriptions.Item label="状态">已启用</Descriptions.Item>
    <Descriptions.Item label="能力">消息中心、实时推送、已读状态、撤回</Descriptions.Item>
    <Descriptions.Item label="说明">站内信是系统默认通知渠道，无需额外配置。</Descriptions.Item>
  </Descriptions>
);

const NotificationEmailChannelSettingsForm = ({
  canManageSmtp,
  loadingSmtpSettings,
  testingSmtpSettings,
  smtpFormProps,
  smtpTestFormProps,
  onTestSmtpSettings,
}: {
  canManageSmtp: boolean;
  loadingSmtpSettings: boolean;
  testingSmtpSettings: boolean;
  smtpFormProps: FormProps<SmtpSettings>;
  smtpTestFormProps: FormProps<SmtpTestPayload>;
  onTestSmtpSettings: () => void;
}) => (
  <Space direction="vertical" size={16} style={{ width: '100%' }}>
    <Form {...smtpFormProps} disabled={loadingSmtpSettings || !canManageSmtp}>
      <Form.Item name="host" label="SMTP 主机" rules={[{ required: true, message: '请输入 SMTP 主机' }]}>
        <Input placeholder="例如：smtp.example.com" />
      </Form.Item>
      <Form.Item name="port" label="SMTP 端口" rules={[{ required: true, message: '请输入 SMTP 端口' }]}>
        <InputNumber min={1} max={65535} style={{ width: '100%' }} />
      </Form.Item>
      <Form.Item name="username" label="SMTP 用户名">
        <Input placeholder="SMTP 登录用户名" autoComplete="off" />
      </Form.Item>
      <Form.Item name="password" label="SMTP 密码">
        <Input.Password placeholder="留空则保持现有密码" autoComplete="new-password" />
      </Form.Item>
      <Form.Item name="from" label="发件人地址" rules={[{ required: true, type: 'email', message: '请输入有效发件人邮箱' }]}>
        <Input placeholder="例如：notice@example.com" />
      </Form.Item>
      <Form.Item name="authEnabled" label="SMTP 认证" valuePropName="checked">
        <Switch />
      </Form.Item>
      <Form.Item name="startTlsEnabled" label="STARTTLS" valuePropName="checked">
        <Switch />
      </Form.Item>
      <Form.Item name="sslEnabled" label="SSL" valuePropName="checked">
        <Switch />
      </Form.Item>
    </Form>
    <Form {...smtpTestFormProps}>
      <Typography.Title level={5} style={{ marginTop: 0 }}>
        测试发送
      </Typography.Title>
      <Form.Item name="toEmail" label="测试收件邮箱" rules={[{ required: true, type: 'email', message: '请输入有效邮箱地址' }]}>
        <Input placeholder="请输入测试收件邮箱" />
      </Form.Item>
      <Form.Item name="subject" label="测试主题">
        <Input />
      </Form.Item>
      <Form.Item name="content" label="测试内容">
        <Input.TextArea rows={4} />
      </Form.Item>
      <Button loading={testingSmtpSettings} disabled={!canManageSmtp} onClick={onTestSmtpSettings}>
        发送测试邮件
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
    <Form.Item name="appId" label="AppID" rules={[{ required: true, message: '请输入微信公众号 AppID' }]}>
      <Input placeholder="微信公众号或服务号 AppID" />
    </Form.Item>
    <Form.Item
      name="appSecret"
      label="AppSecret"
      rules={!wechatOfficialAppSecretConfigured ? [{ required: true, message: '请输入微信公众号 AppSecret' }] : undefined}
      extra={wechatOfficialAppSecretConfigured ? '当前密钥已脱敏显示，留空则保持现有密钥' : '用于获取公众号 access_token'}
    >
      <Input.Password placeholder="留空则保持现有密钥" autoComplete="new-password" />
    </Form.Item>
    <Form.Item name="templateId" label="模板消息 ID" rules={[{ required: true, message: '请输入模板消息 ID' }]}>
      <Input placeholder="公众号后台配置的模板消息 ID" />
    </Form.Item>
    <Form.Item name="detailUrl" label="通知详情链接" extra="模板消息点击后打开的页面，可留空">
      <Input placeholder="例如：https://test.elexvx.com/messages" />
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
      title: '关键字',
      dataIndex: 'keyword',
      hideInTable: true,
      renderFormItem: () => <DatePicker.RangePicker showTime style={{ width: '100%' }} />,
    },
    {
      title: '标题',
      dataIndex: 'title',
      ellipsis: true,
      copyable: true,
      search: false,
      render: (_: unknown, record: MessageNoticeRecord) => <Typography.Text strong>{record.title}</Typography.Text>,
    },
    {
      title: '目标范围',
      dataIndex: 'targetScope',
      width: 120,
      valueEnum: { TENANT: { text: '全员' }, USER: { text: '指定用户' }, ROLE: { text: '角色分组' } },
      renderFormItem: () => <Select allowClear options={[{ label: '全员', value: 'TENANT' }, { label: '指定用户', value: 'USER' }, { label: '角色分组', value: 'ROLE' }]} placeholder="全部" />,
      render: (_: unknown, record: MessageNoticeRecord) => renderTag(TARGET_SCOPE_LABELS[record.targetScope] || record.targetScope, 'geekblue'),
    },
    {
      title: '目标用户',
      dataIndex: 'targetUserName',
      width: 160,
      search: false,
      responsive: ['lg', 'xl', 'xxl'],
      render: (_: unknown, record: MessageNoticeRecord) => (record.targetScope === 'USER' ? record.targetUserName || (record.targetUserId ? String(record.targetUserId) : '-') : '-'),
    },
    {
      title: '目标角色',
      dataIndex: 'targetRoleName',
      width: 160,
      search: false,
      responsive: ['lg', 'xl', 'xxl'],
      render: (_: unknown, record: MessageNoticeRecord) => (record.targetScope === 'ROLE' ? record.targetRoleName || (record.targetRoleId ? String(record.targetRoleId) : '-') : '-'),
    },
    {
      title: '状态',
      dataIndex: 'publishStatus',
      width: 110,
      valueEnum: { PUBLISHED: { text: '已发布' }, RETRACTED: { text: '已撤回' } },
      renderFormItem: () => <Select allowClear options={[{ label: '已发布', value: 'PUBLISHED' }, { label: '已撤回', value: 'RETRACTED' }]} placeholder="全部" />,
      render: (_: unknown, record: MessageNoticeRecord) => renderEnumTag(record.publishStatus, PUBLISH_STATUS_LABELS),
    },
    {
      title: '发布时间',
      dataIndex: 'publishedAt',
      width: 180,
      search: false,
      sorter: true,
      render: (_: unknown, record: MessageNoticeRecord) => formatDateTime(record.publishedAt || record.createdAt),
    },
    {
      title: '发布时间范围',
      dataIndex: 'publishedAtRange',
      hideInTable: true,
      renderFormItem: () => <DatePicker.RangePicker showTime style={{ width: '100%' }} />,
      search: buildRangeSearch(),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 160,
      render: (_: unknown, record: MessageNoticeRecord) => (
        <TableActionBar
          isMobile={false}
          items={[
            { key: 'detail', label: '详情', onClick: () => handleOpenDetail(record) },
            { key: 'retract', label: '撤回', danger: true, hidden: record.publishStatus === 'RETRACTED' || !canRetractMessage, onClick: () => void handleRetract(record) },
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
      title: '关键字',
      dataIndex: 'keyword',
      hideInTable: true,
      renderFormItem: () => <DatePicker.RangePicker showTime style={{ width: '100%' }} />,
    },
    {
      title: '标题',
      dataIndex: 'title',
      ellipsis: true,
      copyable: true,
      search: false,
      render: (_: unknown, record: MessageDeliveryLogRecord) => <Typography.Text strong>{record.title}</Typography.Text>,
    },
    {
      title: '渠道',
      dataIndex: 'channel',
      width: 110,
      valueEnum: { INBOX: { text: '站内信' }, EMAIL: { text: '邮箱' }, WECHAT_OFFICIAL: { text: '微信' } },
      renderFormItem: () => <Select allowClear options={[{ label: '站内信', value: 'INBOX' }, { label: '邮箱', value: 'EMAIL' }, { label: '微信', value: 'WECHAT_OFFICIAL' }]} placeholder="全部" />,
      render: (_: unknown, record: MessageDeliveryLogRecord) => renderEnumTag(record.channel, CHANNEL_LABELS),
    },
    {
      title: '状态',
      dataIndex: 'sendStatus',
      width: 110,
      valueEnum: { SUCCESS: { text: '成功' }, FAILED: { text: '失败' }, SKIPPED: { text: '跳过' } },
      renderFormItem: () => <Select allowClear options={[{ label: '成功', value: 'SUCCESS' }, { label: '失败', value: 'FAILED' }, { label: '跳过', value: 'SKIPPED' }]} placeholder="全部" />,
      render: (_: unknown, record: MessageDeliveryLogRecord) => renderEnumTag(record.sendStatus, SEND_STATUS_LABELS),
    },
    {
      title: '目标范围',
      dataIndex: 'targetScope',
      width: 120,
      renderFormItem: () => <Select allowClear options={[{ label: '全员', value: 'TENANT' }, { label: '指定用户', value: 'USER' }, { label: '角色分组', value: 'ROLE' }]} placeholder="全部" />,
      render: (_: unknown, record: MessageDeliveryLogRecord) => renderTag(TARGET_SCOPE_LABELS[record.targetScope] || record.targetScope, 'geekblue'),
    },
    {
      title: '收件人',
      dataIndex: 'targetUserName',
      width: 160,
      search: false,
      responsive: ['lg', 'xl', 'xxl'],
      render: (_: unknown, record: MessageDeliveryLogRecord) => record.targetUserName || (record.targetUserId ? String(record.targetUserId) : '-'),
    },
    {
      title: '接收标识',
      dataIndex: 'targetEmail',
      width: 220,
      ellipsis: true,
      search: false,
      responsive: ['lg', 'xl', 'xxl'],
      render: (_: unknown, record: MessageDeliveryLogRecord) => record.targetEmail || '-',
    },
    {
      title: '错误信息',
      dataIndex: 'errorMessage',
      ellipsis: true,
      search: false,
      responsive: ['xl', 'xxl'],
      render: (_: unknown, record: MessageDeliveryLogRecord) => record.errorMessage || '-',
    },
    {
      title: '发送时间',
      dataIndex: 'createdAt',
      width: 180,
      search: false,
      sorter: true,
      render: (_: unknown, record: MessageDeliveryLogRecord) => formatDateTime(record.sentAt || record.createdAt),
    },
    {
      title: '发送时间范围',
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
  { title: '序号', dataIndex: 'order', width: 80, search: false },
  { title: '通知标识', dataIndex: 'identifier', width: 180, copyable: true, search: false },
  {
    title: '通知类型',
    dataIndex: 'type',
    width: 140,
    search: false,
    render: (_: unknown, record: NotificationChannelRecord) => renderTag(record.type, CHANNEL_LABELS[record.key]?.color || 'blue'),
  },
  { title: '标题', dataIndex: 'title', width: 160, search: false, render: (_: unknown, record: NotificationChannelRecord) => <Typography.Text strong>{record.title}</Typography.Text> },
  { title: '描述', dataIndex: 'description', width: 220, search: false, ellipsis: true },
  {
    title: '启用',
    dataIndex: 'enabled',
    width: 120,
    align: 'center',
    search: false,
    render: (_: unknown, record: NotificationChannelRecord) => (record.enabled ? <CheckOutlined style={{ color: tokenColorSuccess }} /> : null),
  },
  {
    title: '操作',
    valueType: 'option',
    width: 240,
    fixed: isDesktop ? 'right' : undefined,
    render: (_: unknown, record: NotificationChannelRecord) => (
      <TableActionBar
        isMobile={isMobile}
        inlineCount={isMobile ? 0 : 3}
        items={[
          {
            key: 'toggle',
            label: '禁用',
            danger: true,
            disabled: !canManageSmtp || record.key === 'INBOX',
            loading: togglingChannelKey === record.key,
            onClick: () => void handleDisableChannel(record),
          },
          {
            key: 'config',
            label: '配置',
            onClick: () => handleOpenChannelDrawer(record),
          },
          {
            key: 'logs',
            label: '日志',
            onClick: () => setLogOpen(true),
          },
          {
            key: 'archive',
            label: '归档',
            hidden: record.key !== 'INBOX',
            onClick: () => setArchiveOpen(true),
          },
          {
            key: 'delete',
            label: '删除',
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
    <ManagementPage title="通知中心">
      <ManagementTable
        rowKey="key"
        columns={channelColumns}
        dataSource={notificationCenter.channelRecords}
        isMobile={responsive.isMobile}
        pagination={{ pageSize: 50, showSizeChanger: true }}
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
            title="删除通知渠道"
            description="选中的通知渠道会被停用，站内信会保留。"
            okText="确认"
            cancelText="取消"
            onConfirm={() => void notificationCenter.handleDeleteSelectedChannels()}
          >
            <Button disabled={!canManageSmtp} icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>,
          <Dropdown key="add" trigger={['click']} menu={{ items: notificationCenter.addChannelItems }} placement="bottomRight">
            <Button type="primary" disabled={!notificationCenter.addChannelItems?.length} icon={<PlusOutlined />}>
              添加 <DownOutlined />
            </Button>
          </Dropdown>,
        ]}
      />

      <ManagementDrawer
        title={notificationCenter.channelRecord ? `配置${notificationCenter.channelRecord.title}` : '配置通知渠道'}
        open={notificationCenter.channelDrawerOpen}
        onClose={notificationCenter.closeChannelDrawer}
        footerActions={
          notificationCenter.channelRecord?.key === 'EMAIL'
            ? [
                { key: 'cancel', label: '取消', onClick: notificationCenter.closeChannelDrawer },
                { key: 'save', label: '保存', type: 'primary' as const, loading: notificationCenter.savingSmtpSettings, disabled: !canManageSmtp, onClick: notificationCenter.handleSaveSmtpSettings },
              ]
            : notificationCenter.channelRecord?.key === 'WECHAT_OFFICIAL'
              ? [
                  { key: 'cancel', label: '取消', onClick: notificationCenter.closeChannelDrawer },
                  { key: 'save', label: '保存', type: 'primary' as const, loading: notificationCenter.savingWechatOfficialSettings, disabled: !canManageSmtp, onClick: notificationCenter.handleSaveWechatOfficialSettings },
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

      <ManagementDrawer title="通知发送日志" open={notificationCenter.logOpen} onClose={() => notificationCenter.setLogOpen(false)} destroyOnHidden>
        <ManagementTable<MessageDeliveryLogRecord>
          actionRef={logActionRef}
          rowKey="id"
          columns={logColumns}
          isMobile={responsive.isMobile}
          pagination={{ showSizeChanger: true, pageSize: 10 }}
          search={searchConfig}
          request={buildTableRequest((params) => deliveryLogs(params))}
          toolBarRender={false}
        />
      </ManagementDrawer>

      <ManagementDrawer title="通知归档" open={notificationCenter.archiveOpen} onClose={() => notificationCenter.setArchiveOpen(false)} destroyOnHidden>
        <ManagementTable<MessageNoticeRecord>
          actionRef={archiveActionRef}
          rowKey="id"
          columns={archiveColumns}
          isMobile={responsive.isMobile}
          pagination={{ showSizeChanger: true, pageSize: 10 }}
          search={searchConfig}
          request={buildTableRequest((params, sorter) =>
            archiveMessages({ ...params, ...resolveSortParams(sorter) }),
          )}
          toolBarRender={false}
        />
      </ManagementDrawer>

      <ManagementDrawer
        title={notificationCenter.detailRecord ? `通知详情 · ${notificationCenter.detailRecord.title}` : '通知详情'}
        open={notificationCenter.detailOpen}
        onClose={notificationCenter.handleCloseDetail}
        extra={
          notificationCenter.detailRecord && notificationCenter.detailRecord.publishStatus === 'PUBLISHED' ? (
            <Button danger disabled={!canRetractMessage} onClick={() => void notificationCenter.handleRetract(notificationCenter.detailRecord!)}>
              撤回
            </Button>
          ) : null
        }
      >
        {notificationCenter.detailRecord ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions {...detailDescriptionsProps} bordered>
              <Descriptions.Item label="标题">{notificationCenter.detailRecord.title}</Descriptions.Item>
              <Descriptions.Item label="目标范围">{TARGET_SCOPE_LABELS[notificationCenter.detailRecord.targetScope] || notificationCenter.detailRecord.targetScope}</Descriptions.Item>
              <Descriptions.Item label="目标用户">
                {notificationCenter.detailRecord.targetScope === 'USER'
                  ? notificationCenter.detailRecord.targetUserName || (notificationCenter.detailRecord.targetUserId ? String(notificationCenter.detailRecord.targetUserId) : '-')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="目标角色">
                {notificationCenter.detailRecord.targetScope === 'ROLE'
                  ? notificationCenter.detailRecord.targetRoleName || (notificationCenter.detailRecord.targetRoleId ? String(notificationCenter.detailRecord.targetRoleId) : '-')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="状态">{PUBLISH_STATUS_LABELS[notificationCenter.detailRecord.publishStatus]?.text || notificationCenter.detailRecord.publishStatus}</Descriptions.Item>
              <Descriptions.Item label="发布时间">{formatDateTime(notificationCenter.detailRecord.publishedAt || notificationCenter.detailRecord.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="当前阅读状态">{notificationCenter.detailRecord.readFlag ? '已读' : '未读'}</Descriptions.Item>
              <Descriptions.Item label="创建人 ID">{notificationCenter.detailRecord.createdBy ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="更新人 ID">{notificationCenter.detailRecord.updatedBy ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="已读时间">{formatDateTime(notificationCenter.detailRecord.readAt)}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{formatDateTime(notificationCenter.detailRecord.updatedAt)}</Descriptions.Item>
            </Descriptions>
            <div>
              <Typography.Title level={5} style={{ marginTop: 0 }}>
                内容
              </Typography.Title>
              <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>{notificationCenter.detailRecord.content}</Typography.Paragraph>
            </div>
          </Space>
        ) : null}
      </ManagementDrawer>

      <ManagementDrawer
        title="手动发布通知"
        open={notificationCenter.publishOpen}
        onClose={notificationCenter.closePublishDrawer}
        footerActions={[
          { key: 'cancel', label: '取消', onClick: notificationCenter.closePublishDrawer },
          {
            key: 'publish',
            label: '发送',
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
          <Form.Item name="channels" label="通知渠道" rules={[{ required: true, message: '请选择通知渠道' }]}>
            <Select
              mode="multiple"
              options={[
                { label: '站内信', value: 'INBOX' },
                { label: '邮箱', value: 'EMAIL' },
                { label: '微信', value: 'WECHAT_OFFICIAL' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="title"
            label="通知标题"
            rules={[{ required: true, message: '请输入通知标题' }, { max: 128, message: '标题长度不能超过 128 个字符' }]}
          >
            <Input placeholder="例如：系统维护提醒" />
          </Form.Item>
          <Form.Item
            name="content"
            label="通知内容"
            rules={[{ required: true, message: '请输入通知内容' }, { max: 2000, message: '内容长度不能超过 2000 个字符' }]}
          >
            <Input.TextArea rows={8} placeholder="请输入要发送给用户的通知内容" />
          </Form.Item>
          <Form.Item name="targetScope" label="目标范围" rules={[{ required: true, message: '请选择目标范围' }]}>
            <Select options={[{ label: '全员', value: 'TENANT' }, { label: '指定用户', value: 'USER' }, { label: '角色分组', value: 'ROLE' }]} />
          </Form.Item>
          {publishTargetScope === 'USER' ? (
            <Form.Item
              name="targetUserId"
              label="目标用户名"
              rules={[
                {
                  validator: async (_, value) => {
                    if (publishTargetScope === 'USER' && !value) {
                      throw new Error('请选择目标用户名');
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
                placeholder="输入用户名搜索"
                searchValue={notificationCenter.userSearch}
                onSearch={(value) => notificationCenter.setUserSearch(value.trim())}
                options={notificationCenter.userOptions.map((item) => {
                  const displayName = item.realName || item.nickname;
                  return { label: displayName ? `${item.username} · ${displayName}` : item.username, value: item.id };
                })}
                notFoundContent={notificationCenter.userLoading ? '加载中...' : '暂无匹配用户'}
              />
            </Form.Item>
          ) : null}
          {publishTargetScope === 'ROLE' ? (
            <Form.Item
              name="targetRoleId"
              label="角色分组"
              rules={[
                {
                  validator: async (_, value) => {
                    if (publishTargetScope === 'ROLE' && !value) {
                      throw new Error('请选择角色分组');
                    }
                  },
                },
              ]}
            >
              <Select
                allowClear
                showSearch
                loading={notificationCenter.roleLoading}
                placeholder="请选择角色分组"
                filterOption={(input, option) => String(option?.label || '').toLowerCase().includes(input.toLowerCase())}
                options={notificationCenter.roleOptions.map((item) => ({ label: item.roleName, value: item.id }))}
                notFoundContent={notificationCenter.roleLoading ? '加载中...' : '暂无角色'}
              />
            </Form.Item>
          ) : null}
        </Form>
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default NotificationsPage;
