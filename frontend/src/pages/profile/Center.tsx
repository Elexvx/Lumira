import { CameraOutlined, UserOutlined } from '@ant-design/icons';
import { PageContainer, StepsForm } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { Alert, Avatar, Button, Card, Col, DatePicker, Descriptions, Divider, Empty, Form, Input, List, Modal, QRCode, Result, Row, Select, Space, Tag, Timeline, Typography, Upload, message, type UploadProps } from 'antd';
import dayjs from 'dayjs';
import ImgCrop from 'antd-img-crop';
import { useEffect, useMemo, useState } from 'react';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { usePermission } from '@/hooks/usePermission';
import { profileService } from '@/services/profile';
import { secondFactorService } from '@/services/secondFactor';
import { ApiRequestError } from '@/services/common/request';
import { resolveApiErrorFeedback } from '@/services/common/errorFeedback';
import { useResponsive } from '@/hooks/useResponsive';
import type { ProfileSummary, SecondFactorChallenge, SecondFactorProviderStatus } from '@/types/api';
import {
  trimString,
  validateOptionalChinaIdCard,
  validateOptionalChinaMobile,
} from '@/utils/validators';

const GENDER_OPTIONS = [
  { label: '男', value: 'MALE' },
  { label: '女', value: 'FEMALE' },
  { label: '其他', value: 'OTHER' },
];

const ProfileCenterPage = () => {
  const [profileForm] = Form.useForm();
  const { initialState, setInitialState } = useInitialStateModel();
  const { isMobile } = useResponsive();
  const { canAccess } = usePermission();
  const profileQuery = useRequest(async () => ({ data: await profileService.summary({ autoRedirectOnUnauthorized: false }) }) as {
    data: ProfileSummary;
  });
  const canViewSecondFactor = canAccess('plugin:2fa:view');
  const canManageSecondFactor = canAccess('plugin:2fa:manage');
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

  const boundProviderCard = canAccessSecondFactor ? (
    <Card title="已绑定登录方式" loading={secondFactorQuery.loading}>
      {providerList.length ? (
        <List
          dataSource={providerList}
          split={false}
          renderItem={(provider) => (
            <List.Item style={{ paddingInline: 0 }}>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  gap: 16,
                  width: '100%',
                  alignItems: 'flex-start',
                }}
              >
                <Space direction="vertical" size={4} style={{ minWidth: 0 }}>
                  <Space wrap>
                    <Typography.Text strong>{provider.pluginName || provider.pluginCode}</Typography.Text>
                    <Tag color={provider.bound ? 'green' : provider.enabled ? 'gold' : 'default'}>
                      {provider.bound ? '已绑定' : '未绑定'}
                    </Tag>
                    <Tag>{provider.factorName || '登录方式'}</Tag>
                  </Space>
                  <Typography.Text type="secondary">{provider.maskedContact || provider.statusMessage || '暂无绑定标识'}</Typography.Text>
                </Space>
                {canManageSecondFactor ? (
                  <Space wrap style={{ flexShrink: 0, justifyContent: 'flex-end' }}>
                    <Button
                      type="primary"
                      onClick={() => void openBindModal(provider)}
                      disabled={bindingLoading || bindingSubmitting || emailBindingSubmitting}
                    >
                      {provider.bound ? '重新绑定' : '绑定'}
                    </Button>
                    {provider.bound ? (
                      <Button danger onClick={() => handleUnbind(provider.pluginCode, provider.pluginName)}>
                        解绑
                      </Button>
                    ) : null}
                  </Space>
                ) : null}
              </div>
            </List.Item>
          )}
        />
      ) : (
        <Empty description="当前租户暂无可绑定登录方式" />
      )}
    </Card>
  ) : null;

  return (
    <PageContainer
      className="saas-management-page saas-profile-page"
      title="个人中心"
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
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
                <Form form={profileForm} layout="vertical">
                  <Form.Item name="avatarUrl" hidden>
                    <Input />
                  </Form.Item>

                  {visibleProfileFields.has('avatarUrl') ? (
                    <div className="saas-profile-avatar-field">
                      <ImgCrop
                        rotationSlider
                        aspect={1}
                        modalTitle="裁切头像"
                        beforeCrop={handleAvatarBeforeCrop}
                      >
                        <Upload
                          accept="image/*"
                          showUploadList={false}
                          customRequest={handleAvatarUploadRequest}
                          disabled={avatarUploading}
                        >
                          <button
                            type="button"
                            className="saas-profile-avatar-trigger"
                            aria-label="点击修改头像"
                            disabled={avatarUploading}
                          >
                            <Avatar
                              size={96}
                              src={avatarValue || currentUser?.avatarUrl || undefined}
                              icon={<UserOutlined />}
                              className="saas-profile-avatar-trigger__avatar"
                            />
                            <span className="saas-profile-avatar-trigger__overlay">
                              {avatarUploading ? '上传中' : <CameraOutlined />}
                            </span>
                          </button>
                        </Upload>
                      </ImgCrop>
                    </div>
                  ) : null}

                  <Row gutter={[12, 0]}>
                    <Col xs={24} md={12}>
                      <Form.Item label="用户名">
                        <Input value={currentUser?.username || '-'} disabled />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item label="用户ID">
                        <Input value={currentUser?.userId ? String(currentUser.userId) : '-'} disabled />
                      </Form.Item>
                    </Col>
                  </Row>

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
                          <Form.Item
                            name="mobile"
                            label="手机号"
                            rules={[{ validator: validateOptionalChinaMobile }]}
                            normalize={trimString}
                          >
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
                          <Form.Item
                            name="idCardNumber"
                            label="身份证号码"
                            rules={[{ validator: validateOptionalChinaIdCard }]}
                            normalize={trimString}
                          >
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
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
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
              {boundProviderCard}
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
