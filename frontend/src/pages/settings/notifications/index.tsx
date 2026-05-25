import { Button, Checkbox, DatePicker, Descriptions, Form, Input, InputNumber, Select, Space, Switch, Tag, Typography, message } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type ActionType, type ProColumns } from '@ant-design/pro-components';
import { ManagementDrawer, ManagementPage, ManagementTable } from '@/features/management';
import { buildTableRequest } from '@/features/table/proTable';
import { useDetailDescriptionsProps } from '@/features/detail/config';
import { useStandardFormProps } from '@/features/form/config';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { iamService } from '@/services/iam';
import { messageService } from '@/services/message';
import { systemService } from '@/services/system';
import { userService } from '@/services/user';
import type { MessageChannel, MessageDeliveryLogRecord, MessageNoticeRecord, RoleRecord, SmtpSettings, SmtpTestPayload, UserRecord } from '@/types/api';
import { TableActionBar } from '@/features/table/TableActionBar';
import { confirmAction } from '@/utils/confirm';
import { notifyMessageCenterRefresh } from '@/components/message-center/messageCenterEvents';

const TARGET_SCOPE_LABELS: Record<string, string> = {
  TENANT: '全员',
  USER: '指定用户',
  ROLE: '角色分组',
};

const CHANNEL_LABELS: Record<string, { color: string; text: string }> = {
  INBOX: { color: 'blue', text: '站内信' },
  EMAIL: { color: 'purple', text: '邮箱' },
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

interface ChannelRecord {
  key: MessageChannel;
  order: number;
  identifier: string;
  type: string;
  title: string;
  description: string;
  enabled: boolean;
  configured: boolean;
}

const smtpFormInitialValues: SmtpSettings = {
  host: '',
  port: 25,
  username: '',
  password: '',
  from: '',
  authEnabled: true,
  startTlsEnabled: true,
  sslEnabled: false,
};

const smtpTestInitialValues: SmtpTestPayload = {
  subject: '系统通知测试邮件',
  content: '这是一封来自系统通知能力的测试邮件。',
  toEmail: '',
};

const SMTP_PASSWORD_MASK = '********';

const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : value;
};

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

const NotificationsPage = () => {
  const archiveActionRef = useRef<ActionType | undefined>(undefined);
  const logActionRef = useRef<ActionType | undefined>(undefined);
  const { actionPermission, responsive, searchConfig, buildToolbarButtons } = usePagePermissionActions();
  const [detailRecord, setDetailRecord] = useState<MessageNoticeRecord | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [channelDrawerOpen, setChannelDrawerOpen] = useState(false);
  const [channelRecord, setChannelRecord] = useState<ChannelRecord | null>(null);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [logOpen, setLogOpen] = useState(false);
  const [smtpSettings, setSmtpSettings] = useState<SmtpSettings | null>(null);
  const [loadingSmtpSettings, setLoadingSmtpSettings] = useState(false);
  const [savingSmtpSettings, setSavingSmtpSettings] = useState(false);
  const [testingSmtpSettings, setTestingSmtpSettings] = useState(false);
  const [publishOpen, setPublishOpen] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [userSearch, setUserSearch] = useState('');
  const [userOptions, setUserOptions] = useState<UserRecord[]>([]);
  const [userLoading, setUserLoading] = useState(false);
  const [roleOptions, setRoleOptions] = useState<RoleRecord[]>([]);
  const [roleLoading, setRoleLoading] = useState(false);
  const [form] = Form.useForm<{
    title: string;
    content: string;
    channels: MessageChannel[];
    targetScope: 'TENANT' | 'USER' | 'ROLE';
    targetUserId?: number;
    targetRoleId?: number;
  }>();
  const [smtpSettingsForm] = Form.useForm<SmtpSettings>();
  const [smtpTestForm] = Form.useForm<SmtpTestPayload>();
  const targetScope = Form.useWatch('targetScope', form);
  const detailDescriptionsProps = useDetailDescriptionsProps({
    column: responsive.isMobile ? 1 : 2,
  });
  const publishFormProps = useStandardFormProps({
    form,
    initialValues: {
      title: '',
      content: '',
      channels: ['INBOX'],
      targetScope: 'TENANT',
      targetUserId: undefined,
      targetRoleId: undefined,
    },
  });
  const smtpFormProps = useStandardFormProps({
    form: smtpSettingsForm,
    initialValues: smtpFormInitialValues,
  });
  const smtpTestFormProps = useStandardFormProps({
    form: smtpTestForm,
    initialValues: smtpTestInitialValues,
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

  const loadSmtpSettings = async () => {
    setLoadingSmtpSettings(true);
    try {
      const nextSettings = await systemService.smtpSettings(requestOptions);
      setSmtpSettings(nextSettings);
      smtpSettingsForm.setFieldsValue({
        ...smtpFormInitialValues,
        ...nextSettings,
        password: nextSettings.passwordConfigured ? SMTP_PASSWORD_MASK : '',
      });
    } catch {
      setSmtpSettings(null);
    } finally {
      setLoadingSmtpSettings(false);
    }
  };

  useEffect(() => {
    void loadSmtpSettings();
  }, []);

  useEffect(() => {
    if (!publishOpen || !canManualPublish || roleOptions.length > 0) {
      return;
    }
    let active = true;
    setRoleLoading(true);
    void iamService
      .roles({ pageNo: 1, pageSize: 200 }, requestOptions)
      .then((result) => {
        if (active) {
          setRoleOptions(result.records || []);
        }
      })
      .catch(() => {
        if (active) {
          message.error('角色列表加载失败，请稍后重试');
        }
      })
      .finally(() => {
        if (active) {
          setRoleLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [canManualPublish, publishOpen, requestOptions, roleOptions.length]);

  useEffect(() => {
    if (!publishOpen || targetScope !== 'USER') {
      return;
    }
    let active = true;
    const timer = window.setTimeout(() => {
      setUserLoading(true);
      void userService
        .list({ username: userSearch || undefined, pageNo: 1, pageSize: 20 }, requestOptions)
        .then((result) => {
          if (active) {
            setUserOptions(result.records || []);
          }
        })
        .catch(() => {
          if (active) {
            message.error('用户名列表加载失败，请稍后重试');
          }
        })
        .finally(() => {
          if (active) {
            setUserLoading(false);
          }
        });
    }, 250);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [publishOpen, requestOptions, targetScope, userSearch]);

  const channelRecords = useMemo<ChannelRecord[]>(
    () => [
      {
        key: 'INBOX',
        order: 1,
        identifier: 'inbox_notice',
        type: '站内信',
        title: '站内信通知',
        description: '发送到系统右上角消息中心，支持实时推送、已读和撤回。',
        enabled: true,
        configured: true,
      },
      {
        key: 'EMAIL',
        order: 2,
        identifier: 'email_notice',
        type: '邮箱',
        title: '邮箱通知',
        description: '通过 SMTP 向已绑定邮箱的用户发送系统通知。',
        enabled: Boolean(smtpSettings?.configured),
        configured: Boolean(smtpSettings?.configured),
      },
    ],
    [smtpSettings?.configured],
  );

  const closePublishDrawer = () => {
    setPublishOpen(false);
    setUserSearch('');
    setUserOptions([]);
    setUserLoading(false);
    setRoleLoading(false);
    form.resetFields();
  };

  const openPublishDrawer = () => {
    form.resetFields();
    setUserSearch('');
    setUserOptions([]);
    setUserLoading(false);
    setRoleLoading(false);
    form.setFieldsValue({ title: '', content: '', channels: ['INBOX'], targetScope: 'TENANT', targetUserId: undefined, targetRoleId: undefined });
    setPublishOpen(true);
  };

  const handlePublish = async () => {
    if (!canManualPublish) {
      return;
    }
    setPublishing(true);
    try {
      const values = await form.validateFields();
      await messageService.createMessage(
        {
          title: values.title,
          content: values.content,
          channels: values.channels,
          targetScope: values.targetScope,
          targetUserId: values.targetScope === 'USER' ? values.targetUserId : undefined,
          targetRoleId: values.targetScope === 'ROLE' ? values.targetRoleId : undefined,
        },
        requestOptions,
      );
      message.success('通知已提交发送');
      closePublishDrawer();
      notifyMessageCenterRefresh();
      archiveActionRef.current?.reload();
      logActionRef.current?.reload();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return;
      }
      message.error(error instanceof Error ? error.message : '通知发送失败，请稍后重试');
    } finally {
      setPublishing(false);
    }
  };

  const handleOpenChannelDrawer = (record: ChannelRecord) => {
    setChannelRecord(record);
    setChannelDrawerOpen(true);
    if (record.key === 'EMAIL') {
      void loadSmtpSettings();
    }
  };

  const handleSaveSmtpSettings = async () => {
    if (!canManageSmtp) {
      return;
    }
    setSavingSmtpSettings(true);
    try {
      const values = await smtpSettingsForm.validateFields();
      const payload = {
        ...values,
        password: values.password === SMTP_PASSWORD_MASK ? undefined : values.password,
      };
      const nextSettings = await systemService.updateSmtpSettings(payload, requestOptions);
      setSmtpSettings(nextSettings);
      smtpSettingsForm.setFieldsValue({
        ...nextSettings,
        password: nextSettings.passwordConfigured ? SMTP_PASSWORD_MASK : '',
      });
      message.success('邮箱通知配置已保存');
      setChannelDrawerOpen(false);
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return;
      }
      message.error(error instanceof Error ? error.message : '邮箱通知配置保存失败，请稍后重试');
    } finally {
      setSavingSmtpSettings(false);
    }
  };

  const handleTestSmtpSettings = async () => {
    setTestingSmtpSettings(true);
    try {
      const values = await smtpTestForm.validateFields();
      await systemService.testSmtpSettings(values, requestOptions);
      message.success('测试邮件已发送');
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return;
      }
      message.error(error instanceof Error ? error.message : '测试邮件发送失败，请检查配置');
    } finally {
      setTestingSmtpSettings(false);
    }
  };

  const handleOpenDetail = (record: MessageNoticeRecord) => {
    setDetailRecord(record);
    setDetailOpen(true);
  };

  const handleRetract = (record: MessageNoticeRecord) => {
    confirmAction({
      title: '撤回站内信',
      content: `确认撤回「${record.title}」吗？撤回后该记录将保留在归档中，但不再继续投递。`,
      okText: '确认撤回',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await messageService.retractMessage(record.id, requestOptions);
          message.success('站内信已撤回');
          setDetailRecord((current) => (current && current.id === record.id ? { ...current, publishStatus: 'RETRACTED' } : current));
          notifyMessageCenterRefresh();
          archiveActionRef.current?.reload();
          logActionRef.current?.reload();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '站内信撤回失败，请稍后重试');
        }
      },
    });
  };

  const channelColumns = useMemo<ProColumns<ChannelRecord>[]>(
    () => [
      { title: '序号', dataIndex: 'order', width: 80, search: false },
      { title: '通知标识', dataIndex: 'identifier', copyable: true, search: false },
      { title: '通知类型', dataIndex: 'type', width: 140, search: false, render: (_, record) => renderTag(record.type, record.key === 'EMAIL' ? 'purple' : 'blue') },
      { title: '标题', dataIndex: 'title', search: false, render: (_, record) => <Typography.Text strong>{record.title}</Typography.Text> },
      { title: '描述', dataIndex: 'description', search: false, ellipsis: true },
      { title: '启用', dataIndex: 'enabled', width: 110, search: false, render: (_, record) => (record.enabled ? <Tag color="green">启用</Tag> : <Tag>未配置</Tag>) },
      {
        title: '操作',
        valueType: 'option',
        width: 140,
        fixed: 'right',
        render: (_, record) => (
          <TableActionBar
            isMobile={responsive.isMobile}
            items={[
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
            ]}
          />
        ),
      },
    ],
    [responsive.isMobile],
  );

  const archiveColumns = useMemo<ProColumns<MessageNoticeRecord>[]>(
    () => [
      { title: '关键字', dataIndex: 'keyword', hideInTable: true, renderFormItem: () => <Input allowClear placeholder="按标题或内容搜索" /> },
      { title: '标题', dataIndex: 'title', ellipsis: true, copyable: true, search: false, render: (_, record) => <Typography.Text strong>{record.title}</Typography.Text> },
      {
        title: '目标范围',
        dataIndex: 'targetScope',
        width: 120,
        valueEnum: { TENANT: { text: '全员' }, USER: { text: '指定用户' }, ROLE: { text: '角色分组' } },
        renderFormItem: () => <Select allowClear options={[{ label: '全员', value: 'TENANT' }, { label: '指定用户', value: 'USER' }, { label: '角色分组', value: 'ROLE' }]} placeholder="全部" />,
        render: (_, record) => renderTag(TARGET_SCOPE_LABELS[record.targetScope] || record.targetScope, 'geekblue'),
      },
      { title: '目标用户', dataIndex: 'targetUserName', width: 160, search: false, responsive: ['lg', 'xl', 'xxl'], render: (_, record) => (record.targetScope === 'USER' ? record.targetUserName || (record.targetUserId ? String(record.targetUserId) : '-') : '-') },
      { title: '目标角色', dataIndex: 'targetRoleName', width: 160, search: false, responsive: ['lg', 'xl', 'xxl'], render: (_, record) => (record.targetScope === 'ROLE' ? record.targetRoleName || (record.targetRoleId ? String(record.targetRoleId) : '-') : '-') },
      {
        title: '状态',
        dataIndex: 'publishStatus',
        width: 110,
        valueEnum: { PUBLISHED: { text: '已发布' }, RETRACTED: { text: '已撤回' } },
        renderFormItem: () => <Select allowClear options={[{ label: '已发布', value: 'PUBLISHED' }, { label: '已撤回', value: 'RETRACTED' }]} placeholder="全部" />,
        render: (_, record) => renderEnumTag(record.publishStatus, PUBLISH_STATUS_LABELS),
      },
      { title: '发布时间', dataIndex: 'publishedAt', width: 180, search: false, sorter: true, render: (_, record) => formatDateTime(record.publishedAt || record.createdAt) },
      { title: '发布时间范围', dataIndex: 'publishedAtRange', hideInTable: true, renderFormItem: () => <DatePicker.RangePicker showTime style={{ width: '100%' }} />, search: buildRangeSearch() },
      {
        title: '操作',
        valueType: 'option',
        width: 160,
        render: (_, record) => (
          <TableActionBar
            isMobile={responsive.isMobile}
            items={[
              { key: 'detail', label: '详情', onClick: () => handleOpenDetail(record) },
              { key: 'retract', label: '撤回', danger: true, hidden: record.publishStatus === 'RETRACTED' || !canRetractMessage, onClick: () => handleRetract(record) },
            ]}
          />
        ),
      },
    ],
    [canRetractMessage, responsive.isMobile],
  );

  const logColumns = useMemo<ProColumns<MessageDeliveryLogRecord>[]>(
    () => [
      { title: '关键字', dataIndex: 'keyword', hideInTable: true, renderFormItem: () => <Input allowClear placeholder="按标题、收件人或错误搜索" /> },
      { title: '标题', dataIndex: 'title', ellipsis: true, copyable: true, search: false, render: (_, record) => <Typography.Text strong>{record.title}</Typography.Text> },
      {
        title: '渠道',
        dataIndex: 'channel',
        width: 110,
        valueEnum: { INBOX: { text: '站内信' }, EMAIL: { text: '邮箱' } },
        renderFormItem: () => <Select allowClear options={[{ label: '站内信', value: 'INBOX' }, { label: '邮箱', value: 'EMAIL' }]} placeholder="全部" />,
        render: (_, record) => renderEnumTag(record.channel, CHANNEL_LABELS),
      },
      {
        title: '状态',
        dataIndex: 'sendStatus',
        width: 110,
        valueEnum: { SUCCESS: { text: '成功' }, FAILED: { text: '失败' }, SKIPPED: { text: '跳过' } },
        renderFormItem: () => <Select allowClear options={[{ label: '成功', value: 'SUCCESS' }, { label: '失败', value: 'FAILED' }, { label: '跳过', value: 'SKIPPED' }]} placeholder="全部" />,
        render: (_, record) => renderEnumTag(record.sendStatus, SEND_STATUS_LABELS),
      },
      {
        title: '目标范围',
        dataIndex: 'targetScope',
        width: 120,
        renderFormItem: () => <Select allowClear options={[{ label: '全员', value: 'TENANT' }, { label: '指定用户', value: 'USER' }, { label: '角色分组', value: 'ROLE' }]} placeholder="全部" />,
        render: (_, record) => renderTag(TARGET_SCOPE_LABELS[record.targetScope] || record.targetScope, 'geekblue'),
      },
      { title: '收件人', dataIndex: 'targetUserName', width: 160, search: false, responsive: ['lg', 'xl', 'xxl'], render: (_, record) => record.targetUserName || (record.targetUserId ? String(record.targetUserId) : '-') },
      { title: '邮箱', dataIndex: 'targetEmail', width: 220, ellipsis: true, search: false, responsive: ['lg', 'xl', 'xxl'], render: (_, record) => record.targetEmail || '-' },
      { title: '错误信息', dataIndex: 'errorMessage', ellipsis: true, search: false, responsive: ['xl', 'xxl'], render: (_, record) => record.errorMessage || '-' },
      { title: '发送时间', dataIndex: 'createdAt', width: 180, search: false, sorter: true, render: (_, record) => formatDateTime(record.sentAt || record.createdAt) },
      { title: '发送时间范围', dataIndex: 'publishedAtRange', hideInTable: true, renderFormItem: () => <DatePicker.RangePicker showTime style={{ width: '100%' }} />, search: buildRangeSearch() },
    ],
    [responsive.isMobile],
  );

  const toolbar = useMemo(
    () =>
      buildToolbarButtons([
        { key: 'logs', label: '发送日志', onClick: () => setLogOpen(true) },
        { key: 'archive', label: '通知归档', onClick: () => setArchiveOpen(true) },
        { key: 'refresh', label: '刷新', onClick: () => void loadSmtpSettings() },
        { key: 'manual-send', permission: ['message:message:write', 'system:notification:write'], type: 'primary', label: '手动发布', onClick: openPublishDrawer },
      ]),
    [buildToolbarButtons],
  );

  return (
    <ManagementPage title="通知中心">
      <ManagementTable<ChannelRecord>
        rowKey="key"
        columns={channelColumns}
        dataSource={channelRecords}
        isMobile={responsive.isMobile}
        pagination={{ pageSize: 50, showSizeChanger: true }}
        search={false}
        toolBarRender={() => toolbar}
      />

      <ManagementDrawer
        title={channelRecord ? `配置${channelRecord.title}` : '配置通知渠道'}
        open={channelDrawerOpen}
        onClose={() => {
          setChannelDrawerOpen(false);
          setChannelRecord(null);
        }}
        footerActions={
          channelRecord?.key === 'EMAIL'
            ? [
                { key: 'cancel', label: '取消', onClick: () => setChannelDrawerOpen(false) },
                { key: 'save', label: '保存', type: 'primary', loading: savingSmtpSettings, disabled: !canManageSmtp, onClick: () => void handleSaveSmtpSettings() },
              ]
            : undefined
        }
      >
        {channelRecord?.key === 'INBOX' ? (
          <Descriptions {...detailDescriptionsProps} bordered>
            <Descriptions.Item label="通知标识">inbox_notice</Descriptions.Item>
            <Descriptions.Item label="状态">已启用</Descriptions.Item>
            <Descriptions.Item label="能力">消息中心、实时推送、已读状态、撤回</Descriptions.Item>
            <Descriptions.Item label="说明">站内信是系统默认通知渠道，无需额外配置。</Descriptions.Item>
          </Descriptions>
        ) : (
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
              <Button loading={testingSmtpSettings} disabled={!canManageSmtp} onClick={() => void handleTestSmtpSettings()}>
                发送测试邮件
              </Button>
            </Form>
          </Space>
        )}
      </ManagementDrawer>

      <ManagementDrawer
        title="通知发送日志"
        open={logOpen}
        onClose={() => setLogOpen(false)}
        width={responsive.isMobile ? '100%' : 1100}
        destroyOnHidden
      >
        <ManagementTable<MessageDeliveryLogRecord>
          actionRef={logActionRef}
          rowKey="id"
          columns={logColumns}
          isMobile={responsive.isMobile}
          pagination={{ showSizeChanger: true, pageSize: 10 }}
          search={searchConfig}
          request={buildTableRequest((params) => messageService.deliveryLogs(params, requestOptions))}
          toolBarRender={false}
        />
      </ManagementDrawer>

      <ManagementDrawer
        title="通知归档"
        open={archiveOpen}
        onClose={() => setArchiveOpen(false)}
        width={responsive.isMobile ? '100%' : 1100}
        destroyOnHidden
      >
        <ManagementTable<MessageNoticeRecord>
          actionRef={archiveActionRef}
          rowKey="id"
          columns={archiveColumns}
          isMobile={responsive.isMobile}
          pagination={{ showSizeChanger: true, pageSize: 10 }}
          search={searchConfig}
          request={buildTableRequest((params, sorter) => messageService.archiveMessages({ ...params, ...resolveSortParams(sorter) }, requestOptions))}
          toolBarRender={false}
        />
      </ManagementDrawer>

      <ManagementDrawer
        title={detailRecord ? `通知详情 · ${detailRecord.title}` : '通知详情'}
        open={detailOpen}
        onClose={() => {
          setDetailOpen(false);
          setDetailRecord(null);
        }}
        extra={
          detailRecord && detailRecord.publishStatus === 'PUBLISHED' ? (
            <Button danger disabled={!canRetractMessage} onClick={() => handleRetract(detailRecord)}>
              撤回
            </Button>
          ) : null
        }
      >
        {detailRecord ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions {...detailDescriptionsProps} bordered>
              <Descriptions.Item label="标题">{detailRecord.title}</Descriptions.Item>
              <Descriptions.Item label="目标范围">{TARGET_SCOPE_LABELS[detailRecord.targetScope] || detailRecord.targetScope}</Descriptions.Item>
              <Descriptions.Item label="目标用户">{detailRecord.targetScope === 'USER' ? detailRecord.targetUserName || (detailRecord.targetUserId ? String(detailRecord.targetUserId) : '-') : '-'}</Descriptions.Item>
              <Descriptions.Item label="目标角色">{detailRecord.targetScope === 'ROLE' ? detailRecord.targetRoleName || (detailRecord.targetRoleId ? String(detailRecord.targetRoleId) : '-') : '-'}</Descriptions.Item>
              <Descriptions.Item label="状态">{PUBLISH_STATUS_LABELS[detailRecord.publishStatus]?.text || detailRecord.publishStatus}</Descriptions.Item>
              <Descriptions.Item label="发布时间">{formatDateTime(detailRecord.publishedAt || detailRecord.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="当前阅读状态">{detailRecord.readFlag ? '已读' : '未读'}</Descriptions.Item>
              <Descriptions.Item label="创建人 ID">{detailRecord.createdBy ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="更新人 ID">{detailRecord.updatedBy ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="已读时间">{formatDateTime(detailRecord.readAt)}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{formatDateTime(detailRecord.updatedAt)}</Descriptions.Item>
            </Descriptions>
            <div>
              <Typography.Title level={5} style={{ marginTop: 0 }}>
                内容
              </Typography.Title>
              <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>{detailRecord.content}</Typography.Paragraph>
            </div>
          </Space>
        ) : null}
      </ManagementDrawer>

      <ManagementDrawer
        title="手动发布通知"
        open={publishOpen}
        onClose={closePublishDrawer}
        footerActions={[
          { key: 'cancel', label: '取消', onClick: closePublishDrawer },
          { key: 'publish', label: '发送', type: 'primary', loading: publishing, disabled: !canManualPublish, onClick: () => void handlePublish() },
        ]}
      >
        <Form
          {...publishFormProps}
          onValuesChange={(changedValues) => {
            if (Object.prototype.hasOwnProperty.call(changedValues, 'targetScope')) {
              if (changedValues.targetScope === 'TENANT') {
                form.setFieldsValue({ targetUserId: undefined, targetRoleId: undefined });
                setUserOptions([]);
                setUserLoading(false);
              } else if (changedValues.targetScope === 'USER') {
                form.setFieldsValue({ targetRoleId: undefined });
                setRoleLoading(false);
              } else if (changedValues.targetScope === 'ROLE') {
                form.setFieldsValue({ targetUserId: undefined });
                setUserOptions([]);
                setUserLoading(false);
              }
            }
          }}
        >
          <Form.Item name="channels" label="通知渠道" rules={[{ required: true, message: '请选择通知渠道' }]}>
            <Checkbox.Group
              options={[
                { label: '站内信', value: 'INBOX' },
                { label: '邮箱', value: 'EMAIL' },
              ]}
            />
          </Form.Item>
          <Form.Item name="title" label="通知标题" rules={[{ required: true, message: '请输入通知标题' }, { max: 128, message: '标题长度不能超过 128 个字符' }]}>
            <Input placeholder="例如：系统维护提醒" />
          </Form.Item>
          <Form.Item name="content" label="通知内容" rules={[{ required: true, message: '请输入通知内容' }, { max: 2000, message: '内容长度不能超过 2000 个字符' }]}>
            <Input.TextArea rows={8} placeholder="请输入要发送给用户的通知内容" />
          </Form.Item>
          <Form.Item name="targetScope" label="目标范围" rules={[{ required: true, message: '请选择目标范围' }]}>
            <Select options={[{ label: '全员', value: 'TENANT' }, { label: '指定用户', value: 'USER' }, { label: '角色分组', value: 'ROLE' }]} />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, current) => prev.targetScope !== current.targetScope}>
            {({ getFieldValue }) => {
              const currentTargetScope = getFieldValue('targetScope');
              if (currentTargetScope === 'USER') {
                return (
                  <Form.Item name="targetUserId" label="目标用户名" rules={[{ validator: async (_, value) => { if (currentTargetScope === 'USER' && !value) throw new Error('请选择目标用户名'); } }]}>
                    <Select
                      allowClear
                      showSearch
                      filterOption={false}
                      loading={userLoading}
                      placeholder="输入用户名搜索"
                      onSearch={(value) => setUserSearch(value.trim())}
                      options={userOptions.map((item) => {
                        const displayName = item.realName || item.nickname;
                        return { label: displayName ? `${item.username} · ${displayName}` : item.username, value: item.id };
                      })}
                      notFoundContent={userLoading ? '加载中...' : '暂无匹配用户'}
                    />
                  </Form.Item>
                );
              }
              if (currentTargetScope === 'ROLE') {
                return (
                  <Form.Item name="targetRoleId" label="角色分组" rules={[{ validator: async (_, value) => { if (currentTargetScope === 'ROLE' && !value) throw new Error('请选择角色分组'); } }]}>
                    <Select
                      allowClear
                      showSearch
                      loading={roleLoading}
                      placeholder="请选择角色分组"
                      filterOption={(input, option) => String(option?.label || '').toLowerCase().includes(input.toLowerCase())}
                      options={roleOptions.map((item) => ({ label: item.roleName, value: item.id }))}
                      notFoundContent={roleLoading ? '加载中...' : '暂无角色'}
                    />
                  </Form.Item>
                );
              }
              return null;
            }}
          </Form.Item>
        </Form>
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default NotificationsPage;
