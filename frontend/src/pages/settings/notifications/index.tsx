import { DeleteOutlined, DownOutlined, PlusOutlined, CheckOutlined } from '@ant-design/icons';
import { Button, Checkbox, DatePicker, Descriptions, Dropdown, Form, Input, InputNumber, Popconfirm, Select, Space, Switch, Tag, Typography, message, theme } from 'antd';
import type { MenuProps } from 'antd';
import dayjs from 'dayjs';
import type { Key } from 'react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
import type { MessageChannel, MessageDeliveryLogRecord, MessageNoticeRecord, RoleRecord, SmtpSettings, SmtpTestPayload, UserRecord, WechatOfficialAccountSettings } from '@/types/api';
import { TableActionBar } from '@/features/table/TableActionBar';
import { confirmAction } from '@/utils/confirm';
import { notifyMessageCenterRefresh } from '@/components/message-center/messageCenterEvents';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';


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
  enabled: true,
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
const WECHAT_APP_SECRET_MASK = '********';

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
  const { token } = theme.useToken();
  const archiveActionRef = useRef<ActionType | undefined>(undefined);
  const logActionRef = useRef<ActionType | undefined>(undefined);
  const { actionPermission, responsive, searchConfig } = usePagePermissionActions();
  const [detailRecord, setDetailRecord] = useState<MessageNoticeRecord | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [channelDrawerOpen, setChannelDrawerOpen] = useState(false);
  const [channelRecord, setChannelRecord] = useState<ChannelRecord | null>(null);
  const [selectedChannelKeys, setSelectedChannelKeys] = useState<Key[]>([]);
  const [togglingChannelKey, setTogglingChannelKey] = useState<MessageChannel | null>(null);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [logOpen, setLogOpen] = useState(false);
  const [smtpSettings, setSmtpSettings] = useState<SmtpSettings | null>(null);
  const [wechatOfficialSettings, setWechatOfficialSettings] = useState<WechatOfficialAccountSettings | null>(null);
  const [loadingSmtpSettings, setLoadingSmtpSettings] = useState(false);
  const [savingSmtpSettings, setSavingSmtpSettings] = useState(false);
  const [loadingWechatOfficialSettings, setLoadingWechatOfficialSettings] = useState(false);
  const [savingWechatOfficialSettings, setSavingWechatOfficialSettings] = useState(false);
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
  const [wechatOfficialSettingsForm] = Form.useForm<WechatOfficialAccountSettings>();
  const [smtpTestForm] = Form.useForm<SmtpTestPayload>();
  const targetScope = Form.useWatch('targetScope', form);
  const wechatOfficialAppSecretConfigured = wechatOfficialSettings?.appSecretConfigured ?? false;
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
  const wechatOfficialFormProps = useStandardFormProps({
    form: wechatOfficialSettingsForm,
    initialValues: {
      enabled: false,
      appId: '',
      appSecret: '',
      templateId: '',
      detailUrl: '',
    },
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

  const loadWechatOfficialSettings = async () => {
    setLoadingWechatOfficialSettings(true);
    try {
      const nextSettings = await systemService.wechatOfficialAccountSettings(requestOptions);
      setWechatOfficialSettings(nextSettings);
      wechatOfficialSettingsForm.setFieldsValue({
        enabled: nextSettings.enabled ?? false,
        appId: nextSettings.appId || '',
        appSecret: nextSettings.appSecretConfigured ? WECHAT_APP_SECRET_MASK : '',
        templateId: nextSettings.templateId || '',
        detailUrl: nextSettings.detailUrl || '',
      });
    } catch {
      setWechatOfficialSettings(null);
    } finally {
      setLoadingWechatOfficialSettings(false);
    }
  };

  useEffect(() => {
    void loadSmtpSettings();
    void loadWechatOfficialSettings();
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
    () => {
      const records: ChannelRecord[] = [
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
      {
        key: 'WECHAT_OFFICIAL',
        order: 3,
        identifier: 'wechat_official_notice',
        type: '微信',
        title: '微信服务号通知',
        description: '通过微信公众号/服务号模板消息向已绑定微信 OpenID 的用户发送通知。',
        enabled: Boolean(wechatOfficialSettings?.configured),
        configured: Boolean(wechatOfficialSettings?.configured),
      },
      ];
      return records.filter((record) => record.key === 'INBOX' || record.configured);
    },
    [smtpSettings?.configured, wechatOfficialSettings?.configured],
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
      showErrorMessage(error, '通知发送失败，请稍后重试');
    } finally {
      setPublishing(false);
    }
  };

  const handleOpenChannelDrawer = useCallback((record: ChannelRecord) => {
    setChannelRecord(record);
    setChannelDrawerOpen(true);
    if (record.key === 'EMAIL') {
      void loadSmtpSettings();
    }
    if (record.key === 'WECHAT_OFFICIAL') {
      void loadWechatOfficialSettings();
    }
  }, []);

  const handleSaveSmtpSettings = async () => {
    if (!canManageSmtp) {
      return;
    }
    setSavingSmtpSettings(true);
    try {
      const values = await smtpSettingsForm.validateFields();
      const payload = {
        ...values,
        enabled: true,
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
      showErrorMessage(error, '邮箱通知配置保存失败，请稍后重试');
    } finally {
      setSavingSmtpSettings(false);
    }
  };

  const handleSaveWechatOfficialSettings = async () => {
    if (!canManageSmtp) {
      return;
    }
    setSavingWechatOfficialSettings(true);
    try {
      const values = await wechatOfficialSettingsForm.validateFields();
      const payload = {
        ...values,
        enabled: true,
        appSecret: values.appSecret === WECHAT_APP_SECRET_MASK ? undefined : values.appSecret,
      };
      const nextSettings = await systemService.updateWechatOfficialAccountSettings(payload, requestOptions);
      setWechatOfficialSettings(nextSettings);
      wechatOfficialSettingsForm.setFieldsValue({
        ...nextSettings,
        appSecret: nextSettings.appSecretConfigured ? WECHAT_APP_SECRET_MASK : '',
      });
      message.success(nextSettings.configured ? '微信通知配置已保存' : '微信通知配置已保存，当前仍未完全启用');
      setChannelDrawerOpen(false);
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return;
      }
      showErrorMessage(error, '微信通知配置保存失败，请稍后重试');
    } finally {
      setSavingWechatOfficialSettings(false);
    }
  };

  const handleDisableChannel = useCallback(async (record: ChannelRecord) => {
    if (!canManageSmtp) {
      return;
    }
    if (record.key === 'INBOX') {
      message.warning('站内信是基础通知渠道，不能删除');
      return;
    }
    setTogglingChannelKey(record.key);
    try {
      if (record.key === 'EMAIL') {
        const nextSettings = await systemService.updateSmtpSettings({ enabled: false }, requestOptions);
        setSmtpSettings(nextSettings);
        message.success('邮箱通知已停用');
      }
      if (record.key === 'WECHAT_OFFICIAL') {
        const nextSettings = await systemService.updateWechatOfficialAccountSettings({ enabled: false }, requestOptions);
        setWechatOfficialSettings(nextSettings);
        message.success('微信通知已停用');
      }
      setSelectedChannelKeys((keys) => keys.filter((key) => key !== record.key));
    } catch (error) {
      showErrorMessage(error, '通知渠道停用失败，请稍后重试');
    } finally {
      setTogglingChannelKey(null);
    }
  }, [canManageSmtp, requestOptions]);

  const handleDeleteSelectedChannels = useCallback(async () => {
    if (!selectedChannelKeys.length) {
      message.info('请先选择要删除的通知渠道');
      return;
    }
    const selectedRecords = channelRecords.filter((record) => selectedChannelKeys.includes(record.key));
    for (const record of selectedRecords) {
      await handleDisableChannel(record);
    }
  }, [channelRecords, handleDisableChannel, selectedChannelKeys]);

  const handleAddChannel = useCallback((channel: MessageChannel) => {
    const record = {
      INBOX: channelRecords.find((item) => item.key === 'INBOX'),
      EMAIL: {
        key: 'EMAIL',
        order: 2,
        identifier: 'email_notice',
        type: '邮箱',
        title: '邮箱通知',
        description: '通过 SMTP 向已绑定邮箱的用户发送系统通知。',
        enabled: true,
        configured: false,
      },
      WECHAT_OFFICIAL: {
        key: 'WECHAT_OFFICIAL',
        order: 3,
        identifier: 'wechat_official_notice',
        type: '微信',
        title: '微信服务号通知',
        description: '通过微信公众号/服务号模板消息向已绑定微信 OpenID 的用户发送通知。',
        enabled: true,
        configured: false,
      },
    }[channel] as ChannelRecord | undefined;
    if (record) {
      handleOpenChannelDrawer(record);
    }
  }, [channelRecords, handleOpenChannelDrawer]);

  const addChannelItems = useMemo<MenuProps['items']>(
    () =>
      [
        { key: 'EMAIL', label: '邮箱', configured: Boolean(smtpSettings?.configured), channel: 'EMAIL' as const },
        { key: 'WECHAT_OFFICIAL', label: '微信', configured: Boolean(wechatOfficialSettings?.configured), channel: 'WECHAT_OFFICIAL' as const },
      ]
        .filter((item) => !item.configured)
        .map((item) => ({
          key: item.key,
          label: item.label,
          disabled: !canManageSmtp,
          onClick: () => handleAddChannel(item.channel),
        }))
        .concat(canManualPublish ? [{ key: 'manual-send', label: '手动发布', disabled: false, onClick: openPublishDrawer }] : []),
    [canManageSmtp, canManualPublish, handleAddChannel, smtpSettings?.configured, wechatOfficialSettings?.configured],
  );

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
      showErrorMessage(error, '测试邮件发送失败，请检查配置');
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
          showErrorMessage(error, '站内信撤回失败，请稍后重试');
        }
      },
    });
  };

  const channelColumns = useMemo<ProColumns<ChannelRecord>[]>(
    () => [
      { title: '序号', dataIndex: 'order', width: 80, search: false },
      { title: '通知标识', dataIndex: 'identifier', width: 180, copyable: true, search: false },
      { title: '通知类型', dataIndex: 'type', width: 140, search: false, render: (_, record) => renderTag(record.type, CHANNEL_LABELS[record.key]?.color || 'blue') },
      { title: '标题', dataIndex: 'title', width: 160, search: false, render: (_, record) => <Typography.Text strong>{record.title}</Typography.Text> },
      { title: '描述', dataIndex: 'description', width: 220, search: false, ellipsis: true },
      {
        title: '启用',
        dataIndex: 'enabled',
        width: 120,
        align: 'center',
        search: false,
        render: (_, record) => (record.enabled ? <CheckOutlined style={{ color: token.colorSuccess }} /> : null),
      },
      {
        title: '操作',
        valueType: 'option',
        width: 240,
        fixed: responsive.isDesktop ? 'right' : undefined,
        render: (_, record) => (
          <TableActionBar
            isMobile={responsive.isMobile}
            inlineCount={responsive.isMobile ? 0 : 3}
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
              { key: 'delete', label: '删除', danger: true, disabled: !canManageSmtp || record.key === 'INBOX', onClick: () => void handleDisableChannel(record) },
            ]}
          />
        ),
      },
    ],
    [canManageSmtp, handleDisableChannel, responsive.isDesktop, responsive.isMobile, token.colorSuccess, togglingChannelKey],
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
        valueEnum: { INBOX: { text: '站内信' }, EMAIL: { text: '邮箱' }, WECHAT_OFFICIAL: { text: '微信' } },
        renderFormItem: () => <Select allowClear options={[{ label: '站内信', value: 'INBOX' }, { label: '邮箱', value: 'EMAIL' }, { label: '微信', value: 'WECHAT_OFFICIAL' }]} placeholder="全部" />,
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
      { title: '接收标识', dataIndex: 'targetEmail', width: 220, ellipsis: true, search: false, responsive: ['lg', 'xl', 'xxl'], render: (_, record) => record.targetEmail || '-' },
      { title: '错误信息', dataIndex: 'errorMessage', ellipsis: true, search: false, responsive: ['xl', 'xxl'], render: (_, record) => record.errorMessage || '-' },
      { title: '发送时间', dataIndex: 'createdAt', width: 180, search: false, sorter: true, render: (_, record) => formatDateTime(record.sentAt || record.createdAt) },
      { title: '发送时间范围', dataIndex: 'publishedAtRange', hideInTable: true, renderFormItem: () => <DatePicker.RangePicker showTime style={{ width: '100%' }} />, search: buildRangeSearch() },
    ],
    [responsive.isMobile],
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
        tableAlertRender={false}
        rowSelection={{
          selectedRowKeys: selectedChannelKeys,
          onChange: setSelectedChannelKeys,
        }}
        toolBarRender={() => [
          <Popconfirm
            key="delete"
            title="删除通知渠道"
            description="选中的通知渠道会被停用，站内信会保留。"
            okText="确认"
            cancelText="取消"
            onConfirm={() => void handleDeleteSelectedChannels()}
          >
            <Button disabled={!canManageSmtp} icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>,
          <Dropdown key="add" trigger={['click']} menu={{ items: addChannelItems }} placement="bottomRight">
            <Button type="primary" disabled={!addChannelItems?.length} icon={<PlusOutlined />}>
              添加 <DownOutlined />
            </Button>
          </Dropdown>,
        ]}
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
            : channelRecord?.key === 'WECHAT_OFFICIAL'
              ? [
                  { key: 'cancel', label: '取消', onClick: () => setChannelDrawerOpen(false) },
                  { key: 'save', label: '保存', type: 'primary', loading: savingWechatOfficialSettings, disabled: !canManageSmtp, onClick: () => void handleSaveWechatOfficialSettings() },
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
        ) : channelRecord?.key === 'EMAIL' ? (
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
        ) : (
          <Form {...wechatOfficialFormProps} disabled={loadingWechatOfficialSettings || !canManageSmtp}>
            <Form.Item name="appId" label="AppID" rules={[{ required: true, message: '请输入微信公众号 AppID' }]}>
              <Input placeholder="微信公众号或服务号 AppID" />
            </Form.Item>
            <Form.Item name="appSecret" label="AppSecret" rules={!wechatOfficialAppSecretConfigured ? [{ required: true, message: '请输入微信公众号 AppSecret' }] : undefined} extra={wechatOfficialAppSecretConfigured ? '当前密钥已脱敏显示，留空则保持现有密钥' : '用于获取公众号 access_token'}>
              <Input.Password placeholder="留空则保持现有密钥" autoComplete="new-password" />
            </Form.Item>
            <Form.Item name="templateId" label="模板消息 ID" rules={[{ required: true, message: '请输入模板消息 ID' }]}>
              <Input placeholder="公众号后台配置的模板消息 ID" />
            </Form.Item>
            <Form.Item name="detailUrl" label="通知详情链接" extra="模板消息点击后打开的页面，可留空">
              <Input placeholder="例如：https://test.elexvx.com/messages" />
            </Form.Item>
          </Form>
        )}
      </ManagementDrawer>

      <ManagementDrawer
        title="通知发送日志"
        open={logOpen}
        onClose={() => setLogOpen(false)}
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
                { label: '微信', value: 'WECHAT_OFFICIAL' },
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
