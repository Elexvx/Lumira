import { PageContainer } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { Card, Col, Empty, Form, Row, Space, Timeline, Typography, Upload, message, type UploadProps } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useMemo, useState } from 'react';
import { useDetailDescriptionsProps } from '@/features/detail/config';
import { useStandardFormProps } from '@/features/form/config';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { profileService } from '@/services/profile';
import { secondFactorService } from '@/services/secondFactor';
import { systemService } from '@/services/system';
import { BindSecondFactorModal } from '@/pages/profile/center/components/BindSecondFactorModal';
import { BoundProviderCard } from '@/pages/profile/center/components/BoundProviderCard';
import { ContactBindModal } from '@/pages/profile/center/components/ContactBindModal';
import { ProfileBasicCard } from '@/pages/profile/center/components/ProfileBasicCard';
import { SecuritySummaryCard } from '@/pages/profile/center/components/SecuritySummaryCard';
import { buildVisibleProfileFields } from '@/pages/profile/center/utils';
import type { ProfileSummary, SecondFactorChallenge, SecondFactorProviderStatus } from '@/types/api';

const ProfileCenterPage = () => {
  const [profileForm] = Form.useForm();
  const { initialState, setInitialState } = useInitialStateModel();
  const profileQuery = useQuery({
    queryKey: ['profile-summary', initialState?.currentUser?.userId],
    queryFn: async () => profileService.summary({ autoRedirectOnUnauthorized: false }),
  });
  const summary = profileQuery.data;
  const currentUser = summary?.currentUser || initialState?.currentUser;
  const currentTenant = summary?.currentTenant || initialState?.currentTenant || null;
  const roleNames = summary?.roleNames || [];
  const recentLoginLogs = summary?.recentLoginLogs || [];
  const profileFieldSettings = summary?.profileFieldSettings || [];
  const summaryMobileBindAvailable = Boolean(summary?.mobileBindAvailable ?? summary?.mobileBindVerificationRequired);
  const summaryEmailBindAvailable = Boolean(summary?.emailBindAvailable ?? summary?.emailBindVerificationRequired);
  const loginCapabilitiesQuery = useQuery({
    queryKey: ['profile-login-capabilities', currentTenant?.tenantId],
    queryFn: async () =>
      systemService.publicLoginCapabilities({
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        silent: true,
      }),
    enabled: Boolean(currentTenant),
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
  const summaryDescriptionsProps = useDetailDescriptionsProps({
    className: 'saas-profile-page__descriptions',
    column: 1,
  });
  const providersQuery = useQuery({
    queryKey: ['profile-second-factor-providers', currentUser?.userId],
    queryFn: async () => secondFactorService.currentProviders({ autoRedirectOnUnauthorized: false }),
    enabled: Boolean(currentUser),
  });
  const [profileSaving, setProfileSaving] = useState(false);
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
      availableTime: currentUser.availableTime || '',
      idCardNumber: currentUser.idCardNumber || '',
    });
  }, [currentUser, profileForm]);

  const handleAvatarBeforeCrop = (file: File) => {
    if (!file.type.startsWith('image/')) {
      message.error('请选择图片文件');
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
      message.success('头像已上传，请点击保存资料');
      onSuccess?.(avatarUrl);
    } catch (error) {
      onError?.(error as Error);
      message.error(error instanceof Error ? error.message : '头像上传失败，请稍后重试');
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
        message: error instanceof Error ? error.message : '获取绑定信息失败，请稍后重试',
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
        await secondFactorService.currentUnbind(provider.factorCode, { autoRedirectOnUnauthorized: false });
        message.success('已解绑');
        await providersQuery.refetch();
      } catch (error) {
        message.error(error instanceof Error ? error.message : '解绑失败，请稍后重试');
      }
    })();
  };

  const handleVerifyBind = async (values: { verificationCode?: string }) => {
    if (!bindingProvider || !bindingChallenge) {
      setBindingAlert({
        type: 'warning',
        message: '绑定信息已失效，请重新发起绑定。',
      });
      return false;
    }
    if (!values.verificationCode) {
      setBindingAlert({
        type: 'warning',
        message: '请输入验证码。',
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
          message: result.message || '验证码校验失败，请重试。',
        });
        return false;
      }

      message.success('绑定已完成');
      setBindingCompleted(true);
      await providersQuery.refetch();
      return true;
    } catch (error) {
      setBindingAlert({
        type: 'error',
        message: error instanceof Error ? error.message : '绑定失败，请稍后重试',
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
      message.warning(type === 'mobile' ? '当前租户未启用短信验证码，暂不允许绑定手机号' : '当前租户未启用邮箱验证码，暂不允许绑定邮箱');
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
      setContactBindAlert(contactBindType === 'mobile' ? '当前租户未启用短信验证码，暂不允许绑定手机号' : '当前租户未启用邮箱验证码，暂不允许绑定邮箱');
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
            { autoRedirectOnUnauthorized: false, silent: true },
          );
          setContactBindChallenge(challenge);
          setContactBindChallengeTarget(nextValue);
          contactBindForm.setFieldsValue({ verificationCode: undefined });
          message.success('验证码已发送，请输入验证码后继续');
          return;
        } catch (error) {
          setContactBindAlert(error instanceof Error ? error.message : '验证码发送失败，请稍后重试');
          return;
        } finally {
          setContactBindChallengeLoading(false);
        }
      }

      const verificationCode = contactBindVerificationRequired ? contactBindForm.getFieldValue('verificationCode')?.trim() : undefined;
      if (contactBindVerificationRequired) {
        if (!verificationCode) {
          setContactBindAlert('请输入验证码');
          return;
        }
        if (!contactBindChallenge?.challengeId) {
          setContactBindAlert('验证码信息已失效，请重新获取验证码');
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
          { autoRedirectOnUnauthorized: false },
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
        message.success(contactBindType === 'mobile' ? '手机号已绑定' : '邮箱已绑定');
        await profileQuery.refetch();
        setContactBindType(null);
        setContactBindChallenge(null);
        setContactBindChallengeTarget(null);
        setContactBindAlert(null);
        contactBindForm.resetFields();
      } catch (error) {
        setContactBindAlert(error instanceof Error ? error.message : '绑定失败，请稍后重试');
      } finally {
        setContactBindSubmitting(false);
      }
    } catch (error) {
      setContactBindAlert(error instanceof Error ? error.message : '绑定失败，请稍后重试');
    }
  };

  const supplementalItems = [
    ...(mobileBindingVisible
      ? [
          {
            key: 'mobile',
            title: '手机号',
            statusLabel: currentUser?.mobile ? '已绑定' : '未绑定',
            statusColor: currentUser?.mobile ? 'green' : 'default',
            value: currentUser?.mobile ? maskMobile(currentUser.mobile) : '未设置手机号',
            verificationLabel: '需验证码',
            verificationColor: 'blue',
            actionLabel: currentUser?.mobile ? '修改手机号' : '绑定手机号',
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
            title: '邮箱',
            statusLabel: currentUser?.email ? '已绑定' : '未绑定',
            statusColor: currentUser?.email ? 'green' : 'default',
            value: currentUser?.email ? maskEmail(currentUser.email) : '未设置邮箱',
            verificationLabel: '需验证码',
            verificationColor: 'blue',
            actionLabel: currentUser?.email ? '修改邮箱' : '绑定邮箱',
            actionLoading: contactBindType === 'email' && (contactBindSubmitting || contactBindChallengeLoading),
            disabled: contactBindSubmitting || contactBindChallengeLoading || contactBindSettingsLoading,
            onAction: () => openContactBindModal('email'),
          },
        ]
      : []),
  ];
  const contactBindOpen = contactBindType !== null;
  const contactBindTitle = contactBindType === 'mobile' ? '绑定手机号' : '绑定邮箱';
  const contactBindDescription =
    contactBindType === 'mobile'
      ? contactBindVerificationRequired
        ? '当前租户已开启短信验证码验证，绑定手机号时需要先获取并输入验证码。'
        : '当前租户未启用短信验证码，暂不允许绑定手机号。'
      : contactBindVerificationRequired
        ? '当前租户已开启邮箱验证码验证，绑定邮箱时需要先获取并输入验证码。'
        : '当前租户未启用邮箱验证码，暂不允许绑定邮箱。';
  const contactBindLabel = contactBindType === 'mobile' ? '手机号' : '邮箱';
  const contactBindPlaceholder = contactBindType === 'mobile' ? '请输入手机号' : '请输入邮箱地址';
  const contactBindAutoComplete = contactBindType === 'mobile' ? 'tel' : 'email';
  const contactBindInputMode = contactBindType === 'mobile' ? 'tel' : 'email';
  const contactBindOkText = contactBindVerificationRequired
    ? contactBindChallengeMatchesValue
      ? '确认绑定'
      : '发送验证码'
    : '保存';

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
        { autoRedirectOnUnauthorized: false },
      );
      setInitialState((prev) =>
        prev
          ? {
              ...prev,
              currentUser: updatedUser,
            }
          : prev,
      );
      message.success('个人资料已更新');
      await profileQuery.refetch();
    } finally {
      setProfileSaving(false);
    }
  };

  return (
    <PageContainer className="saas-management-page saas-profile-page" title="个人中心">
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Row gutter={[16, 16]} align="top">
          <Col xs={24} lg={12}>
            <ProfileBasicCard
              loading={profileQuery.isLoading}
              hasVisibleProfileFields={hasVisibleProfileFields}
              profileSaving={profileSaving}
              profileFormProps={profileFormProps}
              visibleProfileFields={visibleProfileFields}
              currentUser={currentUser}
              avatarValue={avatarValue}
              avatarUploading={avatarUploading}
              mobileLockedByVerification
              emailLockedByVerification
              onSave={() => void handleSaveProfile()}
              onAvatarBeforeCrop={handleAvatarBeforeCrop}
              onAvatarUploadRequest={handleAvatarUploadRequest}
            />
          </Col>

          <Col xs={24} lg={12}>
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <SecuritySummaryCard
                currentTenant={currentTenant}
                permissionCount={summary?.permissionCount ?? currentUser?.permissions?.length ?? 0}
                roleNames={roleNames}
                descriptionsProps={summaryDescriptionsProps}
              />

              <BoundProviderCard
                canManageSecondFactor
                loading={providersQuery.isLoading}
                providers={providersQuery.data || []}
                bindingLoading={bindingLoading}
                bindingSubmitting={bindingSubmitting}
                supplementalItems={supplementalItems}
                onBind={(provider) => void openBindModal(provider)}
                onUnbind={handleUnbind}
              />
            </Space>
          </Col>
        </Row>

        <Card title="最近登录记录" loading={profileQuery.isLoading}>
          {recentLoginLogs.length ? (
            <Timeline
              items={recentLoginLogs.map((item: ProfileSummary['recentLoginLogs'][number]) => ({
                children: (
                  <Space direction="vertical" size={0}>
                    <Typography.Text strong>{item.username || '未知用户'}</Typography.Text>
                    <Typography.Text type="secondary">
                      {item.logResult || item.failReason || '登录记录'} · {item.createdAt}
                    </Typography.Text>
                  </Space>
                ),
                color: item.logResult === 'SUCCESS' ? 'green' : 'red',
              }))}
            />
          ) : (
            <Empty description="暂无最近登录记录" />
          )}
        </Card>
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
    </PageContainer>
  );
};

export default ProfileCenterPage;
