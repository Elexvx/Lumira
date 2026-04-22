import { PageContainer } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { Card, Empty, Form, Space, Timeline, Typography, Upload, message, type UploadProps } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useMemo, useState } from 'react';
import { useDetailDescriptionsProps } from '@/features/detail/config';
import { useStandardFormProps } from '@/features/form/config';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { profileService } from '@/services/profile';
import { secondFactorService } from '@/services/secondFactor';
import { BindSecondFactorModal } from '@/pages/profile/center/components/BindSecondFactorModal';
import { BoundProviderCard } from '@/pages/profile/center/components/BoundProviderCard';
import { ProfileBasicCard } from '@/pages/profile/center/components/ProfileBasicCard';
import { SecuritySummaryCard } from '@/pages/profile/center/components/SecuritySummaryCard';
import { buildVisibleProfileFields } from '@/pages/profile/center/utils';
import type { ProfileSummary, SecondFactorChallenge, SecondFactorProviderStatus } from '@/types/api';

const ProfileCenterPage = () => {
  const [profileForm] = Form.useForm();
  const { initialState, setInitialState } = useInitialStateModel();
  const profileQuery = useRequest(async () => ({ data: await profileService.summary({ autoRedirectOnUnauthorized: false }) }) as {
    data: ProfileSummary;
  });
  const summary = profileQuery.data;
  const currentUser = summary?.currentUser || initialState?.currentUser;
  const currentTenant = summary?.currentTenant || initialState?.currentTenant || null;
  const roleNames = summary?.roleNames || [];
  const recentLoginLogs = summary?.recentLoginLogs || [];
  const profileFieldSettings = summary?.profileFieldSettings || [];
  const visibleProfileFields = useMemo(() => buildVisibleProfileFields(profileFieldSettings), [profileFieldSettings]);
  const avatarValue = Form.useWatch('avatarUrl', profileForm);
  const hasVisibleProfileFields = visibleProfileFields.size > 0;
  const profileFormProps = useStandardFormProps({ form: profileForm });
  const summaryDescriptionsProps = useDetailDescriptionsProps({
    className: 'saas-profile-page__descriptions',
    column: 1,
  });
  const providersQuery = useRequest(
    async () =>
      ({ data: await secondFactorService.currentProviders({ autoRedirectOnUnauthorized: false }) }) as {
        data: SecondFactorProviderStatus[];
      },
    {
      ready: Boolean(currentUser),
    },
  );
  const [profileSaving, setProfileSaving] = useState(false);
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [bindModalOpen, setBindModalOpen] = useState(false);
  const [bindingProvider, setBindingProvider] = useState<SecondFactorProviderStatus | null>(null);
  const [bindingChallenge, setBindingChallenge] = useState<SecondFactorChallenge | null>(null);
  const [bindingLoading, setBindingLoading] = useState(false);
  const [bindingSubmitting, setBindingSubmitting] = useState(false);
  const [bindingCompleted, setBindingCompleted] = useState(false);
  const [bindingAlert, setBindingAlert] = useState<{ type: 'info' | 'warning' | 'error'; message: string }>();

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
        await providersQuery.refresh();
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
      await providersQuery.refresh();
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

  return (
    <PageContainer className="saas-management-page saas-profile-page" title="个人中心">
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
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

        <SecuritySummaryCard
          currentTenant={currentTenant}
          permissionCount={summary?.permissionCount ?? currentUser?.permissions?.length ?? 0}
          roleNames={roleNames}
          descriptionsProps={summaryDescriptionsProps}
        />

        <BoundProviderCard
          canManageSecondFactor
          loading={providersQuery.loading}
          providers={providersQuery.data || []}
          bindingLoading={bindingLoading}
          bindingSubmitting={bindingSubmitting}
          emailBindingSubmitting={false}
          onBind={(provider) => void openBindModal(provider)}
          onUnbind={handleUnbind}
        />

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
    </PageContainer>
  );
};

export default ProfileCenterPage;
