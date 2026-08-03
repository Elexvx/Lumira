import { Form } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useStandardFormProps } from '@/features/form/config';
import { request } from '@/services/common/request';
import { showErrorMessage } from '@/utils/errorMessage';
import { requestMessageCreate, requestMessageRetract } from '@/services/message/api';
import type { MessageChannel, MessageNoticeRecord, PagedResult, RoleRecord, SmtpSettings, SmtpTestPayload, UserRecord, WechatOfficialAccountSettings } from '@/types/api';

import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

const MESSAGE_CENTER_REFRESH_EVENT = 'saas-message-center:refresh';

const notifyMessageCenterRefresh = () => {
  window.dispatchEvent(new Event(MESSAGE_CENTER_REFRESH_EVENT));
};

type NotificationChannelKey = MessageChannel;
type ChannelDrawerMode = 'detail' | 'edit';

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
  subject: t('ui.settings.notifications.usenotificationcenter.systemNotificationTestEmail'),
  content: t('ui.settings.notifications.usenotificationcenter.thisIsATestEmailFromTheSystem'),
  toEmail: '',
};

const buildChannelRecord = (channel: NotificationChannelRecord['key'], configured: boolean): NotificationChannelRecord => {
  if (channel === 'INBOX') {
    return {
      key: 'INBOX',
      order: 1,
      identifier: 'inbox_notice',
      type: t('ui.settings.notifications.usenotificationcenter.inAppMessage'),
      title: t('ui.settings.notifications.usenotificationcenter.inAppNotifications'),
      description: t('ui.settings.notifications.usenotificationcenter.sentToTheMessageCenterInTheTop'),
      enabled: true,
      configured: true,
    };
  }

  if (channel === 'EMAIL') {
    return {
      key: 'EMAIL',
      order: 2,
      identifier: 'email_notice',
      type: t('ui.settings.notifications.usenotificationcenter.email'),
      title: t('ui.settings.notifications.usenotificationcenter.emailNotifications'),
      description: t('ui.settings.notifications.usenotificationcenter.sendSystemNotificationsViaSmtpToUsersWith'),
      enabled: configured,
      configured,
    };
  }

  return {
    key: 'WECHAT_OFFICIAL',
    order: 3,
    identifier: 'wechat_official_notice',
    type: t('ui.settings.notifications.usenotificationcenter.wechat'),
    title: t('ui.settings.notifications.usenotificationcenter.wechatOfficialAccountNotifications'),
    description: t('ui.settings.notifications.usenotificationcenter.sendNotificationsViaWechatOfficialAccountTemplateMessages'),
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
    { key: 'EMAIL', label: t('ui.settings.notifications.usenotificationcenter.email'), configured: smtpConfigured, channel: 'EMAIL' as const },
    { key: 'WECHAT_OFFICIAL', label: t('ui.settings.notifications.usenotificationcenter.wechat'), configured: wechatConfigured, channel: 'WECHAT_OFFICIAL' as const },
  ]
    .filter((item) => !item.configured)
    .map((item) => ({
      key: item.key,
      label: item.label,
      disabled: !canManageSmtp,
      onClick: () => onAddChannel(item.channel),
    }))
    .concat(canManualPublish ? [{ key: 'manual-send', label: t('ui.settings.notifications.usenotificationcenter.manualPublish'), disabled: false, onClick: onOpenPublishDrawer }] : []);

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
      message.success(t('ui.settings.notifications.usenotificationcenter.emailNotificationSettingsSaved'));
      return true;
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return false;
      }
      showErrorMessage(error, t('ui.settings.notifications.usenotificationcenter.failedToSaveEmailNotificationSettingsPleaseTry'));
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
      message.success(t('ui.settings.notifications.usenotificationcenter.emailNotificationsDisabled'));
      return true;
    } catch (error) {
      showErrorMessage(error, t('ui.settings.notifications.usenotificationcenter.failedToDisableEmailNotificationsPleaseTryAgain'));
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
      message.success(t('ui.settings.notifications.usenotificationcenter.testEmailSent'));
      return true;
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return false;
      }
      showErrorMessage(error, t('ui.settings.notifications.usenotificationcenter.failedToSendTheTestEmailPleaseCheck'));
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
      message.success(nextSettings.configured ? t('ui.settings.notifications.usenotificationcenter.wechatNotificationSettingsSaved') : t('ui.settings.notifications.usenotificationcenter.wechatNotificationSettingsSavedButItIsNot'));
      return true;
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return false;
      }
      showErrorMessage(error, t('ui.settings.notifications.usenotificationcenter.failedToSaveWechatNotificationSettingsPleaseTry'));
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
      message.success(t('ui.settings.notifications.usenotificationcenter.wechatNotificationsDisabled'));
      return true;
    } catch (error) {
      showErrorMessage(error, t('ui.settings.notifications.usenotificationcenter.failedToDisableWechatNotificationsPleaseTryAgain'));
      return false;
    } finally {
      setSavingWechatOfficialSettings(false);
    }
  }, [canManageSmtp, requestOptions, setSavingWechatOfficialSettings, setWechatOfficialSettings]);

  const smtpConfigured = Boolean(smtpSettings?.configured);
  const wechatConfigured = Boolean(wechatOfficialSettings?.configured);

  const [channelDrawerOpen, setChannelDrawerOpen] = useState(false);
  const [channelDrawerMode, setChannelDrawerMode] = useState<ChannelDrawerMode>('detail');
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
    (record: (typeof channelRecords)[number], mode: ChannelDrawerMode = 'detail') => {
      setChannelRecord(record);
      setChannelDrawerMode(mode);
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
        handleOpenChannelDrawer(record, 'edit');
      }
    },
    [handleOpenChannelDrawer],
  );

  const closeChannelDrawer = useCallback(() => {
    setChannelDrawerOpen(false);
    setChannelDrawerMode('detail');
    setChannelRecord(null);
  }, []);

  const handleDisableChannel = useCallback(
    async (record: NotificationChannelRecord) => {
      if (!canManageSmtp) {
        return;
      }
      if (record.key === 'INBOX') {
        message.warning(t('ui.settings.notifications.usenotificationcenter.inAppMessagesAreABaseNotificationChannel'));
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
        showErrorMessage(error, t('ui.settings.notifications.usenotificationcenter.failedToDisableTheNotificationChannelPleaseTry'));
      } finally {
        setTogglingChannelKey(null);
      }
    },
    [canManageSmtp, disableSmtpSettings, disableWechatOfficialSettings],
  );

  const handleDeleteSelectedChannels = useCallback(async () => {
    if (!selectedChannelKeys.length) {
      message.info(t('ui.settings.notifications.usenotificationcenter.pleaseSelectTheNotificationChannelsToDeleteFirst'));
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
      channelDrawerMode,
      channelRecord,
      selectedChannelKeys,
      togglingChannelKey,
      channelRecords,
      setChannelDrawerOpen,
      setChannelDrawerMode,
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
  targetScope: 'PLATFORM' | 'USER' | 'ROLE';
  targetUserId?: number;
  targetRoleId?: number;
};

interface UseNotificationCenterParams {
  canManualPublish: boolean;
  canManageSmtp: boolean;
  canRetractMessage: boolean;
  requestOptions: { autoRedirectOnUnauthorized: boolean; silent: boolean };
  onReloadLog: () => void;
}

export const useNotificationCenter = ({
  canManualPublish,
  canManageSmtp,
  canRetractMessage,
  requestOptions,
  onReloadLog,
}: UseNotificationCenterParams) => {
  const [detailRecord, setDetailRecord] = useState<MessageNoticeRecord | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
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
      targetScope: 'PLATFORM',
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
      targetScope: 'PLATFORM',
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
      message.success(t('ui.settings.notifications.usenotificationcenter.notificationQueuedForDelivery'));
      closePublishDrawer();
      notifyMessageCenterRefresh();
      onReloadLog();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return;
      }
      showErrorMessage(error, t('ui.settings.notifications.usenotificationcenter.failedToSendTheNotificationPleaseTryAgain'));
    } finally {
      setPublishing(false);
    }
  }, [canManualPublish, closePublishDrawer, form, onReloadLog, requestOptions]);
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
          message.error(t('ui.settings.notifications.usenotificationcenter.failedToLoadTheRoleListPleaseTry'));
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
            message.error(t('ui.settings.notifications.usenotificationcenter.failedToLoadTheUserListPleaseTry'));
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
        message.success(t('ui.settings.notifications.usenotificationcenter.inAppMessageRetracted'));
        setDetailRecord((current) => (current && current.id === record.id ? { ...current, publishStatus: 'RETRACTED' } : current));
        notifyMessageCenterRefresh();
        onReloadLog();
      } catch (error) {
        showErrorMessage(error, t('ui.settings.notifications.usenotificationcenter.failedToRetractTheInAppMessagePlease'));
      }
    },
    [canRetractMessage, onReloadLog, requestOptions],
  );

  return {
    detailRecord,
    detailOpen,
    handleCloseDetail,
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
