import { CameraOutlined, UserOutlined } from '@ant-design/icons';
import { PageContainer, StepsForm } from '@ant-design/pro-components';
import { useLocation, useRequest } from '@umijs/max';
import { Alert, Avatar, Button, Card, Col, DatePicker, Descriptions, Divider, Empty, Form, Input, Modal, QRCode, Result, Row, Select, Slider, Space, Tag, Tabs, Timeline, Typography, Upload, message } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useMemo, useRef, useState, type PointerEvent, type SyntheticEvent } from 'react';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { profileService } from '@/services/profile';
import { secondFactorService } from '@/services/secondFactor';
import { ApiRequestError } from '@/services/common/request';
import { resolveApiErrorFeedback } from '@/services/common/errorFeedback';
import { useResponsive } from '@/hooks/useResponsive';
import type { ProfileSummary, SecondFactorChallenge, SecondFactorProviderStatus } from '@/types/api';

const GENDER_OPTIONS = [
  { label: '男', value: 'MALE' },
  { label: '女', value: 'FEMALE' },
  { label: '其他', value: 'OTHER' },
];

const AVATAR_CROP_SIZE = 320;
const AVATAR_CROP_OUTPUT_SIZE = 640;
const AVATAR_MIN_ZOOM = 1;
const AVATAR_MAX_ZOOM = 3;

type AvatarCropLayout = {
  width: number;
  height: number;
};

type AvatarCropPosition = {
  x: number;
  y: number;
};

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

const getAvatarCropLayout = (layout: AvatarCropLayout, zoom: number) => {
  const baseScale = Math.max(AVATAR_CROP_SIZE / layout.width, AVATAR_CROP_SIZE / layout.height);
  const displayScale = baseScale * zoom;
  const displayWidth = layout.width * displayScale;
  const displayHeight = layout.height * displayScale;

  return {
    baseScale,
    displayScale,
    displayWidth,
    displayHeight,
  };
};

const centerAvatarCropPosition = (layout: ReturnType<typeof getAvatarCropLayout>): AvatarCropPosition => ({
  x: (AVATAR_CROP_SIZE - layout.displayWidth) / 2,
  y: (AVATAR_CROP_SIZE - layout.displayHeight) / 2,
});

const clampAvatarCropPosition = (position: AvatarCropPosition, layout: ReturnType<typeof getAvatarCropLayout>): AvatarCropPosition => ({
  x: clamp(position.x, AVATAR_CROP_SIZE - layout.displayWidth, 0),
  y: clamp(position.y, AVATAR_CROP_SIZE - layout.displayHeight, 0),
});

const ProfileCenterPage = () => {
  const [profileForm] = Form.useForm();
  const { initialState, setInitialState } = useInitialStateModel();
  const { isMobile } = useResponsive();
  const location = useLocation();
  const profileQuery = useRequest(async () => ({ data: await profileService.summary({ autoRedirectOnUnauthorized: false }) }) as {
    data: ProfileSummary;
  });
  const secondFactorQuery = useRequest(async () => ({ data: await secondFactorService.providers({ autoRedirectOnUnauthorized: false }) }) as {
    data: SecondFactorProviderStatus[];
  });
  const [emailBindForm] = Form.useForm();
  const [profileSaving, setProfileSaving] = useState(false);
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [avatarCropOpen, setAvatarCropOpen] = useState(false);
  const [avatarCropFile, setAvatarCropFile] = useState<File | null>(null);
  const [avatarCropUrl, setAvatarCropUrl] = useState<string | null>(null);
  const [avatarCropZoom, setAvatarCropZoom] = useState(1);
  const [avatarCropPosition, setAvatarCropPosition] = useState<AvatarCropPosition>({ x: 0, y: 0 });
  const [avatarCropLayout, setAvatarCropLayout] = useState<AvatarCropLayout | null>(null);
  const [avatarCropLoaded, setAvatarCropLoaded] = useState(false);
  const [avatarCropDragging, setAvatarCropDragging] = useState(false);
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
  const avatarCropImageRef = useRef<HTMLImageElement | null>(null);
  const avatarCropDragRef = useRef<{
    pointerId: number;
    startX: number;
    startY: number;
    originX: number;
    originY: number;
  } | null>(null);
  const defaultActiveTab = useMemo(() => {
    const tab = new URLSearchParams(location.search).get('tab');
    return tab === 'second-factor' ? 'second-factor' : 'overview';
  }, [location.search]);
  const [activeTab, setActiveTab] = useState(defaultActiveTab);

  useEffect(() => {
    setActiveTab(defaultActiveTab);
  }, [defaultActiveTab]);

  const summary = profileQuery.data;
  const currentUser = summary?.currentUser || initialState?.currentUser;
  const currentTenant = summary?.currentTenant || initialState?.currentTenant || null;
  const roleNames = summary?.roleNames || [];
  const recentLoginLogs = summary?.recentLoginLogs || [];
  const profileFieldSettings = summary?.profileFieldSettings || [];
  const providerList = secondFactorQuery.data || [];
  const requiresEmail = providerList.some((provider) => provider.emailRequired);
  const hasEmail = Boolean(currentUser?.email);
  const hasMobile = Boolean(currentUser?.mobile);
  const bindingIsSms = bindingProvider?.factorCode === 'sms' || bindingChallenge?.factorCode === 'sms';
  const visibleProfileFields = useMemo(
    () => new Set(profileFieldSettings.filter((item) => item.visible).map((item) => item.fieldKey)),
    [profileFieldSettings],
  );
  const avatarValue = Form.useWatch('avatarUrl', profileForm);
  const hasVisibleProfileFields = visibleProfileFields.size > 0;
  const avatarCropRenderLayout = avatarCropLayout ? getAvatarCropLayout(avatarCropLayout, avatarCropZoom) : null;

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

  useEffect(
    () => () => {
      if (avatarCropUrl) {
        URL.revokeObjectURL(avatarCropUrl);
      }
    },
    [avatarCropUrl],
  );

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

  const resetAvatarCropState = () => {
    avatarCropDragRef.current = null;
    avatarCropImageRef.current = null;
    if (avatarCropUrl) {
      URL.revokeObjectURL(avatarCropUrl);
    }
    setAvatarCropOpen(false);
    setAvatarCropFile(null);
    setAvatarCropUrl(null);
    setAvatarCropLoaded(false);
    setAvatarCropLayout(null);
    setAvatarCropZoom(1);
    setAvatarCropPosition({ x: 0, y: 0 });
  };

  const openAvatarCropper = (file: File) => {
    if (!file.type.startsWith('image/')) {
      message.error('请选择图片文件');
      return false;
    }

    if (avatarCropUrl) {
      URL.revokeObjectURL(avatarCropUrl);
    }

    const nextUrl = URL.createObjectURL(file);
    setAvatarCropFile(file);
    setAvatarCropUrl(nextUrl);
    setAvatarCropOpen(true);
    setAvatarCropLoaded(false);
    setAvatarCropLayout(null);
    setAvatarCropZoom(1);
    setAvatarCropPosition({ x: 0, y: 0 });
    return false;
  };

  const handleAvatarBeforeUpload = (file: File) => openAvatarCropper(file);

  const handleAvatarImageLoad = (event: SyntheticEvent<HTMLImageElement>) => {
    const image = event.currentTarget;
    avatarCropImageRef.current = image;
    const nextLayout = {
      width: image.naturalWidth,
      height: image.naturalHeight,
    };
    const cropLayout = getAvatarCropLayout(nextLayout, avatarCropZoom);
    setAvatarCropLayout(nextLayout);
    setAvatarCropLoaded(true);
    setAvatarCropPosition(centerAvatarCropPosition(cropLayout));
  };

  const updateAvatarCropZoom = (nextZoom: number) => {
    if (!avatarCropLayout) {
      setAvatarCropZoom(nextZoom);
      return;
    }

    const currentLayout = getAvatarCropLayout(avatarCropLayout, avatarCropZoom);
    const nextLayout = getAvatarCropLayout(avatarCropLayout, nextZoom);
    const currentCenter = {
      x: avatarCropPosition.x + currentLayout.displayWidth / 2,
      y: avatarCropPosition.y + currentLayout.displayHeight / 2,
    };

    setAvatarCropZoom(nextZoom);
    setAvatarCropPosition(
      clampAvatarCropPosition(
        {
          x: currentCenter.x - nextLayout.displayWidth / 2,
          y: currentCenter.y - nextLayout.displayHeight / 2,
        },
        nextLayout,
      ),
    );
  };

  const handleAvatarCropReset = () => {
    if (!avatarCropLayout) {
      setAvatarCropZoom(1);
      setAvatarCropPosition({ x: 0, y: 0 });
      return;
    }

    const nextLayout = getAvatarCropLayout(avatarCropLayout, 1);
    setAvatarCropZoom(1);
    setAvatarCropPosition(centerAvatarCropPosition(nextLayout));
  };

  const handleAvatarCropPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    if (!avatarCropLayout || !avatarCropLoaded) {
      return;
    }

    event.preventDefault();
    avatarCropDragRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      originX: avatarCropPosition.x,
      originY: avatarCropPosition.y,
    };
    setAvatarCropDragging(true);
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const handleAvatarCropPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    if (!avatarCropDragRef.current || avatarCropDragRef.current.pointerId !== event.pointerId || !avatarCropLayout) {
      return;
    }

    const nextLayout = getAvatarCropLayout(avatarCropLayout, avatarCropZoom);
    const deltaX = event.clientX - avatarCropDragRef.current.startX;
    const deltaY = event.clientY - avatarCropDragRef.current.startY;
    setAvatarCropPosition(
      clampAvatarCropPosition(
        {
          x: avatarCropDragRef.current.originX + deltaX,
          y: avatarCropDragRef.current.originY + deltaY,
        },
        nextLayout,
      ),
    );
  };

  const handleAvatarCropPointerEnd = (event: PointerEvent<HTMLDivElement>) => {
    if (avatarCropDragRef.current?.pointerId !== event.pointerId) {
      return;
    }

    avatarCropDragRef.current = null;
    setAvatarCropDragging(false);
  };

  const createCroppedAvatarFile = async () => {
    if (!avatarCropFile || !avatarCropImageRef.current || !avatarCropLayout) {
      throw new Error('头像裁切信息无效，请重新选择图片');
    }

    const image = avatarCropImageRef.current;
    const cropLayout = getAvatarCropLayout(avatarCropLayout, avatarCropZoom);
    const sourceWidth = Math.min(image.naturalWidth, (AVATAR_CROP_SIZE / cropLayout.displayWidth) * image.naturalWidth);
    const sourceHeight = Math.min(image.naturalHeight, (AVATAR_CROP_SIZE / cropLayout.displayHeight) * image.naturalHeight);
    const sourceX = clamp(((0 - avatarCropPosition.x) / cropLayout.displayWidth) * image.naturalWidth, 0, image.naturalWidth - sourceWidth);
    const sourceY = clamp(((0 - avatarCropPosition.y) / cropLayout.displayHeight) * image.naturalHeight, 0, image.naturalHeight - sourceHeight);
    const mimeType = avatarCropFile.type.startsWith('image/') ? avatarCropFile.type : 'image/png';
    const canvas = document.createElement('canvas');

    canvas.width = AVATAR_CROP_OUTPUT_SIZE;
    canvas.height = AVATAR_CROP_OUTPUT_SIZE;

    const context = canvas.getContext('2d');
    if (!context) {
      throw new Error('当前浏览器不支持图片裁切');
    }

    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = 'high';
    context.drawImage(
      image,
      sourceX,
      sourceY,
      sourceWidth,
      sourceHeight,
      0,
      0,
      canvas.width,
      canvas.height,
    );

    const blob = await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob(
        (nextBlob) => {
          if (!nextBlob) {
            reject(new Error('头像裁切失败，请重试'));
            return;
          }
          resolve(nextBlob);
        },
        mimeType,
        mimeType === 'image/png' ? undefined : 0.92,
      );
    });

    const extension = mimeType === 'image/jpeg' ? 'jpg' : mimeType === 'image/webp' ? 'webp' : 'png';
    return new File([blob], `avatar.${extension}`, {
      type: mimeType,
      lastModified: Date.now(),
    });
  };

  const uploadAvatarFile = async (file: File) => {
    const avatarUrl = await profileService.uploadAvatar(file, { autoRedirectOnUnauthorized: false });
    profileForm.setFieldValue('avatarUrl', avatarUrl);
    message.success('头像已上传，请点击保存资料');
  };

  const handleAvatarCropConfirm = async () => {
    try {
      setAvatarUploading(true);
      const croppedFile = await createCroppedAvatarFile();
      await uploadAvatarFile(croppedFile);
      resetAvatarCropState();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '头像上传失败，请稍后重试');
    } finally {
      setAvatarUploading(false);
    }
  };

  const handleAvatarCropCancel = () => {
    if (avatarUploading) {
      return;
    }
    resetAvatarCropState();
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
        message:
          feedback?.message ||
          (error instanceof Error ? error.message : '获取绑定信息失败，请稍后重试'),
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

  const handleUnbind = (pluginCode: string, pluginName?: string | null) => {
    Modal.confirm({
      title: `解绑 ${pluginName || pluginCode}`,
      content: '解绑后该验证方式将立即失效，确认继续吗？',
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        await secondFactorService.unbind(pluginCode, { autoRedirectOnUnauthorized: false });
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
        message:
          feedback?.message ||
          (error instanceof Error ? error.message : '绑定失败，请稍后重试'),
      });
      return false;
    } finally {
      setBindingSubmitting(false);
    }
  };

  const handleFinishBindModal = () => {
    closeBindModal();
  };

  const providerCards = providerList.length ? (
    <Row gutter={[16, 16]}>
      {providerList.map((provider) => {
        const statusColor = provider.enabled && provider.bound ? 'green' : provider.enabled ? 'gold' : 'default';
        const actionLabel = provider.bound ? '重新绑定' : '绑定';
        return (
          <Col key={provider.pluginCode} xs={24} lg={12}>
            <Card
              title={
                <Space wrap>
                  <span>{provider.pluginName || provider.pluginCode}</span>
                  <Tag color={statusColor}>{provider.bound ? '已绑定' : '未绑定'}</Tag>
                </Space>
              }
              loading={secondFactorQuery.loading}
              extra={<Tag color={provider.emailRequired ? 'blue' : 'default'}>{provider.factorName || '二次验证'}</Tag>}
            >
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Typography.Paragraph style={{ marginBottom: 0 }}>
                  {provider.statusMessage || '请完成绑定与验证码验证后启用该方式。'}
                </Typography.Paragraph>
                <Descriptions column={1} size="small" bordered>
                  <Descriptions.Item label="验证方式">{provider.factorName || '-'}</Descriptions.Item>
                  <Descriptions.Item label="绑定标识">{provider.maskedContact || '-'}</Descriptions.Item>
                  <Descriptions.Item label="邮箱要求">{provider.emailRequired ? '需要邮箱' : '不需要邮箱'}</Descriptions.Item>
                </Descriptions>
                <Space wrap>
                  <Button
                    type="primary"
                    onClick={() => void openBindModal(provider)}
                    disabled={bindingLoading || bindingSubmitting || emailBindingSubmitting}
                  >
                    {actionLabel}
                  </Button>
                  {provider.bound ? (
                    <Button danger onClick={() => handleUnbind(provider.pluginCode, provider.pluginName)}>
                      解绑
                    </Button>
                  ) : null}
                </Space>
              </Space>
            </Card>
          </Col>
        );
      })}
    </Row>
  ) : (
    <Empty description="当前租户未启用任何二次验证插件" />
  );

  return (
    <PageContainer
      className="saas-management-page saas-profile-page"
      title="个人中心"
    >
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'overview',
            label: '账号概览',
            children: (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Alert
                  showIcon
                  type="info"
                  message="账号信息、租户信息和权限摘要会在这里统一展示。"
                />
                <Row gutter={[16, 16]} align="stretch">
                  <Col xs={24} lg={12} style={{ display: 'flex' }}>
                    <Card
                      title="基础资料"
                      loading={profileQuery.loading}
                      style={{ width: '100%' }}
                      extra={
                        hasVisibleProfileFields ? (
                          <Button type="primary" loading={profileSaving} onClick={() => void handleSaveProfile()}>
                            保存资料
                          </Button>
                        ) : null
                      }
                    >
                      <Space direction="vertical" size={16} style={{ width: '100%' }}>
                        <Space wrap size={[12, 8]}>
                          <Tag>用户名：{currentUser?.username || '-'}</Tag>
                          <Tag>用户ID：{currentUser?.userId || '-'}</Tag>
                        </Space>

                        <Form form={profileForm} layout="vertical">
                          <Form.Item name="avatarUrl" hidden>
                            <Input />
                          </Form.Item>

                          {visibleProfileFields.has('avatarUrl') ? (
                            <Form.Item label="头像">
                              <Upload accept="image/*" showUploadList={false} beforeUpload={handleAvatarBeforeUpload}>
                                <button
                                  type="button"
                                  className="saas-profile-avatar-trigger"
                                  aria-label="点击修改头像"
                                >
                                  <Avatar
                                    size={96}
                                    src={avatarValue || currentUser?.avatarUrl || undefined}
                                    icon={<UserOutlined />}
                                    className="saas-profile-avatar-trigger__avatar"
                                  />
                                  <span className="saas-profile-avatar-trigger__overlay">
                                    <CameraOutlined />
                                  </span>
                                </button>
                              </Upload>
                            </Form.Item>
                          ) : null}

                          {hasVisibleProfileFields ? (
                            <Row gutter={[12, 0]}>
                              <Col xs={24} md={12}>
                                <Form.Item name="nickname" label="昵称">
                                  <Input placeholder="请输入昵称" />
                                </Form.Item>
                              </Col>
                              {visibleProfileFields.has('realName') ? (
                                <Col xs={24} md={12}>
                                  <Form.Item name="realName" label="姓名">
                                    <Input placeholder="请输入姓名" />
                                  </Form.Item>
                                </Col>
                              ) : null}
                              {visibleProfileFields.has('mobile') ? (
                                <Col xs={24} md={12}>
                                  <Form.Item name="mobile" label="手机号">
                                    <Input placeholder="请输入手机号" />
                                  </Form.Item>
                                </Col>
                              ) : null}
                              {visibleProfileFields.has('email') ? (
                                <Col xs={24} md={12}>
                                  <Form.Item name="email" label="邮箱" rules={[{ type: 'email', message: '请输入有效邮箱地址' }]}>
                                    <Input placeholder="请输入邮箱地址" autoComplete="email" />
                                  </Form.Item>
                                </Col>
                              ) : null}
                              {visibleProfileFields.has('birthMonth') ? (
                                <Col xs={24} md={12}>
                                  <Form.Item name="birthMonth" label="出生年月">
                                    <DatePicker picker="month" placeholder="请选择出生年月" format="YYYY年MM月" style={{ width: '100%' }} />
                                  </Form.Item>
                                </Col>
                              ) : null}
                              {visibleProfileFields.has('gender') ? (
                                <Col xs={24} md={12}>
                                  <Form.Item name="gender" label="性别">
                                    <Select allowClear placeholder="请选择性别" options={GENDER_OPTIONS} />
                                  </Form.Item>
                                </Col>
                              ) : null}
                              {visibleProfileFields.has('region') ? (
                                <Col xs={24} md={12}>
                                  <Form.Item name="region" label="所在地区">
                                    <Input placeholder="请输入所在地区" />
                                  </Form.Item>
                                </Col>
                              ) : null}
                              {visibleProfileFields.has('idCardNumber') ? (
                                <Col xs={24} md={12}>
                                  <Form.Item name="idCardNumber" label="身份证号码">
                                    <Input placeholder="请输入身份证号码" />
                                  </Form.Item>
                                </Col>
                              ) : null}
                              {visibleProfileFields.has('availableTime') ? (
                                <Col xs={24}>
                                  <Form.Item name="availableTime" label="可工作时间">
                                    <Input.TextArea rows={2} placeholder="请输入可工作时间，如：周一至周五 09:00-18:00" />
                                  </Form.Item>
                                </Col>
                              ) : null}
                            </Row>
                          ) : (
                            <Empty description="当前租户未开启任何可编辑资料字段" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                          )}
                        </Form>
                      </Space>
                    </Card>
                  </Col>
                  <Col xs={24} lg={12} style={{ display: 'flex' }}>
                    <Card title="租户与安全" style={{ width: '100%' }}>
                      <Descriptions className="saas-profile-page__descriptions" column={isMobile ? 1 : 2} size="small" bordered>
                        <Descriptions.Item label="当前租户">{currentTenant?.tenantName || '未选择'}</Descriptions.Item>
                        <Descriptions.Item label="租户编码">{currentTenant?.tenantCode || '-'}</Descriptions.Item>
                        <Descriptions.Item label="权限数">{summary?.permissionCount ?? currentUser?.permissions?.length ?? 0}</Descriptions.Item>
                        <Descriptions.Item label="角色摘要">
                          <Space wrap>
                            {roleNames.length ? roleNames.map((name) => <Tag key={name}>{name}</Tag>) : <Tag>暂无角色摘要</Tag>}
                          </Space>
                        </Descriptions.Item>
                      </Descriptions>
                    </Card>
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
            ),
          },
          {
            key: 'second-factor',
            label: '2FA验证',
            children: (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Alert
                  showIcon
                  type="info"
                  message="二次验证入口"
                  description="短信验证码与 2FA 验证是两个独立插件。若当前未绑定邮箱，系统会先引导你补充邮箱，再继续绑定验证方式。"
                />
                {requiresEmail && !hasEmail ? (
                  <Alert
                    showIcon
                    type="warning"
                    message="请先补充邮箱"
                    description="当前租户启用了需要邮箱的验证插件。点击绑定时会先要求补充邮箱，然后自动继续。"
                  />
                ) : null}
                {providerCards}
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title="裁切头像"
        open={avatarCropOpen}
        onCancel={handleAvatarCropCancel}
        onOk={() => void handleAvatarCropConfirm()}
        confirmLoading={avatarUploading}
        okText="裁切并上传"
        cancelText="取消"
        destroyOnClose
        maskClosable={false}
        width={720}
        cancelButtonProps={{ disabled: avatarUploading }}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            拖动图片调整取景，缩放后再上传。
          </Typography.Paragraph>
          <div
            className={`saas-profile-avatar-crop__viewport${avatarCropDragging ? ' saas-profile-avatar-crop__viewport--dragging' : ''}`}
            onPointerDown={handleAvatarCropPointerDown}
            onPointerMove={handleAvatarCropPointerMove}
            onPointerUp={handleAvatarCropPointerEnd}
            onPointerCancel={handleAvatarCropPointerEnd}
            onPointerLeave={handleAvatarCropPointerEnd}
          >
            {avatarCropUrl && avatarCropLoaded && avatarCropRenderLayout ? (
              <img
                ref={avatarCropImageRef}
                src={avatarCropUrl}
                alt="头像裁切预览"
                className="saas-profile-avatar-crop__image"
                draggable={false}
                style={{
                  width: `${avatarCropRenderLayout.displayWidth}px`,
                  height: `${avatarCropRenderLayout.displayHeight}px`,
                  transform: `translate(${avatarCropPosition.x}px, ${avatarCropPosition.y}px)`,
                }}
                onLoad={handleAvatarImageLoad}
              />
            ) : avatarCropUrl ? (
              <div className="saas-profile-avatar-crop__loading">
                <Typography.Text type="secondary">图片加载中...</Typography.Text>
              </div>
            ) : (
              <div className="saas-profile-avatar-crop__loading">
                <Typography.Text type="secondary">请先选择一张图片</Typography.Text>
              </div>
            )}
            <div className="saas-profile-avatar-crop__frame" />
          </div>
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            <Space align="center" style={{ justifyContent: 'space-between', width: '100%' }}>
              <Typography.Text type="secondary">缩放</Typography.Text>
              <Button type="link" onClick={handleAvatarCropReset} disabled={!avatarCropLoaded}>
                恢复默认
              </Button>
            </Space>
            <Slider
              min={AVATAR_MIN_ZOOM}
              max={AVATAR_MAX_ZOOM}
              step={0.01}
              value={avatarCropZoom}
              disabled={!avatarCropLoaded}
              onChange={(value) => updateAvatarCropZoom(typeof value === 'number' ? value : value[0])}
            />
          </Space>
        </Space>
      </Modal>

      <Modal
        title="补充邮箱"
        open={emailBindModalOpen}
        onCancel={closeEmailBindModal}
        onOk={() => void handleEmailBind()}
        confirmLoading={emailBindingSubmitting}
        okText="保存并继续"
        cancelText="取消"
        destroyOnClose
        maskClosable={false}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Alert
            showIcon
            type="info"
            message="先绑定邮箱，再继续验证绑定"
            description="当前选择的验证方式需要邮箱。补充邮箱后，系统会自动返回继续绑定 2FA 或短信验证码。"
          />
          {emailBindingAlert ? <Alert showIcon type="error" message={emailBindingAlert} /> : null}
          <Form form={emailBindForm} layout="vertical" initialValues={{ email: currentUser?.email || '' }}>
            <Form.Item
              name="email"
              label="邮箱"
              rules={[
                { required: true, message: '请输入邮箱' },
                { type: 'email', message: '请输入有效邮箱地址' },
              ]}
            >
              <Input placeholder="请输入邮箱地址" autoComplete="email" />
            </Form.Item>
          </Form>
        </Space>
      </Modal>

      <Modal
        title={
          bindingProvider
            ? `${bindingProvider.pluginName || bindingProvider.pluginCode} · ${bindingIsSms ? '短信验证码绑定' : '2FA 绑定'}`
            : '二次验证绑定'
        }
        open={bindModalOpen}
        onCancel={closeBindModal}
        footer={null}
        width={780}
        destroyOnClose
        maskClosable={false}
      >
        {bindingCompleted && bindingChallenge ? (
          bindingIsSms ? (
            <Result
              status="success"
              title="短信验证码绑定已完成"
              subTitle="后续登录或验证时会向该手机号发送短信验证码。"
              extra={[
                <Button key="close" type="primary" onClick={handleFinishBindModal}>
                  完成
                </Button>,
              ]}
              style={{ padding: 0 }}
            />
          ) : (
            <Result
              status="success"
              title="绑定已完成"
              subTitle="请妥善保存以下恢复码，用于设备丢失或验证码不可用时找回账号。"
              extra={[
                <Button key="close" type="primary" onClick={handleFinishBindModal}>
                  完成
                </Button>,
              ]}
              style={{ padding: 0 }}
            >
              <Card size="small" title="恢复码">
                <Space wrap>
                  {(bindingChallenge.recoveryCodes || []).length ? (
                    bindingChallenge.recoveryCodes!.map((code) => (
                      <Tag key={code} color="gold">
                        {code}
                      </Tag>
                    ))
                  ) : (
                    <Typography.Text type="secondary">暂无恢复码</Typography.Text>
                  )}
                </Space>
                <Divider />
                <Typography.Paragraph style={{ marginBottom: 0 }} type="secondary" copyable={{ text: (bindingChallenge.recoveryCodes || []).join('\n') }}>
                  点击复制全部恢复码
                </Typography.Paragraph>
              </Card>
            </Result>
          )
        ) : bindingIsSms ? (
          <StepsForm
            submitter={{
              render: (props) => (
                <Space size={8} wrap>
                  <Button onClick={closeBindModal} disabled={bindingSubmitting}>
                    取消
                  </Button>
                  {props.step > 0 ? (
                    <Button onClick={props.onPre} disabled={bindingLoading || bindingSubmitting}>
                      上一步
                    </Button>
                  ) : null}
                  <Button onClick={() => void retryBindChallenge()} disabled={bindingLoading || bindingSubmitting || !bindingProvider}>
                    重新发送验证码
                  </Button>
                  <Button
                    type="primary"
                    loading={bindingLoading || bindingSubmitting}
                    disabled={bindingLoading || bindingSubmitting || !bindingChallenge}
                    onClick={props.onSubmit}
                  >
                    {props.step === 0 ? '下一步' : '确认绑定'}
                  </Button>
                </Space>
              ),
            }}
            stepsProps={{ responsive: false }}
            formProps={{ layout: 'vertical' }}
            onFinish={handleVerifyBind}
            stepsFormRender={(formDom, submitterDom) => (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                {bindingAlert ? <Alert showIcon type={bindingAlert.type} message={bindingAlert.message} /> : null}
                {formDom}
                {submitterDom}
              </Space>
            )}
          >
            <StepsForm.StepForm name="sms-verify" title="接收验证码">
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Alert
                  showIcon
                  type="info"
                  message="短信验证码已发送"
                  description={
                    bindingChallenge?.maskedContact
                      ? `验证码已发送至 ${bindingChallenge.maskedContact}，请输入收到的 6 位短信验证码完成绑定。`
                      : '验证码已发送至手机号，请输入收到的 6 位短信验证码完成绑定。'
                  }
                />
                {bindingLoading ? (
                  <Card loading />
                ) : bindingChallenge ? (
                  <Descriptions bordered column={1} size="small">
                    <Descriptions.Item label="插件">{bindingChallenge.pluginName || bindingChallenge.pluginCode || '-'}</Descriptions.Item>
                    <Descriptions.Item label="验证方式">{bindingChallenge.factorName || '短信验证码'}</Descriptions.Item>
                    <Descriptions.Item label="绑定标识">{bindingChallenge.maskedContact || '-'}</Descriptions.Item>
                    <Descriptions.Item label="提示信息">{bindingChallenge.promptMessage || '请输入收到的短信验证码'}</Descriptions.Item>
                  </Descriptions>
                ) : (
                  <Empty
                    description={
                      <Space direction="vertical" size={8}>
                        <span>绑定信息尚未加载，请重试</span>
                        <Button type="primary" onClick={() => void retryBindChallenge()} disabled={!bindingProvider}>
                          重新获取绑定信息
                        </Button>
                      </Space>
                    }
                  />
                )}
              </Space>
            </StepsForm.StepForm>
            <StepsForm.StepForm name="sms-input" title="输入短信验证码">
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Alert
                  showIcon
                  type="info"
                  message="输入短信验证码"
                  description="请填写手机收到的 6 位短信验证码，校验成功后即完成绑定。"
                />
                <Form.Item
                  name="verificationCode"
                  rules={[
                    { required: true, message: '请输入短信验证码' },
                    { pattern: /^\d{6}$/, message: '验证码必须为 6 位数字' },
                  ]}
                >
                  <Input
                    size="large"
                    maxLength={6}
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    placeholder="请输入 6 位短信验证码"
                  />
                </Form.Item>
              </Space>
            </StepsForm.StepForm>
          </StepsForm>
        ) : (
          <StepsForm
            submitter={{
              render: (props) => (
                <Space size={8} wrap>
                  <Button onClick={closeBindModal} disabled={bindingSubmitting}>
                    取消
                  </Button>
                  {props.step > 0 ? (
                    <Button onClick={props.onPre} disabled={bindingLoading || bindingSubmitting}>
                      上一步
                    </Button>
                  ) : null}
                  <Button
                    type="primary"
                    loading={bindingLoading || bindingSubmitting}
                    disabled={bindingLoading || bindingSubmitting || !bindingChallenge}
                    onClick={props.onSubmit}
                  >
                    {props.step === 0 ? '下一步' : '确认绑定'}
                  </Button>
                </Space>
              ),
            }}
            stepsProps={{ responsive: false }}
            formProps={{ layout: 'vertical' }}
            onFinish={handleVerifyBind}
            stepsFormRender={(formDom, submitterDom) => (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                {bindingAlert ? <Alert showIcon type={bindingAlert.type} message={bindingAlert.message} /> : null}
                {formDom}
                {submitterDom}
              </Space>
            )}
          >
            <StepsForm.StepForm name="bind-preview" title="扫描二维码">
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Alert
                  showIcon
                  type="info"
                  message="扫码绑定"
                  description={
                    bindingProvider?.bound
                      ? '当前已绑定，重新绑定会生成新的密钥并覆盖旧绑定，请确认后继续。'
                      : '请使用支持 TOTP 的认证器扫描二维码。也可以手动输入密钥完成绑定。'
                  }
                />
                {bindingLoading ? (
                  <Card loading />
                ) : bindingChallenge ? (
                  <Space direction="vertical" size={16} style={{ width: '100%' }}>
                    <div className="saas-profile-2fa-binding__qr">
                      <QRCode value={bindingChallenge.setupUri || bindingChallenge.setupSecret || ''} size={188} bordered />
                    </div>
                    <Descriptions bordered column={1} size="small">
                      <Descriptions.Item label="插件">{bindingChallenge.pluginName || bindingChallenge.pluginCode || '-'}</Descriptions.Item>
                      <Descriptions.Item label="绑定标识">{bindingChallenge.maskedContact || '-'}</Descriptions.Item>
                      <Descriptions.Item label="手动密钥">
                        <Typography.Text copyable={{ text: bindingChallenge.setupSecret || '' }}>
                          {bindingChallenge.setupSecret || '-'}
                        </Typography.Text>
                      </Descriptions.Item>
                      <Descriptions.Item label="绑定地址">
                        <Typography.Paragraph style={{ marginBottom: 0 }} copyable={{ text: bindingChallenge.setupUri || '' }}>
                          {bindingChallenge.setupUri || '-'}
                        </Typography.Paragraph>
                      </Descriptions.Item>
                    </Descriptions>
                    <Typography.Text type="secondary">下一步将要求你输入认证器中的首个 6 位验证码，确认成功后才算绑定完成。</Typography.Text>
                  </Space>
                ) : (
                  <Empty
                    description={
                      <Space direction="vertical" size={8}>
                        <span>绑定信息尚未加载，请重试</span>
                        <Button type="primary" onClick={() => void retryBindChallenge()} disabled={!bindingProvider}>
                          重新获取绑定信息
                        </Button>
                      </Space>
                    }
                  />
                )}
              </Space>
            </StepsForm.StepForm>
            <StepsForm.StepForm name="bind-verify" title="验证首个验证码">
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Alert
                  showIcon
                  type="info"
                  message="验证首个验证码"
                  description="请在认证器中查看当前 6 位验证码并输入，系统会用它确认二维码已经成功绑定。"
                />
                <Form.Item
                  name="verificationCode"
                  rules={[
                    { required: true, message: '请输入首个验证码' },
                    { pattern: /^\d{6}$/, message: '验证码必须为 6 位数字' },
                  ]}
                >
                  <Input
                    size="large"
                    maxLength={6}
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    placeholder="请输入 6 位验证码"
                  />
                </Form.Item>
              </Space>
            </StepsForm.StepForm>
          </StepsForm>
        )}
      </Modal>
    </PageContainer>
  );
};

export default ProfileCenterPage;
