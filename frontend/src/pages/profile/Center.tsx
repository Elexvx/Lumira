import { PageContainer } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { Alert, Button, Card, Col, Empty, Form, Row, Space, Timeline, Typography, Upload, message, Modal, type UploadProps } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useMemo, useState } from 'react';
import { useDetailDescriptionsProps, useDetailFormProps } from '@/features/detail/config';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import { profileService } from '@/services/profile';
import { secondFactorService } from '@/services/secondFactor';
import { ApiRequestError } from '@/services/common/request';
import { resolveApiErrorFeedback } from '@/services/common/errorFeedback';
import { BindSecondFactorModal } from '@/pages/profile/center/components/BindSecondFactorModal';
import { BoundProviderCard } from '@/pages/profile/center/components/BoundProviderCard';
import { EmailBindModal } from '@/pages/profile/center/components/EmailBindModal';
import { ProfileBasicCard } from '@/pages/profile/center/components/ProfileBasicCard';
import { SecuritySummaryCard } from '@/pages/profile/center/components/SecuritySummaryCard';
import type { ProfileSummary, SecondFactorChallenge, SecondFactorProviderStatus } from '@/types/api';

const ProfileCenterPage = () => {
  const [profileForm] = Form.useForm();
  const { initialState, setInitialState } = useInitialStateModel();
  const { isMobile } = useResponsive();
  const actionPermission = useActionPermission();
  const profileQuery = useRequest(async () => ({ data: await profileService.summary({ autoRedirectOnUnauthorized: false }) }) as {
    data: ProfileSummary;
  });
  const canViewSecondFactor = actionPermission.can('plugin:2fa:view');
  const canManageSecondFactor = actionPermission.can('plugin:2fa:manage');
  const canAccessSecondFactor = canViewSecondFactor || canManageSecondFactor;
  const secondFactorQuery = useRequest(
    async () =>
      ({ data: await secondFactorService.providers({ autoRedirectOnUnauthorized: false }) }) as {
        data: SecondFactorProviderStatus[];
      },
    {
      ready: canAccessSecondFactor,
    },
  );
  const [emailBindForm] = Form.useForm();
  const [profileSaving, setProfileSaving] = useState(false);
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [bindModalOpen, setBindModalOpen] = useState(false);
  const [bindingProvider, setBindingProvider] = useState<SecondFactorProviderStatus | null>(null);
  const [bindingChallenge, setBindingChallenge] = useState<SecondFactorChallenge | null>(null);
  const [bindingLoading, setBindingLoading] = useState(false);
  const [bindingSubmitting, setBindingSubmitting] = useState(false);
  const [bindingCompleted, setBindingCompleted] = useState(false);
  const [bindingAlert, setBindingAlert] = useState<{ type: 'info' | 'warning' | 'error'; message: string }>();
  const [emailBindModalOpen, setEmailBindModalOpen] = useState(false);
  const [emailBindingProvider, setEmailBindingProvider] = useState<SecondFactorProviderStatus | null>(null);
  const [emailBindingSubmitting, setEmailBindingSubmitting] = useState(false);
  const [emailBindingAlert, setEmailBindingAlert] = useState<string | null>(null);

  const summary = profileQuery.data;
  const currentUser = summary?.currentUser || initialState?.currentUser;
  const currentTenant = summary?.currentTenant || initialState?.currentTenant || null;
  const roleNames = summary?.roleNames || [];
  const recentLoginLogs = summary?.recentLoginLogs || [];
  const profileFieldSettings = summary?.profileFieldSettings || [];
  const providerList = secondFactorQuery.data || [];
  const hasEmail = Boolean(currentUser?.email);
  const hasMobile = Boolean(currentUser?.mobile);
  const bindingIsSms = bindingProvider?.factorCode === 'sms' || bindingChallenge?.factorCode === 'sms';
  const visibleProfileFields = useMemo(
    () => new Set(profileFieldSettings.filter((item) => item.visible).map((item) => item.fieldKey)),
    [profileFieldSettings],
  );
  const avatarValue = Form.useWatch('avatarUrl', profileForm);
  const hasVisibleProfileFields = visibleProfileFields.size > 0;
  const profileFormProps = useDetailFormProps({ form: profileForm });
  const emailBindFormProps = useDetailFormProps({
    form: emailBindForm,
    initialValues: { email: currentUser?.email || '' },
  });
  const summaryDescriptionsProps = useDetailDescriptionsProps({
    className: 'saas-profile-page__descriptions',
    column: isMobile ? 1 : 2,
  });
  const singleColumnDescriptionsProps = useDetailDescriptionsProps({ column: 1 });

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

  useEffect(() => {
    if (emailBindModalOpen) {
      emailBindForm.setFieldsValue({
        email: currentUser?.email || '',
      });
    }
  }, [currentUser?.email, emailBindForm, emailBindModalOpen]);

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

  const closeEmailBindModal = () => {
    if (emailBindingSubmitting) {
      return;
    }
    setEmailBindModalOpen(false);
    setEmailBindingProvider(null);
    setEmailBindingAlert(null);
    window.setTimeout(() => {
      emailBindForm.resetFields();
    }, 0);
  };

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

  const handleSaveProfile = async () => {
    try {
      const values = await profileForm.validateFields();
      setProfileSaving(true);
      const updatedUser = await profileService.updateBasicInfo(
        {
          ...values,
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
      await profileQuery.refresh();
    } finally {
      setProfileSaving(false);
    }
  };

  const fetchBindChallenge = async (provider: SecondFactorProviderStatus) => {
    setBindingLoading(true);
    setBindingAlert(undefined);
    try {
      const challenge = await secondFactorService.bind(provider.pluginCode, {
        autoRedirectOnUnauthorized: false,
        silent: true,
      });
      setBindingChallenge(challenge);
    } catch (error) {
      const feedback = error instanceof ApiRequestError ? resolveApiErrorFeedback(error, true) : null;
      setBindingAlert({
        type: feedback?.type || 'error',
        message: feedback?.message || (error instanceof Error ? error.message : '获取绑定信息失败，请稍后重试'),
      });
    } finally {
      setBindingLoading(false);
    }
  };

  const openEmailBindModal = (provider: SecondFactorProviderStatus) => {
    setEmailBindingProvider(provider);
    setEmailBindModalOpen(true);
    setEmailBindingAlert(null);
    emailBindForm.setFieldsValue({
      email: currentUser?.email || '',
    });
  };

  const openBindModal = async (provider: SecondFactorProviderStatus, options?: { skipEmailCheck?: boolean }) => {
    if (!options?.skipEmailCheck && provider.emailRequired && !hasEmail) {
      openEmailBindModal(provider);
      return;
    }
    if ((provider.factorCode === 'sms' || provider.pluginCode === 'sms') && !hasMobile) {
      setBindingProvider(provider);
      setBindingChallenge(null);
      setBindingLoading(false);
      setBindingSubmitting(false);
      setBindingCompleted(false);
      setBindingAlert({
        type: 'warning',
        message: '当前账号未绑定手机号，请先补充手机号后再启用短信验证码。',
      });
      setBindModalOpen(true);
      return;
    }

    setBindingProvider(provider);
    setBindingChallenge(null);
    setBindingLoading(true);
    setBindingSubmitting(false);
    setBindingCompleted(false);
    setBindingAlert(undefined);
    setBindModalOpen(true);
    await fetchBindChallenge(provider);
  };

  const handleEmailBind = async () => {
    if (!emailBindingProvider) {
      return;
    }

    try {
      const values = await emailBindForm.validateFields();
      setEmailBindingSubmitting(true);
      setEmailBindingAlert(null);
      const nextProvider = emailBindingProvider;
      const updatedUser = await profileService.updateEmail({ email: values.email }, { autoRedirectOnUnauthorized: false });
      message.success('邮箱已绑定');
      setInitialState((prev) =>
        prev
          ? {
              ...prev,
              currentUser: updatedUser,
            }
          : prev,
      );
      setEmailBindModalOpen(false);
      setEmailBindingProvider(null);
      window.setTimeout(() => {
        emailBindForm.resetFields();
      }, 0);
      try {
        await profileQuery.refresh();
      } catch {
        message.warning('邮箱已保存，但账号信息刷新失败，请稍后手动刷新页面');
      }
      await openBindModal(nextProvider, { skipEmailCheck: true });
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return;
      }
      const feedback = error instanceof ApiRequestError ? resolveApiErrorFeedback(error, true) : null;
      setEmailBindingAlert(feedback?.message || (error instanceof Error ? error.message : '邮箱绑定失败，请稍后重试'));
    } finally {
      setEmailBindingSubmitting(false);
    }
  };

  const retryBindChallenge = async () => {
    if (!bindingProvider) {
      return;
    }
    setBindingChallenge(null);
    await fetchBindChallenge(bindingProvider);
  };

  const handleUnbind = (provider: SecondFactorProviderStatus) => {
    Modal.confirm({
      title: `解绑 ${provider.pluginName || provider.pluginCode}`,
      content: '解绑后该验证方式将立即失效，确认继续吗？',
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        await secondFactorService.unbind(provider.pluginCode, { autoRedirectOnUnauthorized: false });
        message.success('已解绑');
        await secondFactorQuery.refresh();
      },
    });
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
        message: bindingIsSms ? '请输入短信验证码。' : '请输入首个验证码。',
      });
      return false;
    }

    setBindingSubmitting(true);
    setBindingAlert(undefined);
    try {
      const result = await secondFactorService.verify(
        bindingProvider.pluginCode,
        {
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

      message.success('2FA 绑定已完成');
      setBindingCompleted(true);
      await secondFactorQuery.refresh();
      return true;
    } catch (error) {
      const feedback = error instanceof ApiRequestError ? resolveApiErrorFeedback(error, true) : null;
      setBindingAlert({
        type: feedback?.type || 'error',
        message: feedback?.message || (error instanceof Error ? error.message : '绑定失败，请稍后重试'),
      });
      return false;
    } finally {
      setBindingSubmitting(false);
    }
  };

  return (
    <PageContainer className="saas-management-page saas-profile-page" title="个人中心">
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Row gutter={[16, 16]} align="stretch">
          <Col xs={24} lg={12} style={{ display: 'flex' }}>
            <ProfileBasicCard
              loading={profileQuery.loading}
              hasVisibleProfileFields={hasVisibleProfileFields}
              profileSaving={profileSaving}
              profileFormProps={profileFormProps}
              visibleProfileFields={visibleProfileFields}
              currentUser={currentUser}
              avatarValue={avatarValue}
              avatarUploading={avatarUploading}
              onSave={() => void handleSaveProfile()}
              onAvatarBeforeCrop={handleAvatarBeforeCrop}
              onAvatarUploadRequest={handleAvatarUploadRequest}
            />
          </Col>
          <Col xs={24} lg={12} style={{ display: 'flex' }}>
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <SecuritySummaryCard
                currentTenant={currentTenant}
                permissionCount={summary?.permissionCount ?? currentUser?.permissions?.length ?? 0}
                roleNames={roleNames}
                descriptionsProps={summaryDescriptionsProps}
              />
              {canAccessSecondFactor ? (
                <BoundProviderCard
                  canManageSecondFactor={canManageSecondFactor}
                  loading={secondFactorQuery.loading}
                  providers={providerList}
                  bindingLoading={bindingLoading}
                  bindingSubmitting={bindingSubmitting}
                  emailBindingSubmitting={emailBindingSubmitting}
                  onBind={(provider) => void openBindModal(provider)}
                  onUnbind={(provider) => handleUnbind(provider)}
                />
              ) : null}
            </Space>
          </Col>
        </Row>

        <Row gutter={[16, 16]}>
          <Col xs={24}>
            <Card title="最近登录记录" loading={profileQuery.loading}>
              {recentLoginLogs.length ? (
                <Timeline
                  items={recentLoginLogs.map((item) => ({
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
          </Col>
        </Row>
      </Space>

      <EmailBindModal
        open={emailBindModalOpen}
        submitting={emailBindingSubmitting}
        alertMessage={emailBindingAlert}
        formProps={emailBindFormProps}
        onCancel={closeEmailBindModal}
        onConfirm={() => void handleEmailBind()}
      />

      <BindSecondFactorModal
        open={bindModalOpen}
        bindingProvider={bindingProvider}
        bindingChallenge={bindingChallenge}
        bindingCompleted={bindingCompleted}
        bindingIsSms={bindingIsSms}
        bindingLoading={bindingLoading}
        bindingSubmitting={bindingSubmitting}
        bindingAlert={bindingAlert}
        singleColumnDescriptionsProps={singleColumnDescriptionsProps}
        onCancel={closeBindModal}
        onRetry={() => void retryBindChallenge()}
        onFinish={closeBindModal}
        onVerify={handleVerifyBind}
      />
    </PageContainer>
  );
};

export default ProfileCenterPage;
