import { formatMessage } from '@umijs/max';
import { getLocale } from '@umijs/max';
import { Alert, Avatar, Button, Card, Col, DatePicker, Descriptions, Divider, Drawer, Empty, Form, Input, List, Modal, Popconfirm, Progress, QRCode, Result, Row, Select, Space, Tag, Timeline, Tooltip, Typography, Upload, theme } from 'antd';
import type { DescriptionsProps, FormProps, UploadProps } from 'antd';
import ImgCrop from 'antd-img-crop';
import { StepsForm } from '@ant-design/pro-components';
import { useEffect } from 'react';
import type { ReactNode, RefObject } from 'react';
import { EditOutlined, ExclamationCircleFilled, KeyOutlined, UserOutlined } from '@ant-design/icons';
import { STANDARD_DRAWER_WIDTH_BY_BREAKPOINT } from '@/constants/ui';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { PROFILE_2FA_BINDING_MODAL_WIDTH_BY_BREAKPOINT } from '@/constants/ui';
import { useProfileCenterPageAccess, type LoginMethodItem } from '@/pages/profile/center/hooks/useProfileCenterPageAccess';
import type { CurrentUser, PasskeyCredentialRecord, ProfileCompletionSummary, ProfileSummary, SecondFactorChallenge, SecondFactorProviderStatus } from '@/types/api';
import { trimString, validateOptionalChinaIdCard } from '@/utils/validators';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const GENDER_OPTIONS = [
  { label: t('男', 'Male'), value: 'MALE' },
  { label: t('女', 'Female'), value: 'FEMALE' },
  { label: t('其他', 'Other'), value: 'OTHER' },
];

const BindSecondFactorSubmitter = ({
  bindingSubmitting,
  bindingLoading,
  hasChallenge,
  onCancel,
  onRetry,
  isMobile,
}: {
  bindingSubmitting: boolean;
  bindingLoading: boolean;
  hasChallenge: boolean;
  onCancel: () => void;
  onRetry: () => void;
  isMobile: boolean;
}) => ({
  render: (props: { step: number; onPre?: () => void; onSubmit?: () => void }) => (
    <Space size={resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile)} wrap>
        <Button onClick={onCancel} disabled={bindingSubmitting}>
          {t('取消', 'Cancel')}
        </Button>
      {props.step > 0 ? (
        <Button onClick={props.onPre} disabled={bindingLoading || bindingSubmitting}>
          {t('上一步', 'Previous step')}
        </Button>
      ) : null}
      <Button onClick={onRetry} disabled={bindingLoading || bindingSubmitting || !hasChallenge}>
        {t('重新发送验证码', 'Resend code')}
      </Button>
      <Button
        type="primary"
        loading={bindingLoading || bindingSubmitting}
        disabled={bindingLoading || bindingSubmitting || !hasChallenge}
        onClick={props.onSubmit}
      >
        {props.step === 0 ? t('下一步', 'Next') : t('确认绑定', 'Confirm binding')}
      </Button>
    </Space>
  ),
});

const BindSecondFactorTotpPreviewStep = ({
  bindingProvider,
  bindingChallenge,
  bindingLoading,
  singleColumnDescriptionsProps,
  onRetry,
  isMobile,
}: {
  bindingProvider: SecondFactorProviderStatus | null;
  bindingChallenge: SecondFactorChallenge | null;
  bindingLoading: boolean;
  singleColumnDescriptionsProps: DescriptionsProps;
  onRetry: () => void;
  isMobile: boolean;
}) => (
  <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, isMobile)} style={{ width: '100%' }}>
    <Alert
      showIcon
      type="info"
      message={t('扫码绑定', 'Scan to bind')}
      description={
        bindingProvider?.bound
          ? t('当前已绑定，重新绑定会生成新的密钥并覆盖旧绑定，请确认后继续。', 'This is already bound. Rebinding will generate a new secret and overwrite the existing binding. Please confirm to continue.')
          : t('请使用支持 TOTP 的认证器扫描二维码。也可以手动输入密钥完成绑定。', 'Use a TOTP authenticator to scan the QR code. You can also enter the secret manually to complete binding.')
      }
    />
    {bindingLoading ? (
      <Card className="saas-profile-page__card" loading />
    ) : bindingChallenge ? (
      <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, isMobile)} style={{ width: '100%' }}>
        <div className="saas-profile-2fa-binding__qr">
          <QRCode value={bindingChallenge.setupUri || bindingChallenge.setupSecret || ''} size={resolveResponsiveValue(APP_SPACING.qrCodeSize, isMobile)} bordered />
        </div>
        <Descriptions {...singleColumnDescriptionsProps}>
          <Descriptions.Item label={t('验证方式', 'Verification method')}>{bindingChallenge.factorName || '2FA'}</Descriptions.Item>
          <Descriptions.Item label={t('标识', 'Identifier')}>{bindingChallenge.factorCode || '-'}</Descriptions.Item>
          <Descriptions.Item label={t('绑定标识', 'Binding identifier')}>{bindingChallenge.maskedContact || '-'}</Descriptions.Item>
          <Descriptions.Item label={t('手动密钥', 'Manual key')}>
            <Typography.Text copyable={{ text: bindingChallenge.setupSecret || '' }}>
              {bindingChallenge.setupSecret || '-'}
            </Typography.Text>
          </Descriptions.Item>
          <Descriptions.Item label={t('绑定地址', 'Binding address')}>
            <Typography.Paragraph style={{ marginBottom: 0 }} copyable={{ text: bindingChallenge.setupUri || '' }}>
              {bindingChallenge.setupUri || '-'}
            </Typography.Paragraph>
          </Descriptions.Item>
        </Descriptions>
        <Typography.Text type="secondary">
          {t('下一步将要求你输入认证器中的首个 6 位验证码，确认成功后才算绑定完成。', 'Next you will enter the first 6-digit code from your authenticator. Binding is complete only after confirmation.')}
        </Typography.Text>
      </Space>
    ) : (
      <Empty
        description={
            <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile)}>
            <span>{t('绑定信息尚未加载，请重试', 'Binding information has not loaded. Please try again.')}</span>
            <Button type="primary" onClick={onRetry} disabled={!bindingProvider}>
              {t('重新获取绑定信息', 'Reload binding information')}
            </Button>
          </Space>
        }
      />
    )}
  </Space>
);

const BindSecondFactorTotpVerifyStep = ({ isMobile }: { isMobile: boolean }) => (
    <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, isMobile)} style={{ width: '100%' }}>
    <Alert
      showIcon
      type="info"
      message={t('验证首个验证码', 'Verify the first code')}
      description={t('请在认证器中查看当前 6 位验证码并输入，系统会用它确认二维码已经成功绑定。', 'Check the current 6-digit code in your authenticator and enter it. The system will use it to confirm the QR code has been bound.')}
    />
    <Form.Item
      name="verificationCode"
      rules={[
        { required: true, message: t('请输入首个验证码', 'Please enter the first code') },
        { pattern: /^\d{6}$/, message: t('验证码必须为 6 位数字', 'The verification code must be 6 digits') },
      ]}
    >
      <Input size="large" maxLength={6} inputMode="numeric" autoComplete="one-time-code" placeholder={t('请输入 6 位验证码', 'Enter the 6-digit code')} />
    </Form.Item>
  </Space>
);

const BindSecondFactorTotpSteps = ({
  bindingProvider,
  bindingChallenge,
  bindingLoading,
  bindingSubmitting,
  bindingAlert,
  singleColumnDescriptionsProps,
  onCancel,
  onRetry,
  onVerify,
  isMobile,
}: {
  bindingProvider: SecondFactorProviderStatus | null;
  bindingChallenge: SecondFactorChallenge | null;
  bindingLoading: boolean;
  bindingSubmitting: boolean;
  bindingAlert?: { type: 'info' | 'warning' | 'error'; message: string };
  singleColumnDescriptionsProps: DescriptionsProps;
  onCancel: () => void;
  onRetry: () => void;
  onVerify: (values: { verificationCode?: string }) => Promise<boolean>;
  isMobile: boolean;
}) => (
  <StepsForm
    submitter={BindSecondFactorSubmitter({
      bindingSubmitting,
      bindingLoading,
      hasChallenge: Boolean(bindingChallenge),
      onCancel,
      onRetry,
      isMobile,
    })}
    stepsProps={{ responsive: false }}
    formProps={{ layout: 'vertical' }}
    onFinish={onVerify}
    stepsFormRender={(formDom, submitterDom) => (
    <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, isMobile)} style={{ width: '100%' }}>
        {bindingAlert ? <Alert showIcon type={bindingAlert.type} message={bindingAlert.message} /> : null}
        {formDom}
        {submitterDom}
      </Space>
    )}
  >
    <StepsForm.StepForm name="bind-preview" title={t('扫描二维码', 'Scan QR code')}>
      <BindSecondFactorTotpPreviewStep
        bindingProvider={bindingProvider}
        bindingChallenge={bindingChallenge}
        bindingLoading={bindingLoading}
        isMobile={isMobile}
        singleColumnDescriptionsProps={singleColumnDescriptionsProps}
        onRetry={onRetry}
      />
    </StepsForm.StepForm>
    <StepsForm.StepForm name="bind-verify" title={t('验证首个验证码', 'Verify first code')}>
      <BindSecondFactorTotpVerifyStep isMobile={isMobile} />
    </StepsForm.StepForm>
  </StepsForm>
);

const ContactBindModal = ({
  open,
  title,
  description,
  label,
  placeholder,
  autoComplete,
  inputMode,
  submitting,
  alertMessage,
  verificationRequired,
  verificationChallenge,
  okText,
  initialValue,
  formProps,
  onCancel,
  onConfirm,
  isMobile,
}: {
  open: boolean;
  title: string;
  description: string;
  label: string;
  placeholder: string;
  autoComplete?: string;
  inputMode?: React.HTMLAttributes<HTMLInputElement>['inputMode'];
  submitting: boolean;
  alertMessage: string | null;
  verificationRequired: boolean;
  verificationChallenge: SecondFactorChallenge | null;
  okText: string;
  initialValue?: string;
  formProps: FormProps;
  onCancel: () => void;
  onConfirm: () => void;
  isMobile: boolean;
}) => {
  useEffect(() => {
    if (!open) {
      return;
    }
    formProps.form?.setFieldsValue({ value: initialValue || '', verificationCode: undefined });
  }, [formProps.form, initialValue, open]);

  return (
    <Modal
      title={title}
      open={open}
      onCancel={onCancel}
      onOk={onConfirm}
      confirmLoading={submitting}
      okText={okText}
      cancelText={t('取消', 'Cancel')}
      destroyOnHidden
      maskClosable={false}
    >
    <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, isMobile)} style={{ width: '100%' }}>
        <Alert showIcon type="info" message={title} description={description} />
        {verificationRequired ? (
          <Alert
            showIcon
            type="info"
            message={t('需要验证码确认', 'Verification code required')}
            description={
              verificationChallenge
                ? verificationChallenge.promptMessage ||
                  (verificationChallenge.maskedContact
                    ? t('验证码已发送至 {contact}，请输入收到的验证码继续。', 'The verification code has been sent to {contact}, please enter the code to continue.').replace('{contact}', verificationChallenge.maskedContact)
                    : t('验证码已发送，请输入收到的验证码继续。', 'The verification code has been sent. Please enter the code to continue.'))
                : t('点击发送验证码后，需要输入收到的验证码才能完成绑定。', 'After sending the verification code, you need to enter it to complete the binding.')
            }
          />
        ) : null}
        {alertMessage ? <Alert showIcon type="error" message={alertMessage} /> : null}
        <Form {...formProps}>
          <Form.Item
            name="value"
            label={label}
            rules={[
              { required: true, message: t(`请输入${label}`, `Please enter ${label}`) },
              ...(label === '邮箱' ? [{ type: 'email' as const, message: t('请输入有效邮箱地址', 'Please enter a valid email address') }] : []),
              ...(label === '手机号' ? [{ pattern: /^1[3-9]\d{9}$/, message: t('请输入有效手机号', 'Please enter a valid mobile number') }] : []),
            ]}
          >
            <Input placeholder={placeholder} autoComplete={autoComplete} inputMode={inputMode} />
          </Form.Item>
          {verificationRequired && verificationChallenge ? (
            <Form.Item
              name="verificationCode"
              label={t('验证码', 'Verification code')}
              rules={[
                { required: true, message: t('请输入验证码', 'Please enter the code') },
                { pattern: /^\d{6}$/, message: t('验证码必须为 6 位数字', 'The verification code must be 6 digits') },
              ]}
            >
              <Input placeholder={t('请输入收到的 6 位验证码', 'Enter the 6-digit code you received')} autoComplete="one-time-code" inputMode="numeric" maxLength={6} />
            </Form.Item>
          ) : null}
        </Form>
      </Space>
    </Modal>
  );
};

const ProfileBasicEditDrawer = ({
  isMobile,
  profileSaving,
  profileFormProps,
  visibleProfileFields,
  currentUser,
  avatarValue,
  avatarUploading,
  editingOpen,
  onSave,
  onEditOpenChange,
  onAvatarBeforeCrop,
  onAvatarUploadRequest,
}: {
  isMobile: boolean;
  profileSaving: boolean;
  profileFormProps: FormProps;
  visibleProfileFields: Set<string>;
  currentUser: CurrentUser | null | undefined;
  avatarValue?: string;
  avatarUploading: boolean;
  editingOpen: boolean;
  onSave: () => void;
  onEditOpenChange: (open: boolean) => void;
  onAvatarBeforeCrop: (file: File) => boolean;
  onAvatarUploadRequest: UploadProps['customRequest'];
}) => (
  <Drawer
    title={t('编辑个人资料', 'Edit profile')}
    open={editingOpen}
    width={resolveResponsiveValue(STANDARD_DRAWER_WIDTH_BY_BREAKPOINT, isMobile)}
    destroyOnClose={false}
    onClose={() => onEditOpenChange(false)}
    footer={
      <div className="saas-drawer-footer">
        <Space>
          <Button onClick={() => onEditOpenChange(false)}>{t('取消', 'Cancel')}</Button>
          <Button type="primary" loading={profileSaving} onClick={onSave}>
            {t('保存资料', 'Save profile')}
          </Button>
        </Space>
      </div>
    }
  >
    <Form {...profileFormProps} layout="vertical">
      <Form.Item name="avatarUrl" hidden>
        <Input />
      </Form.Item>

      {visibleProfileFields.has('avatarUrl') ? (
        <div style={{ display: 'flex', justifyContent: 'center' }}>
          <Space direction="vertical" align="center" size={resolveResponsiveValue(APP_SPACING.sectionGap, isMobile)}>
            <ImgCrop rotationSlider aspect={1} modalTitle={t('裁切头像', 'Crop avatar')} beforeCrop={onAvatarBeforeCrop}>
              <Upload accept="image/*" showUploadList={false} customRequest={onAvatarUploadRequest} disabled={avatarUploading}>
                <Tooltip title={t('点击头像修改', 'Click avatar to edit')} placement="top">
                  <Avatar size={resolveResponsiveValue(APP_SPACING.avatarSize.large, isMobile)} src={avatarValue || currentUser?.avatarUrl || undefined} icon={<UserOutlined />} />
                </Tooltip>
              </Upload>
            </ImgCrop>
          </Space>
        </div>
      ) : null}

      {visibleProfileFields.size ? (
        <Row gutter={[resolveResponsiveValue(APP_SPACING.rowGutterPanel, isMobile)[0], 0]}>
          <Col xs={24}>
            <Form.Item label={t('用户名', 'Username')}>
              <Input value={currentUser?.username || '-'} disabled />
            </Form.Item>
          </Col>
          <Col xs={24}>
            <Form.Item name="nickname" label={t('昵称', 'Nickname')}>
              <Input placeholder={t('请输入昵称', 'Enter a nickname')} />
            </Form.Item>
          </Col>
          {visibleProfileFields.has('realName') ? (
            <Col xs={24}>
              <Form.Item name="realName" label={t('姓名', 'Full name')}>
                <Input placeholder={t('请输入姓名', 'Enter your full name')} />
              </Form.Item>
            </Col>
          ) : null}
          {visibleProfileFields.has('birthMonth') ? (
            <Col xs={24}>
              <Form.Item name="birthMonth" label={t('出生年月', 'Birth month')}>
                <DatePicker picker="month" placeholder={t('请选择出生年月', 'Select your birth month')} format="YYYY年MM月" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          ) : null}
          {visibleProfileFields.has('gender') ? (
            <Col xs={24}>
              <Form.Item name="gender" label={t('性别', 'Gender')}>
                <Select allowClear placeholder={t('请选择性别', 'Select gender')} options={GENDER_OPTIONS} />
              </Form.Item>
            </Col>
          ) : null}
          {visibleProfileFields.has('region') ? (
            <Col xs={24}>
              <Form.Item name="region" label={t('所在地区', 'Region')}>
                <Input placeholder={t('请输入所在地区', 'Enter your region')} />
              </Form.Item>
            </Col>
          ) : null}
          {visibleProfileFields.has('idCardNumber') ? (
            <Col xs={24}>
              <Form.Item name="idCardNumber" label={t('身份证号码', 'ID card number')} rules={[{ validator: validateOptionalChinaIdCard }]} normalize={trimString}>
                <Input placeholder={t('请输入身份证号码', 'Enter your ID card number')} />
              </Form.Item>
            </Col>
          ) : null}
        </Row>
      ) : (
        <Empty description={t('当前未开启任何可编辑资料字段', 'No editable profile fields are enabled')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      )}
    </Form>
  </Drawer>
);

type ProfileCenterOverviewSectionProps = {
  isMobile: boolean;
  profileBasicCardRef: RefObject<HTMLDivElement | null>;
  avatarValue?: string;
  currentUser?: CurrentUser | null;
  displayName: string;
  activeRoleName: string;
  loading: boolean;
  hasVisibleProfileFields: boolean;
  profileSaving: boolean;
  profileFormProps: FormProps;
  visibleProfileFields: Set<string>;
  avatarUploading: boolean;
  editingOpen: boolean;
  onSave: () => void;
  onEditOpenChange: (open: boolean) => void;
  onAvatarBeforeCrop: (file: File) => boolean;
  onAvatarUploadRequest: UploadProps['customRequest'];
  recentLoginLogs: ProfileSummary['recentLoginLogs'];
};

const ProfileCenterOverviewSection = ({
  isMobile,
  profileBasicCardRef,
  avatarValue,
  currentUser,
  displayName,
  activeRoleName,
  loading,
  hasVisibleProfileFields,
  profileSaving,
  profileFormProps,
  visibleProfileFields,
  avatarUploading,
  editingOpen,
  onSave,
  onEditOpenChange,
  onAvatarBeforeCrop,
  onAvatarUploadRequest,
  recentLoginLogs,
}: ProfileCenterOverviewSectionProps) => {
  return (
    <>
      <div className="saas-profile-page__top-row">
        <Card className="saas-profile-page__card saas-profile-page__summary-card">
          <div className="saas-profile-page__summary-content saas-profile-page__summary-content--account-only">
            <section className="saas-profile-page__account-panel" aria-label={t('账户身份', 'Account identity')}>
            <Space align="center" size={resolveResponsiveValue(APP_SPACING.mobileProfileSectionGap, isMobile)} className="saas-profile-page__welcome-profile">
                <Avatar
                  size={isMobile ? 56 : 64}
                  src={avatarValue || currentUser?.avatarUrl || undefined}
                  icon={<UserOutlined />}
                  className="saas-profile-page__account-avatar"
                />
                <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.microGap, isMobile)} className="saas-profile-page__account-copy">
                  {isMobile ? (
                    <Typography.Title level={4} style={{ margin: 0 }}>
                      {displayName}
                    </Typography.Title>
                  ) : (
                    <Typography.Title level={3} style={{ margin: 0 }}>
                      {displayName}
                    </Typography.Title>
                  )}
                  <Typography.Text>{activeRoleName}</Typography.Text>
                  {currentUser?.mobile ? <Typography.Text type="secondary">{currentUser.mobile}</Typography.Text> : null}
                </Space>
              </Space>
            </section>
          </div>
        </Card>
      </div>

      <div ref={profileBasicCardRef}>
        <Card
          title={t('个人信息', 'Personal information')}
          loading={loading}
          className="saas-profile-page__card saas-profile-page__personal-card"
          style={{ width: '100%' }}
          extra={
            <Space size={resolveResponsiveValue(APP_SPACING.microGap, isMobile)}>
              <Tooltip title={t('编辑资料', 'Edit profile')}>
                <Button
                  type="text"
                  shape="circle"
                  aria-label={t('编辑资料', 'Edit profile')}
                  icon={<EditOutlined />}
                  disabled={!hasVisibleProfileFields}
                  onClick={() => onEditOpenChange(true)}
                />
              </Tooltip>
            </Space>
          }
        >
          {hasVisibleProfileFields ? (
            <Descriptions
              className="saas-profile-page__descriptions"
              colon={false}
              column={{ xs: 1, sm: 2, lg: 4 }}
              items={[
                { key: 'username', label: t('用户名', 'Username'), children: currentUser?.username || '-' },
                ...(visibleProfileFields.has('nickname') ? [{ key: 'nickname', label: t('昵称', 'Nickname'), children: currentUser?.nickname || '-' }] : []),
                ...(visibleProfileFields.has('realName') ? [{ key: 'realName', label: t('姓名', 'Full name'), children: currentUser?.realName || '-' }] : []),
                ...(visibleProfileFields.has('birthMonth') ? [{ key: 'birthMonth', label: t('出生年月', 'Birth month'), children: currentUser?.birthMonth || '-' }] : []),
                ...(visibleProfileFields.has('gender') ? [{ key: 'gender', label: t('性别', 'Gender'), children: GENDER_OPTIONS.find((item) => item.value === currentUser?.gender)?.label || '-' }] : []),
                ...(visibleProfileFields.has('region') ? [{ key: 'region', label: t('所在地区', 'Region'), children: currentUser?.region || '-' }] : []),
                ...(visibleProfileFields.has('idCardNumber') ? [{ key: 'idCardNumber', label: t('身份证号码', 'ID card number'), children: currentUser?.idCardNumber || '-' }] : []),
              ]}
            />
          ) : (
        <Empty description={t('当前未开启任何可编辑资料字段', 'No editable profile fields are enabled')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Card>
        <ProfileBasicEditDrawer
          isMobile={isMobile}
          profileSaving={profileSaving}
          profileFormProps={profileFormProps}
          visibleProfileFields={visibleProfileFields}
          currentUser={currentUser}
          avatarValue={avatarValue}
          avatarUploading={avatarUploading}
          editingOpen={editingOpen}
          onSave={onSave}
          onEditOpenChange={onEditOpenChange}
          onAvatarBeforeCrop={onAvatarBeforeCrop}
          onAvatarUploadRequest={onAvatarUploadRequest}
        />
      </div>

      <Card
        title={formatMessage({ id: 'page.profile.recentLogins', defaultMessage: 'Recent login records' })}
        loading={loading}
        className="saas-profile-page__card"
      >
        {recentLoginLogs.length ? (
          <Timeline
            items={recentLoginLogs.map((item) => ({
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
    </>
  );
};

type ProfileCenterCompletionCardProps = {
  loading: boolean;
  isMobile: boolean;
  profileCompletionSummary?: ProfileCompletionSummary | null;
  onProfileCompletionAction: (item: ProfileCompletionSummary['incompleteItems'][number]) => void;
};

const ProfileCenterCompletionCard = ({
  loading,
  isMobile,
  profileCompletionSummary,
  onProfileCompletionAction,
}: ProfileCenterCompletionCardProps) => {
  const { token } = theme.useToken();
  const incompleteItems = profileCompletionSummary?.incompleteItems || [];
  const visibleIncompleteItems = incompleteItems.slice(0, 3);
  const firstActionableItem = incompleteItems.find((item) => item.actionAvailable !== false && Boolean(item.actionType));

  return (
    <Card
      title={t('信息完整度', 'Profile completeness')}
      loading={loading}
      className="saas-profile-page__card saas-profile-page__completion-card saas-profile-page__completion-card--compact"
      style={{ width: '100%' }}
      extra={profileCompletionSummary ? <Tag color={profileCompletionSummary.completionRate === 100 ? 'green' : 'blue'}>{profileCompletionSummary.completionRate}%</Tag> : null}
    >
      {profileCompletionSummary ? (
        <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, isMobile)} style={{ width: '100%' }}>
          <Space align="baseline" size={resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile)} wrap>
            <Typography.Title level={3} style={{ margin: 0 }}>
              {profileCompletionSummary.completionRate}%
            </Typography.Title>
            <Typography.Text type="secondary">
              {incompleteItems.length ? t('{count} 项待完善', '{count} items to complete').replace('{count}', String(incompleteItems.length)) : t('资料已完善', 'Profile complete')}
            </Typography.Text>
            <Typography.Text type="secondary">
              {profileCompletionSummary.score}/{profileCompletionSummary.maxScore} {t('分', 'points')}
            </Typography.Text>
          </Space>
          <Progress
            percent={profileCompletionSummary.completionRate}
            showInfo={false}
            strokeColor={profileCompletionSummary.completionRate === 100 ? token.colorSuccess : token.colorPrimary}
          />
          {visibleIncompleteItems.length ? (
            <Space size={resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile)} wrap>
              {visibleIncompleteItems.map((item) =>
                item.actionAvailable === false ? (
                  <Tag key={item.fieldKey}>{t('待开启', 'Pending')} · {item.fieldLabel}</Tag>
                ) : (
                  <Button key={item.fieldKey} type="link" size="small" icon={<ExclamationCircleFilled />} onClick={() => onProfileCompletionAction(item)}>
                    {item.fieldLabel}
                  </Button>
                ),
              )}
              {incompleteItems.length > visibleIncompleteItems.length ? <Typography.Text type="secondary">{t('还有 {count} 项', '{count} more items').replace('{count}', String(incompleteItems.length - visibleIncompleteItems.length))}</Typography.Text> : null}
            </Space>
          ) : null}

          {firstActionableItem ? (
            <Button type="primary" block onClick={() => onProfileCompletionAction(firstActionableItem)}>
              {t('一键去完善', 'Complete now')}
            </Button>
          ) : null}
        </Space>
      ) : (
        <Empty description={t('当前暂无可评分字段', 'No fields are available for scoring yet')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      )}
    </Card>
  );
};

type ProfileCenterBindingSectionProps = {
  isMobile: boolean;
  loginMethods: LoginMethodItem[];
  passkeys: PasskeyCredentialRecord[];
  loginMethodsLoading: boolean;
  providers: SecondFactorProviderStatus[];
  providersLoading: boolean;
  bindingLoading: boolean;
  bindingSubmitting: boolean;
  onBindPasskey: () => void;
  onRenamePasskey: (id: number, label?: string) => void;
  onDeletePasskey: (id: number) => void;
  onBindProvider: (provider: SecondFactorProviderStatus) => void;
  onUnbindProvider: (provider: SecondFactorProviderStatus) => void;
};

const ProfileCenterBindingSection = ({
  isMobile,
  loginMethods,
  passkeys,
  loginMethodsLoading,
  providers,
  providersLoading,
  bindingLoading,
  bindingSubmitting,
  onBindPasskey,
  onRenamePasskey,
  onDeletePasskey,
  onBindProvider,
  onUnbindProvider,
}: ProfileCenterBindingSectionProps) => {
  const renderLoginMethodList = (items: LoginMethodItem[]) =>
    items.length ? (
      <List
        dataSource={items}
        split={false}
        renderItem={(item) => (
          <List.Item style={{ paddingInline: 0 }}>
            <div
              style={{
                display: 'flex',
                flexDirection: isMobile ? 'column' : 'row',
                justifyContent: 'space-between',
                gap: resolveResponsiveValue(APP_SPACING.sectionGap, isMobile),
                width: '100%',
                alignItems: isMobile ? 'stretch' : 'flex-start',
              }}
            >
              <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.microGap, isMobile)} style={{ minWidth: 0, width: '100%' }}>
                <Space wrap>
                  <Typography.Text strong>{item.title}</Typography.Text>
          <Tag color={item.statusColor || (item.value ? 'green' : 'default')}>{item.statusLabel || (item.value ? t('已绑定', 'Bound') : t('未绑定', 'Unbound'))}</Tag>
                  {item.methodLabel ? <Tag color={item.methodColor}>{item.methodLabel}</Tag> : null}
                </Space>
                <Typography.Text type="secondary">{item.value || t('暂无绑定信息', 'No binding information')}</Typography.Text>
              </Space>
              <Space
                wrap
                style={{
                  flexShrink: 0,
                  justifyContent: isMobile ? 'flex-start' : 'flex-end',
                  width: isMobile ? '100%' : 'auto',
                }}
              >
                <Button type="primary" block={isMobile} onClick={item.onAction} disabled={item.disabled} loading={item.actionLoading}>
                  {item.actionLabel}
                </Button>
              </Space>
            </div>
          </List.Item>
        )}
      />
    ) : (
      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('当前暂无可绑定登录方式', 'No bindable sign-in methods are available yet')} />
    );

  const renderProviderList = (
    items: SecondFactorProviderStatus[],
    currentBindingLoading: boolean,
    currentBindingSubmitting: boolean,
    onBind: (provider: SecondFactorProviderStatus) => void,
    onUnbind: (provider: SecondFactorProviderStatus) => void,
    emptyDescription: ReactNode,
  ) =>
    items.length ? (
      <List
        dataSource={items}
        split={false}
        renderItem={(provider) => (
          <List.Item style={{ paddingInline: 0 }}>
            <div
              style={{
                display: 'flex',
                flexDirection: isMobile ? 'column' : 'row',
                justifyContent: 'space-between',
                gap: resolveResponsiveValue(APP_SPACING.sectionGap, isMobile),
                width: '100%',
                alignItems: isMobile ? 'stretch' : 'flex-start',
              }}
            >
                <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.microGap, isMobile)} style={{ minWidth: 0, width: '100%' }}>
                <Space wrap>
                  <Typography.Text strong>{provider.factorName || provider.factorCode}</Typography.Text>
                  {provider.systemEnabled === false ? <Tag color="red">{t('系统已关闭', 'System disabled')}</Tag> : null}
                  <Tag color={provider.bound ? 'green' : provider.enabled ? 'gold' : 'default'}>{provider.bound ? t('已绑定', 'Bound') : t('未绑定', 'Unbound')}</Tag>
                  <Tag>{provider.factorCode}</Tag>
                </Space>
                <Typography.Text type="secondary">{provider.maskedContact || provider.statusMessage || t('暂无绑定标识', 'No binding identifier')}</Typography.Text>
              </Space>
              <Space
                wrap
                style={{
                  flexShrink: 0,
                  justifyContent: isMobile ? 'flex-start' : 'flex-end',
                  width: isMobile ? '100%' : 'auto',
                }}
              >
                <Button
                  type="primary"
                  block={isMobile}
                  onClick={() => onBind(provider)}
                  disabled={currentBindingLoading || currentBindingSubmitting || provider.systemEnabled === false}
                >
                  {provider.systemEnabled === false ? t('系统未启用', 'System disabled') : provider.bound ? t('重新绑定', 'Rebind') : t('绑定', 'Bind')}
                </Button>
                {provider.bound ? (
                  <Button danger block={isMobile} onClick={() => onUnbind(provider)} disabled={provider.systemEnabled === false}>
                    {t('解绑', 'Unbind')}
                  </Button>
                ) : null}
              </Space>
            </div>
          </List.Item>
        )}
      />
    ) : (
      <Empty description={emptyDescription} />
    );

  const renderPasskeyList = (items: PasskeyCredentialRecord[]) => (
    <>
      <Divider plain style={{ margin: 0 }}>
        {t('通行密钥', 'Passkey')}
      </Divider>
      {items.length ? (
        <List
          dataSource={items}
          split={false}
          renderItem={(item) => (
            <List.Item
              style={{ paddingInline: 0 }}
              actions={[
                <Button key="rename" type="link" onClick={() => onRenamePasskey(item.id, item.label)}>
                  {t('重命名', 'Rename')}
                </Button>,
                <Popconfirm key="delete" title={t('确认删除该通行密钥？', 'Delete this passkey?')} onConfirm={() => onDeletePasskey(item.id)}>
                  <Button type="link" danger>
                    {t('删除', 'Delete')}
                  </Button>
                </Popconfirm>,
              ]}
            >
              <List.Item.Meta
                avatar={<KeyOutlined />}
                title={item.label || t('通行密钥', 'Passkey')}
                description={`${t('创建时间', 'Created at')}: ${item.createdAt || '-'} · ${t('最后使用', 'Last used')}: ${item.lastUsedAt || '-'}`}
              />
            </List.Item>
          )}
        />
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('还没有绑定通行密钥', 'No passkeys are bound yet')} />
      )}
    </>
  );

  const bindingPanel = (
    <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, isMobile)} style={{ width: '100%' }}>
      <Card
        title={t('登录方式绑定', 'Sign-in method binding')}
        loading={loginMethodsLoading}
        className="saas-profile-page__card"
        extra={
          <Button icon={<KeyOutlined />} onClick={onBindPasskey}>
            {t('新增通行密钥', 'Add passkey')}
          </Button>
        }
      >
        {renderLoginMethodList(loginMethods)}
        {renderPasskeyList(passkeys)}
      </Card>
      <Card title={t('二次验证方式', 'Second-factor methods')} loading={providersLoading} className="saas-profile-page__card">
        {renderProviderList(providers, bindingLoading, bindingSubmitting, onBindProvider, onUnbindProvider, '当前暂无可绑定二次验证方式')}
      </Card>
    </Space>
  );

  if (isMobile) {
    return (
      <section className="saas-profile-page__side-section" aria-label={t('账户状态', 'Account status')}>
        {bindingPanel}
      </section>
    );
  }

  return bindingPanel;
};

const ProfileCenterPage = () => {
  const {
    responsive,
    profileForm,
    currentUser,
    profileSectionAccess,
    interactionAccess,
    passkeys,
    loginMethodsLoading,
    providers,
    providersLoading,
  } = useProfileCenterPageAccess();

  const handleProfileCompletionAction = (item: ProfileCompletionSummary['incompleteItems'][number]) => {
    if (item.actionAvailable === false) {
      return;
    }

    if (item.actionType === 'CONTACT_BIND') {
      if (item.actionTarget === 'mobile') {
        interactionAccess.contactBindAccess.openContactBindModal('mobile');
      } else if (item.actionTarget === 'email') {
        interactionAccess.contactBindAccess.openContactBindModal('email');
      }
      return;
    }

    if (item.actionTarget === 'avatarUrl') {
      profileSectionAccess.setProfileEditingOpen(true);
      profileSectionAccess.profileBasicCardRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      return;
    }

    if (item.actionTarget) {
      profileSectionAccess.setProfileEditingOpen(true);
      profileForm.scrollToField([item.actionTarget]);
      profileSectionAccess.profileBasicCardRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  return (
    <ManagementPage
      className="saas-profile-page"
      title={formatMessage({ id: 'page.profile.title', defaultMessage: 'Profile center' })}
    >
      <ManagementPageBody>
        <div className="saas-profile-page__three-blocks">
          <div className="saas-profile-page__main-column">
            <div className="saas-profile-page__main-stack">
              <ProfileCenterOverviewSection
                isMobile={responsive.isMobile}
                profileBasicCardRef={profileSectionAccess.profileBasicCardRef}
                avatarValue={profileSectionAccess.avatarValue}
                currentUser={currentUser}
                displayName={profileSectionAccess.displayName}
                activeRoleName={profileSectionAccess.activeRoleName}
                loading={profileSectionAccess.loading}
                hasVisibleProfileFields={profileSectionAccess.hasVisibleProfileFields}
                profileSaving={profileSectionAccess.profileSaving}
                profileFormProps={profileSectionAccess.profileFormProps}
                visibleProfileFields={profileSectionAccess.visibleProfileFields}
                avatarUploading={profileSectionAccess.avatarUploading}
                editingOpen={profileSectionAccess.editingOpen}
                onSave={profileSectionAccess.onSave}
                onEditOpenChange={profileSectionAccess.onEditOpenChange}
                onAvatarBeforeCrop={profileSectionAccess.onAvatarBeforeCrop}
                onAvatarUploadRequest={profileSectionAccess.onAvatarUploadRequest}
                recentLoginLogs={profileSectionAccess.recentLoginLogs}
              />
            </div>
          </div>
          <div className="saas-profile-page__rail-column">
            <div className="saas-profile-page__rail-block">
              <ProfileCenterCompletionCard
                loading={profileSectionAccess.loading}
                isMobile={responsive.isMobile}
                profileCompletionSummary={profileSectionAccess.profileCompletionSummary}
                onProfileCompletionAction={handleProfileCompletionAction}
              />
              <ProfileCenterBindingSection
                isMobile={responsive.isMobile}
                loginMethods={interactionAccess.passkeyAccess.loginMethods}
                passkeys={passkeys}
                loginMethodsLoading={loginMethodsLoading}
                providers={providers}
                providersLoading={providersLoading}
                bindingLoading={interactionAccess.securityAccess.bindingLoading}
                bindingSubmitting={interactionAccess.securityAccess.bindingSubmitting}
                onBindPasskey={interactionAccess.passkeyAccess.onBindPasskey}
                onRenamePasskey={interactionAccess.passkeyAccess.onRenamePasskey}
                onDeletePasskey={interactionAccess.passkeyAccess.onDeletePasskey}
                onBindProvider={interactionAccess.securityAccess.onBindProvider}
                onUnbindProvider={interactionAccess.securityAccess.onUnbindProvider}
              />
            </div>
          </div>
        </div>
      </ManagementPageBody>
      <Modal
        title={
          interactionAccess.securityAccess.bindingProvider
            ? `${interactionAccess.securityAccess.bindingProvider.factorName || interactionAccess.securityAccess.bindingProvider.factorCode} · 2FA ${t('绑定', 'binding')}`
            : t('二次验证绑定', '2FA binding')
        }
        open={interactionAccess.securityAccess.bindModalOpen}
        onCancel={interactionAccess.securityAccess.closeBindModal}
        footer={null}
        width={resolveResponsiveValue(PROFILE_2FA_BINDING_MODAL_WIDTH_BY_BREAKPOINT, responsive.isMobile)}
        destroyOnHidden
        maskClosable={false}
      >
        {interactionAccess.securityAccess.bindingCompleted && interactionAccess.securityAccess.bindingChallenge ? (
          <Result
            status="success"
            title={t('绑定已完成', 'Binding complete')}
            subTitle={t('请妥善保存以下恢复码，用于设备丢失或验证码不可用时找回账号。', 'Save the recovery codes below in a safe place so you can recover your account if the device is lost or codes are unavailable.')}
            extra={[
              <Button key="close" type="primary" onClick={interactionAccess.securityAccess.closeBindModal}>
                {t('完成', 'Done')}
              </Button>,
            ]}
            style={{ padding: 0 }}
          >
            <Card className="saas-profile-page__card" size="small" title={t('恢复码', 'Recovery codes')}>
              <Space wrap>
                {(interactionAccess.securityAccess.bindingChallenge.recoveryCodes || []).length ? (
                  interactionAccess.securityAccess.bindingChallenge.recoveryCodes!.map((code) => (
                    <Tag key={code} color="gold">
                      {code}
                    </Tag>
                  ))
                ) : (
                  <Typography.Text type="secondary">{t('暂无恢复码', 'No recovery codes')}</Typography.Text>
                )}
              </Space>
              <Divider />
              <Typography.Paragraph
                style={{ marginBottom: 0 }}
                type="secondary"
                copyable={{ text: (interactionAccess.securityAccess.bindingChallenge.recoveryCodes || []).join('\n') }}
              >
                {t('点击复制全部恢复码', 'Click to copy all recovery codes')}
              </Typography.Paragraph>
            </Card>
          </Result>
        ) : (
          <BindSecondFactorTotpSteps
            bindingProvider={interactionAccess.securityAccess.bindingProvider}
            bindingChallenge={interactionAccess.securityAccess.bindingChallenge}
            bindingLoading={interactionAccess.securityAccess.bindingLoading}
            bindingSubmitting={interactionAccess.securityAccess.bindingSubmitting}
            bindingAlert={interactionAccess.securityAccess.bindingAlert}
            isMobile={responsive.isMobile}
            singleColumnDescriptionsProps={{ column: 1 }}
            onCancel={interactionAccess.securityAccess.closeBindModal}
            onRetry={() => void interactionAccess.securityAccess.retryBindChallenge()}
            onVerify={interactionAccess.securityAccess.handleVerifyBind}
          />
        )}
      </Modal>
      <ContactBindModal
        isMobile={responsive.isMobile}
        open={interactionAccess.contactBindAccess.contactBindOpen}
        title={interactionAccess.contactBindAccess.contactBindTitle}
        description={interactionAccess.contactBindAccess.contactBindDescription}
        label={interactionAccess.contactBindAccess.contactBindLabel}
        placeholder={interactionAccess.contactBindAccess.contactBindPlaceholder}
        autoComplete={interactionAccess.contactBindAccess.contactBindAutoComplete}
        inputMode={interactionAccess.contactBindAccess.contactBindInputMode}
        submitting={
          interactionAccess.contactBindAccess.contactBindSubmitting || interactionAccess.contactBindAccess.contactBindChallengeLoading
        }
        alertMessage={interactionAccess.contactBindAccess.contactBindAlert}
        verificationRequired={interactionAccess.contactBindAccess.contactBindVerificationRequired}
        verificationChallenge={interactionAccess.contactBindAccess.contactBindChallenge}
        okText={interactionAccess.contactBindAccess.contactBindOkText}
        initialValue={
          interactionAccess.contactBindAccess.contactBindType === 'mobile'
            ? currentUser?.mobile || ''
            : currentUser?.email || ''
        }
        formProps={interactionAccess.contactBindAccess.contactBindFormProps}
        onCancel={interactionAccess.contactBindAccess.closeContactBindModal}
        onConfirm={() => void interactionAccess.contactBindAccess.handleContactBindConfirm()}
      />
    </ManagementPage>
  );
};

export default ProfileCenterPage;
