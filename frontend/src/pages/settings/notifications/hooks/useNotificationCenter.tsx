import { Form } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useStandardFormProps } from '@/features/form/config';
import { request } from '@/services/common/request';
import { showErrorMessage } from '@/utils/errorMessage';
import { requestMessageCreate, requestMessageRetract } from '@/services/message/api';
import type { MessageChannel, MessageNoticeRecord, PagedResult, RoleRecord, SmtpSettings, SmtpTestPayload, UserRecord, WechatOfficialAccountSettings } from '@/types/api';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const MESSAGE_CENTER_REFRESH_EVENT = 'saas-message-center:refresh';

const notifyMessageCenterRefresh = () => {
  window.dispatchEvent(new Event(MESSAGE_CENTER_REFRESH_EVENT));
};

type NotificationChannelKey = MessageChannel;

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

const SMTP_PASSWORD_MASK = '********';
const WECHAT_APP_SECRET_MASK = '********';

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
  subject: t('系统通知测试邮件', 'System notification test email'),
  content: t('这是一封来自系统通知能力的测试邮件。', 'This is a test email from the system notification feature.'),
  toEmail: '',
};

const buildChannelRecord = (channel: NotificationChannelRecord['key'], configured: boolean): NotificationChannelRecord => {
  if (channel === 'INBOX') {
    return {
      key: 'INBOX',
      order: 1,
      identifier: 'inbox_notice',
      type: t('站内信', 'In-app message'),
      title: t('站内信通知', 'In-app notifications'),
      description: t('发送到系统右上角消息中心，支持实时推送、已读和撤回。', 'Sent to the message center in the top-right corner, with realtime push, read status, and recall support.'),
      enabled: true,
      configured: true,
    };
  }

  if (channel === 'EMAIL') {
    return {
      key: 'EMAIL',
      order: 2,
      identifier: 'email_notice',
      type: t('邮箱', 'Email'),
      title: t('邮箱通知', 'Email notifications'),
      description: t('通过 SMTP 向已绑定邮箱的用户发送系统通知。', 'Send system notifications via SMTP to users with bound email addresses.'),
      enabled: configured,
      configured,
    };
  }

  return {
    key: 'WECHAT_OFFICIAL',
    order: 3,
    identifier: 'wechat_official_notice',
    type: t('微信', 'WeChat'),
    title: t('微信服务号通知', 'WeChat official account notifications'),
    description: t('通过微信公众号/服务号模板消息向已绑定微信 OpenID 的用户发送通知。', 'Send notifications via WeChat official account template messages to users with bound WeChat OpenIDs.'),
    enabled: configured,
    configured,
  };
};

const buildChannelRecords = ({
  smtpConfigured,
  wechatConfigured,
}: {
  smtpConfigured: boolean;
  wechatConfigured: boolean;
}): NotificationChannelRecord[] => {
  const records: NotificationChannelRecord[] = [buildChannelRecord('INBOX', true), buildChannelRecord('EMAIL', smtpConfigured), buildChannelRecord('WECHAT_OFFICIAL', wechatConfigured)];
  return records.filter((record) => record.key === 'INBOX' || record.configured);
};

const buildAddChannelItems = ({
  canManageSmtp,
  canManualPublish,
  smtpConfigured,
  wechatConfigured,
  onAddChannel,
  onOpenPublishDrawer,
}: {
  canManageSmtp: boolean;
  canManualPublish: boolean;
  smtpConfigured: boolean;
  wechatConfigured: boolean;
  onAddChannel: (channel: 'EMAIL' | 'WECHAT_OFFICIAL') => void;
  onOpenPublishDrawer: () => void;
}) =>
  [
    { key: 'EMAIL', label: t('邮箱', 'Email'), configured: smtpConfigured, channel: 'EMAIL' as const },
    { key: 'WECHAT_OFFICIAL', label: t('微信', 'WeChat'), configured: wechatConfigured, channel: 'WECHAT_OFFICIAL' as const },
  ]
    .filter((item) => !item.configured)
    .map((item) => ({
      key: item.key,
      label: item.label,
      disabled: !canManageSmtp,
      onClick: () => onAddChannel(item.channel),
    }))
    .concat(canManualPublish ? [{ key: 'manual-send', label: t('手动发布', 'Manual publish'), disabled: false, onClick: onOpenPublishDrawer }] : []);

type UseNotificationChannelManagementParams = {
  canManageSmtp: boolean;
  canManualPublish: boolean;
  onOpenPublishDrawer: () => void;
  requestOptions: { autoRedirectOnUnauthorized: boolean; silent: boolean };
};

const useNotificationChannelManagement = ({
  canManageSmtp,
  canManualPublish,
  onOpenPublishDrawer,
  requestOptions,
}: UseNotificationChannelManagementParams) => {
  const [smtpSettings, setSmtpSettings] = useState<SmtpSettings | null>(null);
  const [loadingSmtpSettings, setLoadingSmtpSettings] = useState(false);
  const [smtpSettingsForm] = Form.useForm<SmtpSettings>();
  const [smtpTestForm] = Form.useForm<SmtpTestPayload>();
  const smtpFormProps = useStandardFormProps({
    form: smtpSettingsForm,
    initialValues: smtpFormInitialValues,
  });
  const smtpTestFormProps = useStandardFormProps({
    form: smtpTestForm,
    initialValues: smtpTestInitialValues,
  });

  const reloadSmtpSettings = useCallback(async () => {
    setLoadingSmtpSettings(true);
    try {
      const nextSettings = await request<SmtpSettings>('/v1/system/smtp-settings', {
        method: 'GET',
        ...requestOptions,
      });
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
  }, [requestOptions, smtpSettingsForm]);

  useEffect(() => {
    void reloadSmtpSettings();
  }, [reloadSmtpSettings]);

  const [savingSmtpSettings, setSavingSmtpSettings] = useState(false);
  const [testingSmtpSettings, setTestingSmtpSettings] = useState(false);
  const saveSmtpSettings = useCallback(async () => {
    if (!canManageSmtp) {
      return false;
    }
    setSavingSmtpSettings(true);
    try {
      const values = await smtpSettingsForm.validateFields();
      const payload = {
        ...values,
        enabled: true,
        password: values.password === SMTP_PASSWORD_MASK ? undefined : values.password,
      };
      const nextSettings = await request<SmtpSettings>('/v1/system/smtp-settings', {
        method: 'PUT',
        data: payload,
        ...requestOptions,
      });
      setSmtpSettings(nextSettings);
      smtpSettingsForm.setFieldsValue({
        ...nextSettings,
        password: nextSettings.passwordConfigured ? SMTP_PASSWORD_MASK : '',
      });
      message.success(t('邮箱通知配置已保存', 'Email notification settings saved'));
      return true;
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return false;
      }
      showErrorMessage(error, t('邮箱通知配置保存失败，请稍后重试', 'Failed to save email notification settings. Please try again later.'));
      return false;
    } finally {
      setSavingSmtpSettings(false);
    }
  }, [canManageSmtp, requestOptions, smtpSettingsForm]);

  const disableSmtpSettings = useCallback(async () => {
    if (!canManageSmtp) {
      return false;
    }
    setSavingSmtpSettings(true);
    try {
      const nextSettings = await request<SmtpSettings>('/v1/system/smtp-settings', {
        method: 'PUT',
        data: { enabled: false },
        ...requestOptions,
      });
      setSmtpSettings(nextSettings);
      message.success(t('邮箱通知已停用', 'Email notifications disabled'));
      return true;
    } catch (error) {
      showErrorMessage(error, t('邮箱通知停用失败，请稍后重试', 'Failed to disable email notifications. Please try again later.'));
      return false;
    } finally {
      setSavingSmtpSettings(false);
    }
  }, [canManageSmtp, requestOptions]);

  const testSmtpSettings = useCallback(async () => {
    setTestingSmtpSettings(true);
    try {
      const values = await smtpTestForm.validateFields();
      await request('/v1/system/smtp-settings/test', {
        method: 'POST',
        data: values,
        ...requestOptions,
      });
      message.success(t('测试邮件已发送', 'Test email sent'));
      return true;
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return false;
      }
      showErrorMessage(error, t('测试邮件发送失败，请检查配置', 'Failed to send the test email. Please check your configuration.'));
      return false;
    } finally {
      setTestingSmtpSettings(false);
    }
  }, [requestOptions, smtpTestForm]);

  const [wechatOfficialSettings, setWechatOfficialSettings] = useState<WechatOfficialAccountSettings | null>(null);
  const [loadingWechatOfficialSettings, setLoadingWechatOfficialSettings] = useState(false);
  const [wechatOfficialSettingsForm] = Form.useForm<WechatOfficialAccountSettings>();
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

  const reloadWechatOfficialSettings = useCallback(async () => {
    setLoadingWechatOfficialSettings(true);
    try {
      const nextSettings = await request<WechatOfficialAccountSettings>('/v1/system/notification/wechat-official-settings', {
        method: 'GET',
        ...requestOptions,
      });
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
  }, [requestOptions, wechatOfficialSettingsForm]);

  useEffect(() => {
    void reloadWechatOfficialSettings();
  }, [reloadWechatOfficialSettings]);

  const [savingWechatOfficialSettings, setSavingWechatOfficialSettings] = useState(false);
  const saveWechatOfficialSettings = useCallback(async () => {
    if (!canManageSmtp) {
      return false;
    }
    setSavingWechatOfficialSettings(true);
    try {
      const values = await wechatOfficialSettingsForm.validateFields();
      const payload = {
        ...values,
        enabled: true,
        appSecret: values.appSecret === WECHAT_APP_SECRET_MASK ? undefined : values.appSecret,
      };
      const nextSettings = await request<WechatOfficialAccountSettings>('/v1/system/notification/wechat-official-settings', {
        method: 'PUT',
        data: payload,
        ...requestOptions,
      });
      setWechatOfficialSettings(nextSettings);
      wechatOfficialSettingsForm.setFieldsValue({
        ...nextSettings,
        appSecret: nextSettings.appSecretConfigured ? WECHAT_APP_SECRET_MASK : '',
      });
      message.success(nextSettings.configured ? t('微信通知配置已保存', 'WeChat notification settings saved') : t('微信通知配置已保存，当前仍未完全启用', 'WeChat notification settings saved, but it is not fully enabled yet'));
      return true;
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return false;
      }
      showErrorMessage(error, t('微信通知配置保存失败，请稍后重试', 'Failed to save WeChat notification settings. Please try again later.'));
      return false;
    } finally {
      setSavingWechatOfficialSettings(false);
    }
  }, [canManageSmtp, requestOptions, setSavingWechatOfficialSettings, setWechatOfficialSettings, wechatOfficialSettingsForm]);

  const disableWechatOfficialSettings = useCallback(async () => {
    if (!canManageSmtp) {
      return false;
    }
    setSavingWechatOfficialSettings(true);
    try {
      const nextSettings = await request<WechatOfficialAccountSettings>('/v1/system/notification/wechat-official-settings', {
        method: 'PUT',
        data: { enabled: false },
        ...requestOptions,
      });
      setWechatOfficialSettings(nextSettings);
      message.success(t('微信通知已停用', 'WeChat notifications disabled'));
      return true;
    } catch (error) {
      showErrorMessage(error, t('微信通知停用失败，请稍后重试', 'Failed to disable WeChat notifications. Please try again later.'));
      return false;
    } finally {
      setSavingWechatOfficialSettings(false);
    }
  }, [canManageSmtp, requestOptions, setSavingWechatOfficialSettings, setWechatOfficialSettings]);

  const smtpConfigured = Boolean(smtpSettings?.configured);
  const wechatConfigured = Boolean(wechatOfficialSettings?.configured);

  const [channelDrawerOpen, setChannelDrawerOpen] = useState(false);
  const [channelRecord, setChannelRecord] = useState<NotificationChannelRecord | null>(null);
  const [selectedChannelKeys, setSelectedChannelKeys] = useState<MessageChannel[]>([]);
  const [togglingChannelKey, setTogglingChannelKey] = useState<MessageChannel | null>(null);
  const channelRecords = useMemo(
    () =>
      buildChannelRecords({
        smtpConfigured,
        wechatConfigured,
      }),
    [smtpConfigured, wechatConfigured],
  );

  const handleOpenChannelDrawer = useCallback(
    (record: (typeof channelRecords)[number]) => {
      setChannelRecord(record);
      setChannelDrawerOpen(true);
      if (record.key === 'EMAIL') {
        void reloadSmtpSettings();
      }
      if (record.key === 'WECHAT_OFFICIAL') {
        void reloadWechatOfficialSettings();
      }
    },
    [reloadSmtpSettings, reloadWechatOfficialSettings],
  );

  const handleAddChannel = useCallback(
    (channel: 'EMAIL' | 'WECHAT_OFFICIAL') => {
      const record = buildChannelRecord(channel, false);
      if (record) {
        handleOpenChannelDrawer(record);
      }
    },
    [handleOpenChannelDrawer],
  );

  const closeChannelDrawer = useCallback(() => {
    setChannelDrawerOpen(false);
    setChannelRecord(null);
  }, []);

  const handleDisableChannel = useCallback(
    async (record: NotificationChannelRecord) => {
      if (!canManageSmtp) {
        return;
      }
      if (record.key === 'INBOX') {
        message.warning(t('站内信是基础通知渠道，不能删除', 'In-app messages are a base notification channel and cannot be removed'));
        return;
      }
      setTogglingChannelKey(record.key);
      try {
        if (record.key === 'EMAIL') {
          const success = await disableSmtpSettings();
          if (!success) {
            return;
          }
        }
        if (record.key === 'WECHAT_OFFICIAL') {
          const success = await disableWechatOfficialSettings();
          if (!success) {
            return;
          }
        }
        setSelectedChannelKeys((keys) => keys.filter((key) => key !== record.key));
      } catch (error) {
        showErrorMessage(error, t('通知渠道停用失败，请稍后重试', 'Failed to disable the notification channel. Please try again later.'));
      } finally {
        setTogglingChannelKey(null);
      }
    },
    [canManageSmtp, disableSmtpSettings, disableWechatOfficialSettings],
  );

  const handleDeleteSelectedChannels = useCallback(async () => {
    if (!selectedChannelKeys.length) {
      message.info(t('请先选择要删除的通知渠道', 'Please select the notification channels to delete first'));
      return;
    }
    const selectedRecords = channelRecords.filter((record) => selectedChannelKeys.includes(record.key));
    for (const record of selectedRecords) {
      await handleDisableChannel(record);
    }
  }, [channelRecords, handleDisableChannel, selectedChannelKeys]);

  const addChannelItems = useMemo(
    () =>
      buildAddChannelItems({
        canManageSmtp,
        canManualPublish,
        smtpConfigured,
        wechatConfigured,
        onAddChannel: handleAddChannel,
        onOpenPublishDrawer,
      }),
    [canManageSmtp, canManualPublish, handleAddChannel, onOpenPublishDrawer, smtpConfigured, wechatConfigured],
  );

  const handleSaveSmtpSettings = useCallback(async () => {
    const success = await saveSmtpSettings();
    if (success) {
      closeChannelDrawer();
    }
  }, [closeChannelDrawer, saveSmtpSettings]);

  const handleSaveWechatOfficialSettings = useCallback(async () => {
    const success = await saveWechatOfficialSettings();
    if (success) {
      closeChannelDrawer();
    }
  }, [closeChannelDrawer, saveWechatOfficialSettings]);

  const handleTestSmtpSettings = useCallback(async () => {
    await testSmtpSettings();
  }, [testSmtpSettings]);

  return {
    channelStatePack: {
      channelDrawerOpen,
      channelRecord,
      selectedChannelKeys,
      togglingChannelKey,
      channelRecords,
      setChannelDrawerOpen,
      setChannelRecord,
      setSelectedChannelKeys,
      setTogglingChannelKey,
    },
    channelActionPack: {
      handleOpenChannelDrawer,
      closeChannelDrawer,
      handleDisableChannel,
      handleDeleteSelectedChannels,
      addChannelItems,
    },
    channelSettingsPack: {
      loadingSmtpSettings,
      loadingWechatOfficialSettings,
      savingSmtpSettings,
      savingWechatOfficialSettings,
      testingSmtpSettings,
      wechatOfficialAppSecretConfigured: wechatOfficialSettings?.appSecretConfigured ?? false,
      smtpFormProps,
      wechatOfficialFormProps,
      smtpTestFormProps,
    },
    channelSubmitPack: {
      handleSaveSmtpSettings,
      handleSaveWechatOfficialSettings,
      handleTestSmtpSettings,
    },
  };
};

type PublishFormValues = {
  title: string;
  content: string;
  channels: MessageChannel[];
  targetScope: 'TENANT' | 'USER' | 'ROLE';
  targetUserId?: number;
  targetRoleId?: number;
};

interface UseNotificationCenterParams {
  canManualPublish: boolean;
  canManageSmtp: boolean;
  canRetractMessage: boolean;
  requestOptions: { autoRedirectOnUnauthorized: boolean; silent: boolean };
  onReloadArchive: () => void;
  onReloadLog: () => void;
}

export const useNotificationCenter = ({
  canManualPublish,
  canManageSmtp,
  canRetractMessage,
  requestOptions,
  onReloadArchive,
  onReloadLog,
}: UseNotificationCenterParams) => {
  const [detailRecord, setDetailRecord] = useState<MessageNoticeRecord | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [logOpen, setLogOpen] = useState(false);
  const [publishOpen, setPublishOpen] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [form] = Form.useForm<PublishFormValues>();
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
  const closePublishDrawer = useCallback(() => {
    setPublishOpen(false);
    form.resetFields();
  }, [form]);
  const openPublishDrawer = useCallback(() => {
    form.resetFields();
    form.setFieldsValue({
      title: '',
      content: '',
      channels: ['INBOX'],
      targetScope: 'TENANT',
      targetUserId: undefined,
      targetRoleId: undefined,
    });
    setPublishOpen(true);
  }, [form]);
  const handleOpenDetail = useCallback((record: MessageNoticeRecord) => {
    setDetailRecord(record);
    setDetailOpen(true);
  }, []);
  const handleCloseDetail = useCallback(() => {
    setDetailOpen(false);
    setDetailRecord(null);
  }, []);
  const [userSearch, setUserSearch] = useState('');
  const [userOptions, setUserOptions] = useState<UserRecord[]>([]);
  const [userLoading, setUserLoading] = useState(false);
  const [roleOptions, setRoleOptions] = useState<RoleRecord[]>([]);
  const [roleLoading, setRoleLoading] = useState(false);
  const handlePublish = useCallback(async () => {
    if (!canManualPublish) {
      return;
    }
    setPublishing(true);
    try {
      const values = await form.validateFields();
      await requestMessageCreate({
        method: 'POST',
        data: {
          title: values.title,
          content: values.content,
          channels: values.channels,
          targetScope: values.targetScope,
          targetUserId: values.targetScope === 'USER' ? values.targetUserId : undefined,
          targetRoleId: values.targetScope === 'ROLE' ? values.targetRoleId : undefined,
        },
        ...requestOptions,
      });
      message.success(t('通知已提交发送', 'Notification queued for delivery'));
      closePublishDrawer();
      notifyMessageCenterRefresh();
      onReloadArchive();
      onReloadLog();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return;
      }
      showErrorMessage(error, t('通知发送失败，请稍后重试', 'Failed to send the notification. Please try again later.'));
    } finally {
      setPublishing(false);
    }
  }, [canManualPublish, closePublishDrawer, form, onReloadArchive, onReloadLog, requestOptions]);
  const targetScope = Form.useWatch('targetScope', form);
  useEffect(() => {
    if (!publishOpen || !canManualPublish || roleOptions.length > 0) {
      return;
    }

    let active = true;
    setRoleLoading(true);
      void request<PagedResult<RoleRecord>>('/v1/system/roles', {
        method: 'GET',
        params: { pageNo: 1, pageSize: 200 },
        ...requestOptions,
      })
      .then((result) => {
        if (active) {
          setRoleOptions(result.records || []);
        }
      })
      .catch(() => {
        if (active) {
          message.error(t('角色列表加载失败，请稍后重试', 'Failed to load the role list. Please try again later.'));
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
      void request<PagedResult<UserRecord>>('/v1/system/users', {
        method: 'GET',
        params: { username: userSearch || undefined, pageNo: 1, pageSize: 20 },
        ...requestOptions,
      })
        .then((result) => {
          if (active) {
            setUserOptions(result.records || []);
          }
        })
        .catch(() => {
          if (active) {
            message.error(t('用户名列表加载失败，请稍后重试', 'Failed to load the user list. Please try again later.'));
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
  const resetPublishTargets = () => {
    setUserSearch('');
    setUserOptions([]);
    setUserLoading(false);
    setRoleLoading(false);
  };
  const handleClosePublishDrawer = () => {
    resetPublishTargets();
    closePublishDrawer();
  };

  const handleOpenPublishDrawer = () => {
    resetPublishTargets();
    openPublishDrawer();
  };

  const channelManagement = useNotificationChannelManagement({
    canManageSmtp,
    canManualPublish,
    onOpenPublishDrawer: handleOpenPublishDrawer,
    requestOptions,
  });
  const handleRetract = useCallback(
    async (record: MessageNoticeRecord) => {
      if (!canRetractMessage) {
        return;
      }
      try {
        await requestMessageRetract(record.id, {
          method: 'POST',
          ...requestOptions,
        });
        message.success(t('站内信已撤回', 'In-app message retracted'));
        setDetailRecord((current) => (current && current.id === record.id ? { ...current, publishStatus: 'RETRACTED' } : current));
        notifyMessageCenterRefresh();
        onReloadArchive();
        onReloadLog();
      } catch (error) {
        showErrorMessage(error, t('站内信撤回失败，请稍后重试', 'Failed to retract the in-app message. Please try again later.'));
      }
    },
    [canRetractMessage, onReloadArchive, onReloadLog, requestOptions],
  );

  return {
    detailRecord,
    detailOpen,
    handleCloseDetail,
    archiveOpen,
    setArchiveOpen,
    logOpen,
    setLogOpen,
    publishOpen,
    publishing,
    form,
    publishFormProps,
    closePublishDrawer: handleClosePublishDrawer,
    openPublishDrawer: handleOpenPublishDrawer,
    handlePublish,
    userSearch,
    userOptions,
    userLoading,
    roleOptions,
    roleLoading,
    setUserSearch,
    setUserOptions,
    setUserLoading,
    setRoleLoading,
    ...channelManagement.channelStatePack,
    ...channelManagement.channelActionPack,
    ...channelManagement.channelSettingsPack,
    ...channelManagement.channelSubmitPack,
    handleOpenDetail,
    handleRetract,
  };
};
