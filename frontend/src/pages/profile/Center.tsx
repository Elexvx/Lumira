import { useQuery } from '@tanstack/react-query';
import { KeyOutlined, UserOutlined } from '@ant-design/icons';
import { formatMessage } from '@umijs/max';
import { Avatar, Button, Card, Col, Empty, Form, List, Popconfirm, Row, Space, Timeline, Typography, Upload, message, type UploadProps } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useStandardFormProps } from '@/features/form/config';
import { ManagementPage } from '@/features/management';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import { isPasskeySupported, toPublicKeyCreationOptions, toRegistrationPayload } from '@/auth/passkey';
import { authService } from '@/services/auth';
import { profileService } from '@/services/profile';
import { secondFactorService } from '@/services/secondFactor';
import { systemService } from '@/services/system';
import { BindSecondFactorModal } from '@/pages/profile/center/components/BindSecondFactorModal';
import { BoundProviderCard } from '@/pages/profile/center/components/BoundProviderCard';
import { ContactBindModal } from '@/pages/profile/center/components/ContactBindModal';
import { LoginMethodCard } from '@/pages/profile/center/components/LoginMethodCard';
import { ProfileCompletionCard } from '@/pages/profile/center/components/ProfileCompletionCard';
import { ProfileBasicCard } from '@/pages/profile/center/components/ProfileBasicCard';
import { buildVisibleProfileFields } from '@/pages/profile/center/utils';
import type { ProfileCompletionItem, ProfileSummary, SecondFactorChallenge, SecondFactorProviderStatus } from '@/types/api';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';


const ProfileCenterPage = () => {
  const [profileForm] = Form.useForm();
  const { initialState, setInitialState } = useInitialStateModel();
  const responsive = useResponsive();
  const profileQuery = useQuery({
    queryKey: ['profile-summary', initialState?.currentUser?.userId],
    queryFn: async () => profileService.summary(API_OPTS.NO_REDIRECT),
  });
  const summary = profileQuery.data;
  const currentUser = summary?.currentUser || initialState?.currentUser;
  const recentLoginLogs = summary?.recentLoginLogs || [];
  const profileFieldSettings = summary?.profileFieldSettings || [];
  const summaryMobileBindAvailable = Boolean(summary?.mobileBindAvailable ?? summary?.mobileBindVerificationRequired);
  const summaryEmailBindAvailable = Boolean(summary?.emailBindAvailable ?? summary?.emailBindVerificationRequired);
  const loginCapabilitiesQuery = useQuery({
    queryKey: ['profile-login-capabilities'],
    queryFn: async () =>
      systemService.publicLoginCapabilities({
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        silent: true,
      }),
  });
  const loginCapabilities = loginCapabilitiesQuery.data;
  const emailLoginAvailable = Boolean(loginCapabilities?.emailLoginAvailable);
  const smsLoginAvailable = Boolean(loginCapabilities?.smsLoginAvailable);
  const mobileBindAvailable = summaryMobileBindAvailable || smsLoginAvailable;
  const emailBindAvailable = summaryEmailBindAvailable || emailLoginAvailable;
  const mobileBindVerificationRequired = mobileBindAvailable;
  const emailBindVerificationRequired = emailBindAvailable;
  const mobileBindingVisible = mobileBindAvailable;
  const emailBindingVisible = emailBindAvailable;
  const visibleProfileFields = useMemo(() => buildVisibleProfileFields(profileFieldSettings), [profileFieldSettings]);
  const avatarValue = Form.useWatch('avatarUrl', profileForm);
  const hasVisibleProfileFields = visibleProfileFields.size > 0;
  const profileFormProps = useStandardFormProps({ form: profileForm });
  const providersQuery = useQuery({
    queryKey: ['profile-second-factor-providers', currentUser?.userId],
    queryFn: async () => secondFactorService.currentProviders(API_OPTS.NO_REDIRECT),
    enabled: Boolean(currentUser),
  });
  const passkeyQuery = useQuery({
    queryKey: ['profile-passkeys', currentUser?.userId],
    queryFn: async () => authService.passkeyCredentials(API_OPTS.NO_REDIRECT),
    enabled: Boolean(currentUser),
  });
  const [profileSaving, setProfileSaving] = useState(false);
  const [profileEditingOpen, setProfileEditingOpen] = useState(false);
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [bindModalOpen, setBindModalOpen] = useState(false);
  const [bindingProvider, setBindingProvider] = useState<SecondFactorProviderStatus | null>(null);
  const [bindingChallenge, setBindingChallenge] = useState<SecondFactorChallenge | null>(null);
  const [bindingLoading, setBindingLoading] = useState(false);
  const [bindingSubmitting, setBindingSubmitting] = useState(false);
  const [bindingCompleted, setBindingCompleted] = useState(false);
  const [bindingAlert, setBindingAlert] = useState<{ type: 'info' | 'warning' | 'error'; message: string }>();
  const [contactBindType, setContactBindType] = useState<'mobile' | 'email' | null>(null);
  const [contactBindChallenge, setContactBindChallenge] = useState<SecondFactorChallenge | null>(null);
  const [contactBindChallengeTarget, setContactBindChallengeTarget] = useState<string | null>(null);
  const [contactBindChallengeLoading, setContactBindChallengeLoading] = useState(false);
  const [contactBindSubmitting, setContactBindSubmitting] = useState(false);
  const [contactBindAlert, setContactBindAlert] = useState<string | null>(null);
  const [contactBindForm] = Form.useForm<{ value?: string; verificationCode?: string }>();
  const contactBindValue = Form.useWatch('value', contactBindForm);
  const profileBasicCardRef = useRef<HTMLDivElement | null>(null);
  const profileCompletionSummary = summary?.profileCompletion;

  const contactBindFormProps = useStandardFormProps({
    form: contactBindForm,
    initialValues: { value: '' },
  });

  useEffect(() => {
    if (!currentUser) {
      return;
    }
    profileForm.setFieldsValue({
      avatarUrl: currentUser.avatarUrl || '',
      nickname: currentUser.nickname || '',
      realName: currentUser.realName || '',
      mobile: currentUser.mobile || '',
      email: currentUser.email || '',
      birthMonth: currentUser.birthMonth ? dayjs(currentUser.birthMonth, 'YYYY-MM') : null,
      gender: currentUser.gender || undefined,
      region: currentUser.region || '',
      idCardNumber: currentUser.idCardNumber || '',
    });
  }, [currentUser, profileForm]);

  const handleAvatarBeforeCrop = (file: File) => {
    if (!file.type.startsWith('image/')) {
      message.error(formatMessage({ id: 'page.profile.avatar.selectImage', defaultMessage: 'Please select an image file' }));
      return false;
    }
    return true;
  };

  const handleAvatarUploadRequest: UploadProps['customRequest'] = async ({ file, onSuccess, onError }) => {
    try {
      setAvatarUploading(true);
      const avatarUrl = await profileService.uploadAvatar(file as File, {
        autoRedirectOnUnauthorized: false,
        silent: true,
      });
      profileForm.setFieldValue('avatarUrl', avatarUrl);
      setInitialState((prev) =>
        prev?.currentUser
          ? {
              ...prev,
              currentUser: {
                ...prev.currentUser,
                avatarUrl,
              },
            }
          : prev,
      );
      message.success(formatMessage({ id: 'page.profile.avatar.uploadSuccess', defaultMessage: 'Avatar uploaded, please click save profile' }));
      onSuccess?.(avatarUrl);
    } catch (error) {
      onError?.(error as Error);
      showErrorMessage(error, formatMessage({ id: 'page.profile.avatar.uploadFailed', defaultMessage: 'Avatar upload failed, please try again later' }));
    } finally {
      setAvatarUploading(false);
    }
  };

  const resetBindState = () => {
    setBindingProvider(null);
    setBindingChallenge(null);
    setBindingLoading(false);
    setBindingSubmitting(false);
    setBindingCompleted(false);
    setBindingAlert(undefined);
  };

  const closeBindModal = () => {
    if (bindingSubmitting) {
      return;
    }
    setBindModalOpen(false);
    window.setTimeout(() => {
      resetBindState();
    }, 0);
  };

  const openBindModal = async (provider: SecondFactorProviderStatus) => {
    setBindingProvider(provider);
    setBindingChallenge(null);
    setBindingLoading(true);
    setBindingSubmitting(false);
    setBindingCompleted(false);
    setBindingAlert(undefined);
    setBindModalOpen(true);
    try {
      const challenge = await secondFactorService.currentBind(provider.factorCode, {
        autoRedirectOnUnauthorized: false,
        silent: true,
      });
      setBindingChallenge(challenge);
    } catch (error) {
      setBindingAlert({
        type: 'error',
        message: error instanceof Error ? error.message : formatMessage({ id: 'page.profile.bind.fetchFailed', defaultMessage: 'Failed to load binding info, please try again later' }),
      });
    } finally {
      setBindingLoading(false);
    }
  };

  const retryBindChallenge = async () => {
    if (!bindingProvider) {
      return;
    }
    setBindingChallenge(null);
    await openBindModal(bindingProvider);
  };

  const handleUnbind = (provider: SecondFactorProviderStatus) => {
    void (async () => {
      try {
        await secondFactorService.currentUnbind(provider.factorCode, API_OPTS.NO_REDIRECT);
        message.success(formatMessage({ id: 'page.profile.bind.unbound', defaultMessage: 'Unbound' }));
        await providersQuery.refetch();
      } catch (error) {
        showErrorMessage(error, formatMessage({ id: 'page.profile.bind.unbindFailed', defaultMessage: 'Failed to unbind, please try again later' }));
      }
    })();
  };

  const handleVerifyBind = async (values: { verificationCode?: string }) => {
    if (!bindingProvider || !bindingChallenge) {
      setBindingAlert({
        type: 'warning',
        message: formatMessage({ id: 'page.profile.bind.expired', defaultMessage: 'Binding information has expired, please start again.' }),
      });
      return false;
    }
    if (!values.verificationCode) {
      setBindingAlert({
        type: 'warning',
        message: formatMessage({ id: 'page.profile.bind.enterCode', defaultMessage: 'Please enter the verification code.' }),
      });
      return false;
    }

    setBindingSubmitting(true);
    setBindingAlert(undefined);
    try {
      const result = await secondFactorService.currentVerify(
        bindingProvider.factorCode,
        {
          factorCode: bindingProvider.factorCode,
          challengeId: bindingChallenge.challengeId,
          verificationCode: values.verificationCode,
        },
        {
          autoRedirectOnUnauthorized: false,
          silent: true,
        },
      );

      if (!result.verified) {
        setBindingAlert({
          type: 'warning',
          message: result.message || formatMessage({ id: 'page.profile.bind.codeFailed', defaultMessage: 'Verification code validation failed, please try again.' }),
        });
        return false;
      }

      message.success(formatMessage({ id: 'page.profile.bind.completed', defaultMessage: 'Binding completed' }));
      setBindingCompleted(true);
      await providersQuery.refetch();
      return true;
    } catch (error) {
      setBindingAlert({
        type: 'error',
        message: error instanceof Error ? error.message : formatMessage({ id: 'page.profile.bind.failed', defaultMessage: 'Binding failed, please try again later' }),
      });
      return false;
    } finally {
      setBindingSubmitting(false);
    }
  };

  const maskMobile = (mobile?: string | null) => {
    if (!mobile) {
      return '';
    }
    return mobile.length >= 7 ? `${mobile.slice(0, 3)}****${mobile.slice(-4)}` : mobile;
  };

  const maskEmail = (email?: string | null) => {
    if (!email) {
      return '';
    }
    const [localPart, domainPart] = email.split('@');
    if (!domainPart) {
      return email;
    }
    if (localPart.length <= 2) {
      return `**@${domainPart}`;
    }
    return `${localPart.slice(0, 2)}***@${domainPart}`;
  };

  const contactBindVerificationRequired =
    contactBindType === 'mobile' ? mobileBindVerificationRequired : contactBindType === 'email' ? emailBindVerificationRequired : false;
  const contactBindAvailable = contactBindType === 'mobile' ? mobileBindAvailable : contactBindType === 'email' ? emailBindAvailable : false;
  const contactBindSettingsLoading = profileQuery.isLoading || loginCapabilitiesQuery.isLoading || !summary;
  const contactBindChallengeMatchesValue = Boolean(
    contactBindVerificationRequired && contactBindChallenge && contactBindChallengeTarget === (contactBindValue?.trim() || ''),
  );

  const openContactBindModal = (type: 'mobile' | 'email') => {
    if ((type === 'mobile' && !mobileBindAvailable) || (type === 'email' && !emailBindAvailable)) {
      message.warning(type === 'mobile'
        ? formatMessage({ id: 'page.profile.bind.mobileDisabled', defaultMessage: 'SMS verification is not enabled, mobile binding is not allowed.' })
        : formatMessage({ id: 'page.profile.bind.emailDisabled', defaultMessage: 'Email verification is not enabled, email binding is not allowed.' }));
      return;
    }
    setContactBindType(type);
    setContactBindChallenge(null);
    setContactBindChallengeTarget(null);
    setContactBindChallengeLoading(false);
    setContactBindSubmitting(false);
    setContactBindAlert(null);
    contactBindForm.setFieldsValue({
      value: type === 'mobile' ? currentUser?.mobile || '' : currentUser?.email || '',
      verificationCode: undefined,
    });
  };

  const closeContactBindModal = () => {
    if (contactBindSubmitting || contactBindChallengeLoading) {
      return;
    }
    setContactBindType(null);
    setContactBindChallenge(null);
    setContactBindChallengeTarget(null);
    setContactBindAlert(null);
    setContactBindChallengeLoading(false);
    contactBindForm.resetFields();
  };

  const handleContactBindConfirm = async () => {
    if (!contactBindType) {
      return;
    }
    if (!contactBindAvailable) {
      setContactBindAlert(contactBindType === 'mobile'
        ? formatMessage({ id: 'page.profile.bind.mobileDisabled', defaultMessage: 'SMS verification is not enabled, mobile binding is not allowed.' })
        : formatMessage({ id: 'page.profile.bind.emailDisabled', defaultMessage: 'Email verification is not enabled, email binding is not allowed.' }));
      return;
    }

    try {
      const values = await contactBindForm.validateFields();
      const nextValue = values.value?.trim();
      if (!nextValue) {
        return;
      }

      setContactBindAlert(null);

      if (contactBindVerificationRequired && (!contactBindChallenge || contactBindChallengeTarget !== nextValue)) {
        setContactBindChallengeLoading(true);
        try {
          const challenge = await profileService.contactBindChallenge(
            {
              contactType: contactBindType,
              value: nextValue,
            },
            API_OPTS.SILENT_NO_REDIRECT,
          );
          setContactBindChallenge(challenge);
          setContactBindChallengeTarget(nextValue);
          contactBindForm.setFieldsValue({ verificationCode: undefined });
          message.success(formatMessage({ id: 'page.profile.bind.codeSent', defaultMessage: 'Verification code sent, please enter it to continue' }));
          return;
        } catch (error) {
          setContactBindAlert(error instanceof Error ? error.message : formatMessage({ id: 'page.profile.bind.sendFailed', defaultMessage: 'Failed to send verification code, please try again later' }));
          return;
        } finally {
          setContactBindChallengeLoading(false);
        }
      }

      const verificationCode = contactBindVerificationRequired ? contactBindForm.getFieldValue('verificationCode')?.trim() : undefined;
      if (contactBindVerificationRequired) {
        if (!verificationCode) {
          setContactBindAlert(formatMessage({ id: 'page.profile.bind.enterCode', defaultMessage: 'Please enter the verification code.' }));
          return;
        }
        if (!contactBindChallenge?.challengeId) {
          setContactBindAlert(formatMessage({ id: 'page.profile.bind.expired', defaultMessage: 'Binding information has expired, please request a new code.' }));
          return;
        }
      }

      setContactBindSubmitting(true);
      try {
        const updatedUser = await profileService.contactBind(
          {
            contactType: contactBindType,
            value: nextValue,
            challengeId: contactBindChallenge?.challengeId,
            verificationCode,
          },
          API_OPTS.NO_REDIRECT,
        );
        setInitialState((prev) =>
          prev
            ? {
                ...prev,
                currentUser: updatedUser,
              }
            : prev,
        );
        profileForm.setFieldValue(contactBindType, nextValue);
        message.success(contactBindType === 'mobile'
          ? formatMessage({ id: 'page.profile.bind.mobileBound', defaultMessage: 'Mobile number bound' })
          : formatMessage({ id: 'page.profile.bind.emailBound', defaultMessage: 'Email bound' }));
        await profileQuery.refetch();
        setContactBindType(null);
        setContactBindChallenge(null);
        setContactBindChallengeTarget(null);
        setContactBindAlert(null);
        contactBindForm.resetFields();
      } catch (error) {
        setContactBindAlert(error instanceof Error ? error.message : formatMessage({ id: 'page.profile.bind.failed', defaultMessage: 'Binding failed, please try again later' }));
      } finally {
        setContactBindSubmitting(false);
      }
    } catch (error) {
      setContactBindAlert(error instanceof Error ? error.message : formatMessage({ id: 'page.profile.bind.failed', defaultMessage: 'Binding failed, please try again later' }));
    }
  };

  const loginMethodItems = [
    ...(mobileBindingVisible
      ? [
          {
            key: 'mobile',
            title: formatMessage({ id: 'page.profile.loginMethod.mobile', defaultMessage: '手机号登录' }),
            statusLabel: currentUser?.mobile ? formatMessage({ id: 'page.profile.contact.bound', defaultMessage: 'Bound' }) : formatMessage({ id: 'page.profile.contact.unbound', defaultMessage: 'Unbound' }),
            statusColor: currentUser?.mobile ? 'green' : 'default',
            value: currentUser?.mobile ? maskMobile(currentUser.mobile) : formatMessage({ id: 'page.profile.contact.notSetMobile', defaultMessage: 'Mobile number not set' }),
            methodLabel: formatMessage({ id: 'page.profile.loginMethod.smsCode', defaultMessage: '短信登录' }),
            methodColor: 'blue',
            actionLabel: currentUser?.mobile ? formatMessage({ id: 'page.profile.contact.editMobile', defaultMessage: 'Change mobile number' }) : formatMessage({ id: 'page.profile.contact.bindMobile', defaultMessage: 'Bind mobile number' }),
            actionLoading: contactBindType === 'mobile' && (contactBindSubmitting || contactBindChallengeLoading),
            disabled: contactBindSubmitting || contactBindChallengeLoading || contactBindSettingsLoading,
            onAction: () => openContactBindModal('mobile'),
          },
        ]
      : []),
    ...(emailBindingVisible
      ? [
          {
            key: 'email',
            title: formatMessage({ id: 'page.profile.loginMethod.email', defaultMessage: '邮箱登录' }),
            statusLabel: currentUser?.email ? formatMessage({ id: 'page.profile.contact.bound', defaultMessage: 'Bound' }) : formatMessage({ id: 'page.profile.contact.unbound', defaultMessage: 'Unbound' }),
            statusColor: currentUser?.email ? 'green' : 'default',
            value: currentUser?.email ? maskEmail(currentUser.email) : formatMessage({ id: 'page.profile.contact.notSetEmail', defaultMessage: 'Email not set' }),
            methodLabel: formatMessage({ id: 'page.profile.loginMethod.emailCode', defaultMessage: '邮箱登录' }),
            methodColor: 'blue',
            actionLabel: currentUser?.email ? formatMessage({ id: 'page.profile.contact.editEmail', defaultMessage: 'Change email' }) : formatMessage({ id: 'page.profile.contact.bindEmail', defaultMessage: 'Bind email' }),
            actionLoading: contactBindType === 'email' && (contactBindSubmitting || contactBindChallengeLoading),
            disabled: contactBindSubmitting || contactBindChallengeLoading || contactBindSettingsLoading,
            onAction: () => openContactBindModal('email'),
          },
        ]
      : []),
  ];
  const contactBindOpen = contactBindType !== null;
  const contactBindTitle = contactBindType === 'mobile'
    ? formatMessage({ id: 'page.profile.contact.bindMobile', defaultMessage: 'Bind mobile number' })
    : formatMessage({ id: 'page.profile.contact.bindEmail', defaultMessage: 'Bind email' });
  const contactBindDescription =
    contactBindType === 'mobile'
      ? contactBindVerificationRequired
        ? formatMessage({ id: 'page.profile.contact.description.mobile.required', defaultMessage: 'SMS verification is enabled, so you must request and enter a code to bind a mobile number.' })
        : formatMessage({ id: 'page.profile.contact.description.mobile.disabled', defaultMessage: 'SMS verification is not enabled, mobile binding is not allowed.' })
      : contactBindVerificationRequired
        ? formatMessage({ id: 'page.profile.contact.description.email.required', defaultMessage: 'Email verification is enabled, so you must request and enter a code to bind an email.' })
        : formatMessage({ id: 'page.profile.contact.description.email.disabled', defaultMessage: 'Email verification is not enabled, email binding is not allowed.' });
  const contactBindLabel = contactBindType === 'mobile' ? formatMessage({ id: 'page.profile.contact.label.mobile', defaultMessage: 'Mobile number' }) : formatMessage({ id: 'page.profile.contact.label.email', defaultMessage: 'Email' });
  const contactBindPlaceholder = contactBindType === 'mobile' ? formatMessage({ id: 'page.profile.contact.placeholder.mobile', defaultMessage: 'Please enter your mobile number' }) : formatMessage({ id: 'page.profile.contact.placeholder.email', defaultMessage: 'Please enter your email address' });
  const contactBindAutoComplete = contactBindType === 'mobile' ? 'tel' : 'email';
  const contactBindInputMode = contactBindType === 'mobile' ? 'tel' : 'email';
  const contactBindOkText = contactBindVerificationRequired
    ? contactBindChallengeMatchesValue
      ? formatMessage({ id: 'page.profile.contact.confirm', defaultMessage: 'Confirm bind' })
      : formatMessage({ id: 'page.profile.contact.sendCode', defaultMessage: 'Send code' })
    : formatMessage({ id: 'page.profile.contact.save', defaultMessage: 'Save' });
  const displayName = currentUser?.nickname || currentUser?.realName || currentUser?.username || '-';
  const roleNames = currentUser?.availableRoles?.map((role) => role.roleName).filter(Boolean) || [];
  const activeRoleName =
    currentUser?.availableRoles?.find((role) => role.id === currentUser.simulatedRoleId)?.roleName || roleNames[0] || '暂无角色';

  const handleSaveProfile = async () => {
    try {
      const values = await profileForm.validateFields();
      setProfileSaving(true);
      const updatedUser = await profileService.updateBasicInfo(
        {
          ...values,
          mobile: currentUser?.mobile || '',
          email: currentUser?.email || '',
          birthMonth: values.birthMonth ? values.birthMonth.format('YYYY-MM') : '',
        },
        API_OPTS.NO_REDIRECT,
      );
      setInitialState((prev) =>
        prev
          ? {
              ...prev,
              currentUser: updatedUser,
            }
          : prev,
      );
      message.success(formatMessage({ id: 'page.profile.bind.updateSuccess', defaultMessage: 'Profile updated' }));
      await profileQuery.refetch();
      setProfileEditingOpen(false);
    } finally {
      setProfileSaving(false);
    }
  };

  const handleProfileCompletionAction = (item: ProfileCompletionItem) => {
    if (item.actionAvailable === false) {
      return;
    }

    if (item.actionType === 'CONTACT_BIND') {
      if (item.actionTarget === 'mobile') {
        openContactBindModal('mobile');
      } else if (item.actionTarget === 'email') {
        openContactBindModal('email');
      }
      return;
    }

    if (item.actionTarget === 'avatarUrl') {
      setProfileEditingOpen(true);
      profileBasicCardRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      return;
    }

    if (item.actionTarget) {
      setProfileEditingOpen(true);
      profileForm.scrollToField([item.actionTarget]);
      profileBasicCardRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  const handleBindPasskey = async () => {
    if (!isPasskeySupported()) {
      message.warning(formatMessage({ id: 'page.profile.passkey.unsupported', defaultMessage: '当前浏览器不支持通行密钥' }));
      return;
    }
    try {
      const options = await authService.passkeyRegistrationOptions(API_OPTS.SILENT_NO_REDIRECT);
      const credential = await navigator.credentials.create({
        publicKey: toPublicKeyCreationOptions(options),
      });
      if (!credential) {
        return;
      }
      await authService.passkeyRegistrationComplete(toRegistrationPayload(options.challengeId, credential as PublicKeyCredential), {
        autoRedirectOnUnauthorized: false,
      });
      message.success(formatMessage({ id: 'page.profile.passkey.bound', defaultMessage: '通行密钥已绑定' }));
      await passkeyQuery.refetch();
    } catch (error) {
      if (error instanceof DOMException && error.name === 'NotAllowedError') {
        message.info(formatMessage({ id: 'page.profile.passkey.cancelled', defaultMessage: '已取消通行密钥绑定' }));
        return;
      }
      showErrorMessage(error, formatMessage({ id: 'page.profile.passkey.failed', defaultMessage: '通行密钥绑定失败' }));
    }
  };

  const handleRenamePasskey = async (id: number, currentLabel?: string) => {
    const label = window.prompt(formatMessage({ id: 'page.profile.passkey.renamePrompt', defaultMessage: '请输入通行密钥名称' }), currentLabel || '通行密钥');
    if (!label?.trim()) {
      return;
    }
    await authService.renamePasskeyCredential(id, label.trim(), API_OPTS.NO_REDIRECT);
    message.success(formatMessage({ id: 'page.profile.passkey.renamed', defaultMessage: '通行密钥已重命名' }));
    await passkeyQuery.refetch();
  };

  const passkeyCard = (
    <Card
      title={formatMessage({ id: 'page.profile.passkey.title', defaultMessage: '我的通行密钥' })}
      extra={
        <Button icon={<KeyOutlined />} onClick={() => void handleBindPasskey()}>
          {formatMessage({ id: 'page.profile.passkey.add', defaultMessage: '新增' })}
        </Button>
      }
      loading={passkeyQuery.isLoading}
    >
      {passkeyQuery.data?.length ? (
        <List
          dataSource={passkeyQuery.data}
          renderItem={(item) => (
            <List.Item
              actions={[
                <Button key="rename" type="link" onClick={() => void handleRenamePasskey(item.id, item.label)}>
                  {formatMessage({ id: 'common.rename', defaultMessage: '重命名' })}
                </Button>,
                <Popconfirm
                  key="delete"
                  title={formatMessage({ id: 'page.profile.passkey.deleteConfirm', defaultMessage: '确认删除该通行密钥？' })}
                  onConfirm={async () => {
                    await authService.deletePasskeyCredential(item.id, API_OPTS.NO_REDIRECT);
                    message.success(formatMessage({ id: 'common.deleted', defaultMessage: '已删除' }));
                    await passkeyQuery.refetch();
                  }}
                >
                  <Button type="link" danger>
                    {formatMessage({ id: 'common.delete', defaultMessage: '删除' })}
                  </Button>
                </Popconfirm>,
              ]}
            >
              <List.Item.Meta
                avatar={<KeyOutlined />}
                title={item.label || formatMessage({ id: 'page.profile.passkey.defaultLabel', defaultMessage: '通行密钥' })}
                description={`${formatMessage({ id: 'page.profile.passkey.createdAt', defaultMessage: '创建时间' })}: ${item.createdAt || '-'} · ${formatMessage({ id: 'page.profile.passkey.lastUsedAt', defaultMessage: '最后使用' })}: ${item.lastUsedAt || '-'}`}
              />
            </List.Item>
          )}
        />
      ) : (
        <Empty description={formatMessage({ id: 'page.profile.passkey.empty', defaultMessage: '还没有绑定通行密钥' })} />
      )}
    </Card>
  );

  const recentLoginCard = (
    <Card title={formatMessage({ id: 'page.profile.recentLogins', defaultMessage: 'Recent login records' })} loading={profileQuery.isLoading}>
      {recentLoginLogs.length ? (
        <Timeline
          items={recentLoginLogs.map((item: ProfileSummary['recentLoginLogs'][number]) => ({
            children: (
              <Space direction="vertical" size={0}>
                <Typography.Text strong>{item.username || formatMessage({ id: 'page.profile.recentLogins.unknownUser', defaultMessage: 'Unknown user' })}</Typography.Text>
                <Typography.Text type="secondary">
                  {item.logResult || item.failReason || formatMessage({ id: 'page.profile.recentLogins.record', defaultMessage: 'Login record' })} · {item.createdAt}
                </Typography.Text>
              </Space>
            ),
            color: item.logResult === 'SUCCESS' ? 'green' : 'red',
          }))}
        />
      ) : (
        <Empty description={formatMessage({ id: 'page.profile.recentLogins.none', defaultMessage: 'No recent login records' })} />
      )}
    </Card>
  );

  const accountPanel = (
    <div className="saas-profile-page__top-row">
      <Card className="saas-profile-page__summary-card">
        <div className="saas-profile-page__summary-content saas-profile-page__summary-content--account-only">
          <section className="saas-profile-page__account-panel" aria-label="账户身份">
            <Space align="center" size={responsive.isMobile ? 14 : 16} className="saas-profile-page__welcome-profile">
              <Avatar size={responsive.isMobile ? 56 : 64} src={avatarValue || currentUser?.avatarUrl || undefined} icon={<UserOutlined />} className="saas-profile-page__account-avatar" />
              <Space direction="vertical" size={4} className="saas-profile-page__account-copy">
                {responsive.isMobile ? (
                  <Typography.Title level={4} style={{ margin: 0 }}>{displayName}</Typography.Title>
                ) : (
                  <Typography.Title level={3} style={{ margin: 0 }}>{displayName}</Typography.Title>
                )}
                <Typography.Text>{activeRoleName}</Typography.Text>
              </Space>
            </Space>
          </section>
        </div>
      </Card>
    </div>
  );

  const profileBasicPanel = (
    <div ref={profileBasicCardRef}>
      <ProfileBasicCard
        loading={profileQuery.isLoading}
        hasVisibleProfileFields={hasVisibleProfileFields}
        profileSaving={profileSaving}
        profileFormProps={profileFormProps}
        visibleProfileFields={visibleProfileFields}
        currentUser={currentUser}
        avatarValue={avatarValue}
        avatarUploading={avatarUploading}
        editingOpen={profileEditingOpen}
        onSave={() => void handleSaveProfile()}
        onEditOpenChange={setProfileEditingOpen}
        onAvatarBeforeCrop={handleAvatarBeforeCrop}
        onAvatarUploadRequest={handleAvatarUploadRequest}
      />
    </div>
  );

  const accountStatusPanel = (
    <>
      <ProfileCompletionCard compact loading={profileQuery.isLoading} summary={profileCompletionSummary} onActionItem={handleProfileCompletionAction} />
      <LoginMethodCard
        canManage
        loading={profileQuery.isLoading || loginCapabilitiesQuery.isLoading}
        items={loginMethodItems}
      />
      <BoundProviderCard
        canManageSecondFactor
        loading={providersQuery.isLoading}
        providers={providersQuery.data || []}
        bindingLoading={bindingLoading}
        bindingSubmitting={bindingSubmitting}
        onBind={(provider) => void openBindModal(provider)}
        onUnbind={handleUnbind}
      />
      {passkeyCard}
    </>
  );

  return (
    <ManagementPage
      className="saas-profile-page"
      title={formatMessage({ id: 'page.profile.title', defaultMessage: 'Profile center' })}
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        {responsive.isMobile ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            {accountPanel}
            {profileBasicPanel}
            <section className="saas-profile-page__side-section" aria-label="账户状态">
              <Typography.Title level={4} style={{ margin: 0 }}>账户状态</Typography.Title>
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                {accountStatusPanel}
              </Space>
            </section>
            {recentLoginCard}
          </Space>
        ) : (
          <Row gutter={[16, 16]} align="stretch" className="saas-profile-page__three-blocks">
            <Col xs={24} xl={18} className="saas-profile-page__main-column">
              <Space direction="vertical" size={16} style={{ width: '100%' }} className="saas-profile-page__main-stack">
                {accountPanel}
                {profileBasicPanel}
                {recentLoginCard}
              </Space>
            </Col>

            <Col xs={24} xl={6} className="saas-profile-page__rail-column">
              <section className="saas-profile-page__rail-block" aria-label="账户状态">
                {accountStatusPanel}
              </section>
            </Col>
          </Row>
        )}
      </Space>

      <BindSecondFactorModal
        open={bindModalOpen}
        bindingProvider={bindingProvider}
        bindingChallenge={bindingChallenge}
        bindingCompleted={bindingCompleted}
        bindingIsSms={false}
        bindingLoading={bindingLoading}
        bindingSubmitting={bindingSubmitting}
        bindingAlert={bindingAlert}
        singleColumnDescriptionsProps={{ column: 1 }}
        onCancel={closeBindModal}
        onRetry={() => void retryBindChallenge()}
        onFinish={closeBindModal}
        onVerify={handleVerifyBind}
      />
      <ContactBindModal
        open={contactBindOpen}
        title={contactBindTitle}
        description={contactBindDescription}
        label={contactBindLabel}
        placeholder={contactBindPlaceholder}
        autoComplete={contactBindAutoComplete}
        inputMode={contactBindInputMode}
        submitting={contactBindSubmitting || contactBindChallengeLoading}
        alertMessage={contactBindAlert}
        verificationRequired={contactBindVerificationRequired}
        verificationChallenge={contactBindChallenge}
        okText={contactBindOkText}
        initialValue={contactBindType === 'mobile' ? currentUser?.mobile || '' : currentUser?.email || ''}
        formProps={contactBindFormProps}
        onCancel={closeContactBindModal}
        onConfirm={() => void handleContactBindConfirm()}
      />
    </ManagementPage>
  );
};

export default ProfileCenterPage;
