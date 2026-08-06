import { formatMessage } from '@/i18n/formatMessage';

import { Alert, Button, Card, Col, DatePicker, Descriptions, Divider, Empty, Form, Input, List, Modal, Progress, QRCode, Result, Row, Select, Space, Tag, Timeline, Tooltip, Typography, Upload, theme } from 'antd';
import type { DescriptionsProps, FormProps, UploadProps } from 'antd';
import ImgCrop from 'antd-img-crop';
import { StepsForm } from '@ant-design/pro-components';
import { useEffect } from 'react';
import type { ReactNode, RefObject } from 'react';
import { EditOutlined, KeyOutlined } from '@ant-design/icons';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { useConfirmableDrawerClose } from '@/features/management/drawerCloseConfirm';
import { PROFILE_2FA_BINDING_MODAL_WIDTH_BY_BREAKPOINT } from '@/constants/ui';
import { useProfileCenterPageAccess, type LoginMethodItem } from '@/pages/profile/center/hooks/useProfileCenterPageAccess';
import type { CurrentUser, PasskeyCredentialRecord, ProfileCompletionSummary, ProfileFieldSetting, ProfileSummary, SecondFactorBindingChallenge, SecondFactorChallenge, SecondFactorProviderStatus } from '@/types/api';
import { trimString, validateOptionalChinaIdCard } from '@/utils/validators';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { databaseMessage } from '@/i18n/databaseMessage';
import { UserAvatar } from '@/components/UserAvatar';
import { StandardDrawer } from '@/features/management/StandardDrawer';

const t = databaseMessage;

const GENDER_OPTIONS = [
  { label: t('ui.profile.center.male'), value: 'MALE' },
  { label: t('ui.profile.center.female'), value: 'FEMALE' },
  { label: t('ui.profile.center.other'), value: 'OTHER' },
];

const renderCustomProfileInput = (field: ProfileFieldSetting) => {
  const placeholder = field.placeholder || t('ui.profile.center.enter', { fieldLabel: field.fieldLabel });
  if (field.fieldType === 'TEXTAREA') {
    return <Input.TextArea rows={4} maxLength={1000} placeholder={placeholder} />;
  }
  if (field.fieldType === 'NUMBER') {
    return <Input type="number" placeholder={placeholder} />;
  }
  return <Input maxLength={1000} placeholder={placeholder} />;
};

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
          {t('ui.profile.center.cancel')}
        </Button>
      {props.step > 0 ? (
        <Button onClick={props.onPre} disabled={bindingLoading || bindingSubmitting}>
          {t('ui.profile.center.previousStep')}
        </Button>
      ) : null}
      <Button onClick={onRetry} disabled={bindingLoading || bindingSubmitting || !hasChallenge}>
        {t('ui.profile.center.resendCode')}
      </Button>
      <Button
        type="primary"
        loading={bindingLoading || bindingSubmitting}
        disabled={bindingLoading || bindingSubmitting || !hasChallenge}
        onClick={props.onSubmit}
      >
        {props.step === 0 ? t('ui.profile.center.next') : t('ui.profile.center.confirmBinding')}
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
  bindingChallenge: SecondFactorBindingChallenge | null;
  bindingLoading: boolean;
  singleColumnDescriptionsProps: DescriptionsProps;
  onRetry: () => void;
  isMobile: boolean;
}) => (
  <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, isMobile)} style={{ width: '100%' }}>
    <Alert
      showIcon
      type="info"
      message={t('ui.profile.center.scanToBind')}
      description={
        t('ui.profile.center.useATotpAuthenticatorToScanTheQr')
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
          <Descriptions.Item label={t('ui.profile.center.verificationMethod')}>{bindingChallenge.factorName || '2FA'}</Descriptions.Item>
          <Descriptions.Item label={t('ui.profile.center.identifier')}>{bindingChallenge.factorCode || '-'}</Descriptions.Item>
          <Descriptions.Item label={t('ui.profile.center.bindingIdentifier')}>{bindingChallenge.maskedContact || '-'}</Descriptions.Item>
          <Descriptions.Item label={t('ui.profile.center.manualKey')}>
            <Typography.Text copyable={{ text: bindingChallenge.setupSecret || '' }}>
              {bindingChallenge.setupSecret || '-'}
            </Typography.Text>
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.profile.center.bindingAddress')}>
            <Typography.Paragraph style={{ marginBottom: 0 }} copyable={{ text: bindingChallenge.setupUri || '' }}>
              {bindingChallenge.setupUri || '-'}
            </Typography.Paragraph>
          </Descriptions.Item>
        </Descriptions>
        <Typography.Text type="secondary">
          {t('ui.profile.center.nextYouWillEnterTheFirst6Digit')}
        </Typography.Text>
      </Space>
    ) : (
      <Empty
        description={
            <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile)}>
            <span>{t('ui.profile.center.bindingInformationHasNotLoadedPleaseTryAgain')}</span>
            <Button type="primary" onClick={onRetry} disabled={!bindingProvider}>
              {t('ui.profile.center.reloadBindingInformation')}
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
      message={t('ui.profile.center.verifyTheFirstCode')}
      description={t('ui.profile.center.checkTheCurrent6DigitCodeInYour')}
    />
    <Form.Item
      name="verificationCode"
      rules={[
        { required: true, message: t('ui.profile.center.pleaseEnterTheFirstCode') },
        { pattern: /^\d{6}$/, message: t('ui.profile.center.theVerificationCodeMustBe6Digits') },
      ]}
    >
      <Input size="large" maxLength={6} inputMode="numeric" autoComplete="one-time-code" placeholder={t('ui.profile.center.enterThe6DigitCode')} />
    </Form.Item>
  </Space>
);

const BindSecondFactorVerificationGate = ({
  provider,
  verificationProvider,
  verificationChallenge,
  verificationLoading,
  bindingLoading,
  bindingSubmitting,
  bindingAlert,
  formProps,
  onRetry,
  onSubmit,
  onCancel,
  isMobile,
}: {
  provider: SecondFactorProviderStatus | null;
  verificationProvider: SecondFactorProviderStatus | null;
  verificationChallenge: SecondFactorChallenge | null;
  verificationLoading: boolean;
  bindingLoading: boolean;
  bindingSubmitting: boolean;
  bindingAlert?: { type: 'info' | 'warning' | 'error'; message: string };
  formProps: FormProps<{ currentPassword?: string; verificationCode?: string }>;
  onRetry: () => void;
  onSubmit: (values: { currentPassword?: string; verificationCode?: string }) => Promise<boolean>;
  onCancel: () => void;
  isMobile: boolean;
}) => (
  <Form {...formProps} layout="vertical" onFinish={onSubmit}>
    <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, isMobile)} style={{ width: '100%' }}>
      {bindingAlert ? <Alert showIcon type={bindingAlert.type} message={bindingAlert.message} /> : null}
      <Alert
        showIcon
        type="warning"
        message={t('ui.profile.center.verifyYourIdentityFirst')}
        description={
          verificationProvider
            ? t('ui.profile.center.enterTheCurrentVerificationCodeOrRecoveryCode')
            : t('ui.profile.center.thisAccountHasNoActiveSecondFactorMethod')
        }
      />
      {verificationProvider ? (
        <>
          <Descriptions column={1} size="small">
            <Descriptions.Item label={t('ui.profile.center.verificationMethod')}>
              {verificationChallenge?.factorName || verificationProvider.factorName || verificationProvider.factorCode}
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.profile.center.bindingIdentifier')}>
              {verificationChallenge?.maskedContact || verificationProvider.maskedContact || '-'}
            </Descriptions.Item>
          </Descriptions>
          <Typography.Text type="secondary">
            {verificationChallenge?.promptMessage || t('ui.profile.center.enterTheCurrentVerificationCodeOrARecovery')}
          </Typography.Text>
          {!verificationChallenge && !verificationLoading ? (
            <Button onClick={onRetry}>{t('ui.profile.center.reloadVerificationDetails')}</Button>
          ) : null}
          <Form.Item
            name="verificationCode"
            rules={[{ required: true, message: t('ui.profile.center.pleaseEnterTheCurrentVerificationCodeOrA') }]}
          >
            <Input
              autoComplete="one-time-code"
              placeholder={t('ui.profile.center.enterTheCurrentVerificationCodeOrARecovery.8ee4f6d3')}
              disabled={verificationLoading || bindingLoading}
            />
          </Form.Item>
        </>
      ) : (
        <Form.Item
          name="currentPassword"
          rules={[{ required: true, message: t('ui.profile.center.pleaseEnterYourCurrentPassword') }]}
        >
          <Input.Password
            autoComplete="current-password"
            placeholder={t('ui.profile.center.enterYourCurrentPassword')}
            disabled={bindingLoading}
          />
        </Form.Item>
      )}
      <Space size={resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile)} wrap>
        <Button onClick={onCancel} disabled={bindingSubmitting || verificationLoading || bindingLoading}>
          {t('ui.profile.center.cancel')}
        </Button>
        <Button
          type="primary"
          htmlType="submit"
          loading={bindingSubmitting || verificationLoading || bindingLoading}
          disabled={!provider}
        >
          {t('ui.profile.center.continue')}
        </Button>
      </Space>
    </Space>
  </Form>
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
  bindingChallenge: SecondFactorBindingChallenge | null;
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
    <StepsForm.StepForm name="bind-preview" title={t('ui.profile.center.scanQrCode')}>
      <BindSecondFactorTotpPreviewStep
        bindingProvider={bindingProvider}
        bindingChallenge={bindingChallenge}
        bindingLoading={bindingLoading}
        isMobile={isMobile}
        singleColumnDescriptionsProps={singleColumnDescriptionsProps}
        onRetry={onRetry}
      />
    </StepsForm.StepForm>
    <StepsForm.StepForm name="bind-verify" title={t('ui.profile.center.verifyFirstCode')}>
      <BindSecondFactorTotpVerifyStep isMobile={isMobile} />
    </StepsForm.StepForm>
  </StepsForm>
);

const UnbindSecondFactorModal = ({
  open,
  provider,
  challenge,
  challengeLoading,
  submitting,
  alertMessage,
  formProps,
  onCancel,
  onRetry,
  onSubmit,
  isMobile,
}: {
  open: boolean;
  provider: SecondFactorProviderStatus | null;
  challenge: SecondFactorChallenge | null;
  challengeLoading: boolean;
  submitting: boolean;
  alertMessage?: string | null;
  formProps: FormProps<{ verificationCode?: string }>;
  onCancel: () => void;
  onRetry: () => void;
  onSubmit: (values: { verificationCode?: string }) => Promise<boolean>;
  isMobile: boolean;
}) => (
  <Modal
    title={provider ? `${provider.factorName || provider.factorCode} · ${t('ui.profile.center.unbindConfirmation')}` : t('ui.profile.center.unbindConfirmation')}
    open={open}
    onCancel={onCancel}
    onOk={() => formProps.form?.submit?.()}
    okText={t('ui.profile.center.confirmUnbind')}
    cancelText={t('ui.profile.center.cancel')}
    confirmLoading={challengeLoading || submitting}
    okButtonProps={{ disabled: challengeLoading || !challenge }}
    cancelButtonProps={{ disabled: challengeLoading || submitting }}
    destroyOnHidden
    maskClosable={!(challengeLoading || submitting)}
    width={resolveResponsiveValue(PROFILE_2FA_BINDING_MODAL_WIDTH_BY_BREAKPOINT, isMobile)}
  >
    <Form {...formProps} layout="vertical" onFinish={onSubmit}>
      <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, isMobile)} style={{ width: '100%' }}>
        {alertMessage ? <Alert showIcon type="error" message={alertMessage} /> : null}
        <Alert
          showIcon
          type="warning"
          message={t('ui.profile.center.verifyBeforeUnbinding')}
          description={t('ui.profile.center.enterTheCurrentAuthenticatorCodeOrUseA')}
        />
        {challengeLoading ? (
          <Card className="saas-profile-page__card" loading />
        ) : challenge ? (
          <>
            <Descriptions column={1} size="small">
              <Descriptions.Item label={t('ui.profile.center.verificationMethod')}>{challenge.factorName || provider?.factorName || provider?.factorCode || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('ui.profile.center.bindingIdentifier')}>{challenge.maskedContact || provider?.maskedContact || '-'}</Descriptions.Item>
            </Descriptions>
            <Typography.Text type="secondary">
              {challenge.promptMessage || t('ui.profile.center.enterTheVerificationCodeToConfirmYourIdentity')}
            </Typography.Text>
          </>
        ) : (
          <Empty
            description={
              <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile)}>
                <span>{t('ui.profile.center.verificationDetailsAreNotLoadedYetPleaseTry')}</span>
                <Button type="primary" onClick={onRetry} disabled={!provider}>
                  {t('ui.profile.center.reloadVerificationDetails')}
                </Button>
              </Space>
            }
          />
        )}
        <Form.Item
          name="verificationCode"
          rules={[{ required: true, message: t('ui.profile.center.pleaseEnterTheVerificationCodeOrARecovery') }]}
        >
          <Input
            size="large"
            autoComplete="one-time-code"
            placeholder={t('ui.profile.center.enterTheVerificationCodeOrARecoveryCode')}
            disabled={challengeLoading || !challenge}
          />
        </Form.Item>
      </Space>
    </Form>
  </Modal>
);

const PasskeyVerificationModal = ({
  open,
  action,
  verificationProvider,
  verificationChallenge,
  challengeLoading,
  submitting,
  alertMessage,
  formProps,
  onCancel,
  onRetry,
  onSubmit,
  isMobile,
}: {
  open: boolean;
  action: 'bind' | 'rename' | 'delete' | null;
  verificationProvider: SecondFactorProviderStatus | null;
  verificationChallenge: SecondFactorChallenge | null;
  challengeLoading: boolean;
  submitting: boolean;
  alertMessage: string | null;
  formProps: FormProps<{ currentPassword?: string; verificationCode?: string }>;
  onCancel: () => void;
  onRetry: () => void;
  onSubmit: (values: { currentPassword?: string; verificationCode?: string }) => Promise<boolean>;
  isMobile: boolean;
}) => (
  <Modal
    title={
      action === 'delete'
        ? t('ui.profile.center.deletePasskey')
        : action === 'rename'
          ? t('ui.profile.center.renamePasskey')
          : t('ui.profile.center.addPasskey')
    }
    open={open}
    onCancel={onCancel}
    onOk={() => formProps.form?.submit?.()}
    okText={
      action === 'delete'
        ? t('ui.profile.center.confirmDelete')
        : action === 'rename'
          ? t('ui.profile.center.confirmRename')
          : t('ui.profile.center.continue')
    }
    cancelText={t('ui.profile.center.cancel')}
    confirmLoading={challengeLoading || submitting}
    cancelButtonProps={{ disabled: challengeLoading || submitting }}
    destroyOnHidden
    maskClosable={!(challengeLoading || submitting)}
    width={resolveResponsiveValue(PROFILE_2FA_BINDING_MODAL_WIDTH_BY_BREAKPOINT, isMobile)}
  >
    <Form {...formProps} layout="vertical" onFinish={onSubmit}>
      <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, isMobile)} style={{ width: '100%' }}>
        {alertMessage ? <Alert showIcon type="error" message={alertMessage} /> : null}
        <Alert
          showIcon
          type="warning"
          message={t('ui.profile.center.verifyYourIdentityFirst')}
          description={
            verificationProvider
              ? t('ui.profile.center.enterTheCurrentVerificationCodeOrRecoveryCode.88a7b7e5')
              : t('ui.profile.center.thisAccountHasNoActiveSecondFactorMethod.b765695a')
          }
        />
        {verificationProvider ? (
          <>
            <Descriptions column={1} size="small">
              <Descriptions.Item label={t('ui.profile.center.verificationMethod')}>
                {verificationChallenge?.factorName || verificationProvider.factorName || verificationProvider.factorCode}
              </Descriptions.Item>
              <Descriptions.Item label={t('ui.profile.center.bindingIdentifier')}>
                {verificationChallenge?.maskedContact || verificationProvider.maskedContact || '-'}
              </Descriptions.Item>
            </Descriptions>
            <Typography.Text type="secondary">
              {verificationChallenge?.promptMessage || t('ui.profile.center.enterTheCurrentVerificationCodeOrARecovery')}
            </Typography.Text>
            {!verificationChallenge && !challengeLoading ? (
              <Button onClick={onRetry}>{t('ui.profile.center.reloadVerificationDetails')}</Button>
            ) : null}
            <Form.Item
              name="verificationCode"
              rules={[{ required: true, message: t('ui.profile.center.pleaseEnterTheCurrentVerificationCodeOrA') }]}
            >
              <Input
                autoComplete="one-time-code"
                placeholder={t('ui.profile.center.enterTheCurrentVerificationCodeOrARecovery.8ee4f6d3')}
                disabled={challengeLoading}
              />
            </Form.Item>
          </>
        ) : (
          <Form.Item
            name="currentPassword"
            rules={[{ required: true, message: t('ui.profile.center.pleaseEnterYourCurrentPassword') }]}
          >
            <Input.Password
              autoComplete="current-password"
              placeholder={t('ui.profile.center.enterYourCurrentPassword')}
            />
          </Form.Item>
        )}
      </Space>
    </Form>
  </Modal>
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
  currentVerificationProvider,
  currentVerificationChallenge,
  currentVerificationLoading,
  verificationRequired,
  verificationChallenge,
  okText,
  initialValue,
  formProps,
  onCancel,
  onConfirm,
  onRetryCurrentVerification,
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
  currentVerificationProvider: SecondFactorProviderStatus | null;
  currentVerificationChallenge: SecondFactorChallenge | null;
  currentVerificationLoading: boolean;
  verificationRequired: boolean;
  verificationChallenge: SecondFactorChallenge | null;
  okText: string;
  initialValue?: string;
  formProps: FormProps;
  onCancel: () => void;
  onConfirm: () => void;
  onRetryCurrentVerification: () => void;
  isMobile: boolean;
}) => {
  useEffect(() => {
    if (!open) {
      return;
    }
    formProps.form?.setFieldsValue({ value: initialValue || '', currentPassword: undefined, currentVerificationCode: undefined, verificationCode: undefined });
  }, [formProps.form, initialValue, open]);

  return (
    <Modal
      title={title}
      open={open}
      onCancel={onCancel}
      onOk={onConfirm}
      confirmLoading={submitting}
      okText={okText}
      cancelText={t('ui.profile.center.cancel')}
      destroyOnHidden
      maskClosable={false}
    >
    <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, isMobile)} style={{ width: '100%' }}>
        <Alert showIcon type="info" message={title} description={description} />
        <Alert
          showIcon
          type="warning"
          message={t('ui.profile.center.verifyYourCurrentIdentityFirst')}
          description={
            currentVerificationProvider
              ? currentVerificationChallenge
                ? currentVerificationChallenge.promptMessage ||
                  t('ui.profile.center.enterTheCodeFromYourCurrentVerificationMethod')
                : t('ui.profile.center.currentVerificationDetailsAreNotLoadedYetPlease')
              : t('ui.profile.center.thisAccountHasNoActiveBoundVerificationMethod')
          }
        />
        {verificationRequired ? (
          <Alert
            showIcon
            type="info"
            message={t('ui.profile.center.verificationCodeRequired')}
            description={
              verificationChallenge
                ? verificationChallenge.promptMessage ||
                  (verificationChallenge.maskedContact
                    ? t('ui.profile.center.theVerificationCodeHasBeenSentToPlease').replace('{contact}', verificationChallenge.maskedContact)
                    : t('ui.profile.center.theVerificationCodeHasBeenSentPleaseEnter'))
                : t('ui.profile.center.afterSendingTheVerificationCodeYouNeedTo')
            }
          />
        ) : null}
        {alertMessage ? <Alert showIcon type="error" message={alertMessage} /> : null}
        <Form {...formProps}>
          <Form.Item
            name="value"
            label={label}
            rules={[
              { required: true, message: t('ui.profile.center.pleaseEnter', { label: label }) },
              ...(label === '邮箱' ? [{ type: 'email' as const, message: t('ui.profile.center.pleaseEnterAValidEmailAddress') }] : []),
              ...(label === '手机号' ? [{ pattern: /^1[3-9]\d{9}$/, message: t('ui.profile.center.pleaseEnterAValidMobileNumber') }] : []),
            ]}
          >
            <Input placeholder={placeholder} autoComplete={autoComplete} inputMode={inputMode} />
          </Form.Item>
          {currentVerificationProvider ? (
            <>
              <Form.Item
                label={t('ui.profile.center.currentVerificationMethod')}
                style={{ marginBottom: 0 }}
              >
                <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile)} style={{ width: '100%' }}>
                  <Typography.Text>
                    {currentVerificationChallenge?.factorName || currentVerificationProvider.factorName || currentVerificationProvider.factorCode}
                    {currentVerificationChallenge?.maskedContact || currentVerificationProvider.maskedContact
                      ? ` · ${currentVerificationChallenge?.maskedContact || currentVerificationProvider.maskedContact}`
                      : ''}
                  </Typography.Text>
                  {!currentVerificationChallenge && !currentVerificationLoading ? (
                    <Button onClick={onRetryCurrentVerification}>
                      {t('ui.profile.center.reloadCurrentVerificationDetails')}
                    </Button>
                  ) : null}
                </Space>
              </Form.Item>
              <Form.Item
                name="currentVerificationCode"
                label={t('ui.profile.center.currentVerificationCode')}
                rules={[{ required: true, message: t('ui.profile.center.pleaseEnterTheCurrentVerificationCodeOrA') }]}
              >
                <Input
                  placeholder={t('ui.profile.center.enterTheCurrentVerificationCodeOrARecovery.8ee4f6d3')}
                  autoComplete="one-time-code"
                  disabled={currentVerificationLoading}
                />
              </Form.Item>
            </>
          ) : (
            <Form.Item
              name="currentPassword"
              label={t('ui.profile.center.currentPassword')}
              rules={[{ required: true, message: t('ui.profile.center.pleaseEnterYourCurrentPassword') }]}
            >
              <Input.Password
                placeholder={t('ui.profile.center.enterYourCurrentPassword')}
                autoComplete="current-password"
              />
            </Form.Item>
          )}
          {verificationRequired && verificationChallenge ? (
            <Form.Item
              name="verificationCode"
              label={t('ui.profile.center.verificationCode')}
              rules={[
                { required: true, message: t('ui.profile.center.pleaseEnterTheCode') },
                { pattern: /^\d{6}$/, message: t('ui.profile.center.theVerificationCodeMustBe6Digits') },
              ]}
            >
              <Input placeholder={t('ui.profile.center.enterThe6DigitCodeYouReceived')} autoComplete="one-time-code" inputMode="numeric" maxLength={6} />
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
  visibleCustomProfileFields,
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
  visibleCustomProfileFields: ProfileFieldSetting[];
  currentUser: CurrentUser | null | undefined;
  avatarValue?: string;
  avatarUploading: boolean;
  editingOpen: boolean;
  onSave: () => void;
  onEditOpenChange: (open: boolean) => void;
  onAvatarBeforeCrop: (file: File) => boolean;
  onAvatarUploadRequest: UploadProps['customRequest'];
}) => {
  const handleDrawerClose = useConfirmableDrawerClose(() => onEditOpenChange(false));
  return (
    <StandardDrawer
      title={t('ui.profile.center.editProfile')}
      open={editingOpen}
      destroyOnClose={false}
      onClose={handleDrawerClose}
      footer={
        <div className="saas-drawer-footer">
          <Space>
            <Button onClick={() => onEditOpenChange(false)}>{t('ui.profile.center.cancel')}</Button>
            <Button type="primary" loading={profileSaving} onClick={onSave}>
              {t('ui.profile.center.saveProfile')}
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
            <ImgCrop rotationSlider aspect={1} modalTitle={t('ui.profile.center.cropAvatar')} beforeCrop={onAvatarBeforeCrop}>
              <Upload accept="image/*" showUploadList={false} customRequest={onAvatarUploadRequest} disabled={avatarUploading}>
                <Tooltip title={t('ui.profile.center.clickAvatarToEdit')} placement="top">
                  <UserAvatar
                    size={resolveResponsiveValue(APP_SPACING.avatarSize.large, isMobile)}
                    avatarUrl={avatarValue || currentUser?.avatarUrl}
                    userId={currentUser?.userId}
                    userUuid={currentUser?.userUuid}
                    username={currentUser?.username}
                  />
                </Tooltip>
              </Upload>
            </ImgCrop>
          </Space>
        </div>
      ) : null}

      {visibleProfileFields.size ? (
        <Row gutter={[resolveResponsiveValue(APP_SPACING.rowGutterPanel, isMobile)[0], 0]}>
          <Col xs={24}>
            <Form.Item label={t('ui.profile.center.username')}>
              <Input value={currentUser?.username || '-'} disabled />
            </Form.Item>
          </Col>
          <Col xs={24}>
            <Form.Item name="nickname" label={t('ui.profile.center.nickname')}>
              <Input placeholder={t('ui.profile.center.enterANickname')} />
            </Form.Item>
          </Col>
          {visibleProfileFields.has('realName') ? (
            <Col xs={24}>
              <Form.Item name="realName" label={t('ui.profile.center.fullName')}>
                <Input placeholder={t('ui.profile.center.enterYourFullName')} />
              </Form.Item>
            </Col>
          ) : null}
          {visibleProfileFields.has('birthMonth') ? (
            <Col xs={24}>
              <Form.Item name="birthMonth" label={t('ui.profile.center.birthMonth')}>
                <DatePicker picker="month" placeholder={t('ui.profile.center.selectYourBirthMonth')} format="YYYY年MM月" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          ) : null}
          {visibleProfileFields.has('gender') ? (
            <Col xs={24}>
              <Form.Item name="gender" label={t('ui.profile.center.gender')}>
                <Select allowClear placeholder={t('ui.profile.center.selectGender')} options={GENDER_OPTIONS} />
              </Form.Item>
            </Col>
          ) : null}
          {visibleProfileFields.has('region') ? (
            <Col xs={24}>
              <Form.Item name="region" label={t('ui.profile.center.region')}>
                <Input placeholder={t('ui.profile.center.enterYourRegion')} />
              </Form.Item>
            </Col>
          ) : null}
          {visibleProfileFields.has('idCardNumber') ? (
            <Col xs={24}>
              <Form.Item name="idCardNumber" label={t('ui.profile.center.idCardNumber')} rules={[{ validator: validateOptionalChinaIdCard }]} normalize={trimString}>
                <Input placeholder={t('ui.profile.center.enterYourIdCardNumber')} />
              </Form.Item>
            </Col>
          ) : null}
          {visibleCustomProfileFields.map((field) => (
            <Col xs={24} key={field.fieldKey}>
              <Form.Item
                name={['extraProfileValues', field.fieldKey]}
                label={field.fieldLabel}
                normalize={trimString}
                rules={field.required ? [{ required: true, message: t('ui.profile.center.pleaseEnter.a5d392a3', { fieldLabel: field.fieldLabel }) }] : undefined}
              >
                {renderCustomProfileInput(field)}
              </Form.Item>
            </Col>
          ))}
        </Row>
      ) : (
        <Empty description={t('ui.profile.center.noEditableProfileFieldsAreEnabled')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      )}
    </Form>
    </StandardDrawer>
  );
};

type ProfileCenterOverviewSectionProps = {
  isMobile: boolean;
  profileBasicCardRef: RefObject<HTMLDivElement>;
  avatarValue?: string;
  currentUser?: CurrentUser | null;
  displayName: string;
  activeRoleName: string;
  loading: boolean;
  profileCompletionSummary?: ProfileCompletionSummary | null;
  hasVisibleProfileFields: boolean;
  profileSaving: boolean;
  profileFormProps: FormProps;
  visibleProfileFields: Set<string>;
  visibleCustomProfileFields: ProfileFieldSetting[];
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
  profileCompletionSummary,
  hasVisibleProfileFields,
  profileSaving,
  profileFormProps,
  visibleProfileFields,
  visibleCustomProfileFields,
  avatarUploading,
  editingOpen,
  onSave,
  onEditOpenChange,
  onAvatarBeforeCrop,
  onAvatarUploadRequest,
  recentLoginLogs,
}: ProfileCenterOverviewSectionProps) => {
  const { token } = theme.useToken();
  const completionRate = profileCompletionSummary?.completionRate ?? 0;

  return (
    <>
      <div className="saas-profile-page__top-row">
        <Card className="saas-profile-page__card saas-profile-page__summary-card">
          <div className="saas-profile-page__summary-content saas-profile-page__summary-content--account-only">
            <section className="saas-profile-page__account-panel" aria-label={t('ui.profile.center.accountIdentity')}>
            <Space align="center" size={resolveResponsiveValue(APP_SPACING.mobileProfileSectionGap, isMobile)} className="saas-profile-page__welcome-profile">
                <UserAvatar
                  size={isMobile ? 56 : 64}
                  avatarUrl={avatarValue || currentUser?.avatarUrl}
                  userId={currentUser?.userId}
                  userUuid={currentUser?.userUuid}
                  username={currentUser?.username}
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
        <Card className="saas-profile-page__card saas-profile-page__completion-inline-card" loading={loading}>
          <div className="saas-profile-page__completion-block">
            <section className="saas-profile-page__completion-panel" aria-label={t('ui.profile.center.completeness')}>
              <Progress
                type="circle"
                percent={completionRate}
                size={isMobile ? 72 : 88}
                strokeColor={completionRate === 100 ? token.colorSuccess : token.colorWhite}
                trailColor="rgba(255, 255, 255, 0.22)"
                format={(percent) => <span className="saas-profile-page__completion-percent">{percent ?? 0}%</span>}
              />
              <Typography.Text className="saas-profile-page__completion-label">{t('ui.profile.center.completeness')}</Typography.Text>
            </section>
          </div>
        </Card>
      </div>

      <div ref={profileBasicCardRef}>
        <Card
          title={t('ui.profile.center.personalInformation')}
          loading={loading}
          className="saas-profile-page__card saas-profile-page__personal-card"
          style={{ width: '100%' }}
          extra={
            <Space size={resolveResponsiveValue(APP_SPACING.microGap, isMobile)}>
              <Tooltip title={t('ui.profile.center.editProfile.bef8a313')}>
                <Button
                  type="text"
                  shape="circle"
                  aria-label={t('ui.profile.center.editProfile.bef8a313')}
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
                { key: 'username', label: t('ui.profile.center.username'), children: currentUser?.username || '-' },
                ...(visibleProfileFields.has('nickname') ? [{ key: 'nickname', label: t('ui.profile.center.nickname'), children: currentUser?.nickname || '-' }] : []),
                ...(visibleProfileFields.has('realName') ? [{ key: 'realName', label: t('ui.profile.center.fullName'), children: currentUser?.realName || '-' }] : []),
                ...(visibleProfileFields.has('birthMonth') ? [{ key: 'birthMonth', label: t('ui.profile.center.birthMonth'), children: currentUser?.birthMonth || '-' }] : []),
                ...(visibleProfileFields.has('gender') ? [{ key: 'gender', label: t('ui.profile.center.gender'), children: GENDER_OPTIONS.find((item) => item.value === currentUser?.gender)?.label || '-' }] : []),
                ...(visibleProfileFields.has('region') ? [{ key: 'region', label: t('ui.profile.center.region'), children: currentUser?.region || '-' }] : []),
                ...(visibleProfileFields.has('idCardNumber') ? [{ key: 'idCardNumber', label: t('ui.profile.center.idCardNumber'), children: currentUser?.idCardNumber || '-' }] : []),
                ...visibleCustomProfileFields.map((field) => ({
                  key: field.fieldKey,
                  label: field.fieldLabel,
                  children: currentUser?.extraProfileValues?.[field.fieldKey] || '-',
                })),
              ]}
            />
          ) : (
        <Empty description={t('ui.profile.center.noEditableProfileFieldsAreEnabled')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Card>
        <ProfileBasicEditDrawer
          isMobile={isMobile}
          profileSaving={profileSaving}
          profileFormProps={profileFormProps}
          visibleProfileFields={visibleProfileFields}
          visibleCustomProfileFields={visibleCustomProfileFields}
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
        className="saas-profile-page__card saas-profile-page__login-records-card"
      >
        {recentLoginLogs.length ? (
          <Timeline
            items={recentLoginLogs.map((item) => ({
              children: (
                <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.microGap, isMobile)}>
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

type ProfileCenterBindingSectionProps = {
  isMobile: boolean;
  loginMethods: LoginMethodItem[];
  passkeys: PasskeyCredentialRecord[];
  passkeyBinding: boolean;
  passkeyBusy: boolean;
  passkeyEnabled: boolean;
  loginMethodsLoading: boolean;
  providers: SecondFactorProviderStatus[];
  providersLoading: boolean;
  bindingLoading: boolean;
  bindingSubmitting: boolean;
  unbindBusy: boolean;
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
  passkeyBinding,
  passkeyBusy,
  passkeyEnabled,
  loginMethodsLoading,
  providers,
  providersLoading,
  bindingLoading,
  bindingSubmitting,
  unbindBusy,
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
          <Tag color={item.statusColor || (item.value ? 'green' : 'default')}>{item.statusLabel || (item.value ? t('ui.profile.center.bound') : t('ui.profile.center.unbound'))}</Tag>
                  {item.methodLabel ? <Tag color={item.methodColor}>{item.methodLabel}</Tag> : null}
                </Space>
                <Typography.Text type="secondary">{item.value || t('ui.profile.center.noBindingInformation')}</Typography.Text>
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
      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('ui.profile.center.noBindableSignInMethodsAreAvailableYet')} />
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
                  {provider.systemEnabled === false ? <Tag color="red">{t('ui.profile.center.systemDisabled')}</Tag> : null}
                  <Tag color={provider.bound ? 'green' : provider.enabled ? 'gold' : 'default'}>{provider.bound ? t('ui.profile.center.bound') : t('ui.profile.center.unbound')}</Tag>
                  <Tag>{provider.factorCode}</Tag>
                </Space>
                <Typography.Text type="secondary">{provider.maskedContact || provider.statusMessage || t('ui.profile.center.noBindingIdentifier')}</Typography.Text>
              </Space>
              <Space
                wrap
                style={{
                  flexShrink: 0,
                  justifyContent: isMobile ? 'flex-start' : 'flex-end',
                  width: isMobile ? '100%' : 'auto',
                }}
              >
                {!provider.bound ? (
                  <Button
                  type="primary"
                  block={isMobile}
                  onClick={() => onBind(provider)}
                  disabled={currentBindingLoading || currentBindingSubmitting || unbindBusy || provider.systemEnabled === false}
                >
                  {provider.systemEnabled === false ? t('ui.profile.center.systemDisabled.7ac9d2dd') : t('ui.profile.center.bind')}
                  </Button>
                ) : null}
                {provider.bound ? (
                  <Button danger block={isMobile} onClick={() => onUnbind(provider)} disabled={unbindBusy || provider.systemEnabled === false}>
                    {t('ui.profile.center.unbind')}
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
        {t('ui.profile.center.passkey')}
      </Divider>
      {items.length ? (
        <List
          dataSource={items}
          split={false}
          renderItem={(item) => (
            <List.Item
              style={{ paddingInline: 0 }}
              actions={[
                <Button key="rename" type="link" onClick={() => onRenamePasskey(item.id, item.label)} disabled={passkeyBusy}>
                  {t('ui.profile.center.rename')}
                </Button>,
                <Button key="delete" type="link" danger onClick={() => onDeletePasskey(item.id)} disabled={passkeyBusy}>
                  {t('ui.profile.center.delete')}
                </Button>,
              ]}
            >
              <List.Item.Meta
                avatar={<KeyOutlined />}
                title={item.label || t('ui.profile.center.passkey')}
                description={`${t('ui.profile.center.createdAt')}: ${item.createdAt || '-'} · ${t('ui.profile.center.lastUsed')}: ${item.lastUsedAt || '-'}`}
              />
            </List.Item>
          )}
        />
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('ui.profile.center.noPasskeysAreBoundYet')} />
      )}
    </>
  );

  const bindingPanel = (
    <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, isMobile)} style={{ width: '100%' }}>
      <Card
        title={t('ui.profile.center.signInMethodBinding')}
        loading={loginMethodsLoading}
        className="saas-profile-page__card"
        extra={
          <Tooltip title={passkeyEnabled ? undefined : t('ui.profile.center.passkeySignInIsNotEnabled')}>
            <Button icon={<KeyOutlined />} loading={passkeyBinding} disabled={passkeyBusy || !passkeyEnabled} onClick={onBindPasskey}>
            {t('ui.profile.center.addPasskey')}
            </Button>
          </Tooltip>
        }
      >
        {renderLoginMethodList(loginMethods)}
        {renderPasskeyList(passkeys)}
      </Card>
      <Card title={t('ui.profile.center.secondFactorMethods')} loading={providersLoading} className="saas-profile-page__card">
        {renderProviderList(providers, bindingLoading, bindingSubmitting, onBindProvider, onUnbindProvider, '当前暂无可绑定二次验证方式')}
      </Card>
    </Space>
  );

  if (isMobile) {
    return (
      <section className="saas-profile-page__side-section" aria-label={t('ui.profile.center.accountStatus')}>
        {bindingPanel}
      </section>
    );
  }

  return bindingPanel;
};

const ProfileCenterPage = () => {
  const {
    responsive,
    currentUser,
    profileSectionAccess,
    interactionAccess,
    passkeys,
    loginMethodsLoading,
    providers,
    providersLoading,
  } = useProfileCenterPageAccess();

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
                profileCompletionSummary={profileSectionAccess.profileCompletionSummary}
                hasVisibleProfileFields={profileSectionAccess.hasVisibleProfileFields}
                profileSaving={profileSectionAccess.profileSaving}
                profileFormProps={profileSectionAccess.profileFormProps}
                visibleProfileFields={profileSectionAccess.visibleProfileFields}
                visibleCustomProfileFields={profileSectionAccess.visibleCustomProfileFields}
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
              <ProfileCenterBindingSection
                isMobile={responsive.isMobile}
                loginMethods={interactionAccess.passkeyAccess.loginMethods}
                passkeys={passkeys}
                passkeyBinding={interactionAccess.passkeyAccess.passkeyBinding}
                passkeyBusy={
                  interactionAccess.passkeyAccess.passkeyBinding
                  || interactionAccess.passkeyAccess.passkeyVerificationChallengeLoading
                  || interactionAccess.passkeyAccess.passkeyVerificationSubmitting
                }
                passkeyEnabled={interactionAccess.passkeyAccess.passkeyEnabled}
                loginMethodsLoading={loginMethodsLoading}
                providers={providers}
                providersLoading={providersLoading}
                bindingLoading={interactionAccess.securityAccess.bindingLoading}
                bindingSubmitting={interactionAccess.securityAccess.bindingSubmitting}
                unbindBusy={interactionAccess.securityAccess.unbindChallengeLoading || interactionAccess.securityAccess.unbindSubmitting}
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
            ? `${interactionAccess.securityAccess.bindingProvider.factorName || interactionAccess.securityAccess.bindingProvider.factorCode} · 2FA ${t('ui.profile.center.binding')}`
            : t('ui.profile.center.2faBinding')
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
            title={t('ui.profile.center.bindingComplete')}
            subTitle={t('ui.profile.center.saveTheRecoveryCodesBelowInASafe')}
            extra={[
              <Button key="close" type="primary" onClick={interactionAccess.securityAccess.closeBindModal}>
                {t('ui.profile.center.done')}
              </Button>,
            ]}
            style={{ padding: 0 }}
          >
            <Card className="saas-profile-page__card" size="small" title={t('ui.profile.center.recoveryCodes')}>
              <Space wrap>
                {(interactionAccess.securityAccess.bindingChallenge.recoveryCodes || []).length ? (
                  interactionAccess.securityAccess.bindingChallenge.recoveryCodes!.map((code) => (
                    <Tag key={code} color="gold">
                      {code}
                    </Tag>
                  ))
                ) : (
                  <Typography.Text type="secondary">{t('ui.profile.center.noRecoveryCodes')}</Typography.Text>
                )}
              </Space>
              <Divider />
              <Typography.Paragraph
                style={{ marginBottom: 0 }}
                type="secondary"
                copyable={{ text: (interactionAccess.securityAccess.bindingChallenge.recoveryCodes || []).join('\n') }}
              >
                {t('ui.profile.center.clickToCopyAllRecoveryCodes')}
              </Typography.Paragraph>
            </Card>
          </Result>
        ) : interactionAccess.securityAccess.bindingChallenge ? (
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
        ) : (
          <BindSecondFactorVerificationGate
            provider={interactionAccess.securityAccess.bindingProvider}
            verificationProvider={interactionAccess.securityAccess.bindingVerificationFactor}
            verificationChallenge={interactionAccess.securityAccess.bindingVerificationChallenge}
            verificationLoading={interactionAccess.securityAccess.bindingVerificationChallengeLoading}
            bindingLoading={interactionAccess.securityAccess.bindingLoading}
            bindingSubmitting={interactionAccess.securityAccess.bindingSubmitting}
            bindingAlert={interactionAccess.securityAccess.bindingAlert}
            formProps={interactionAccess.securityAccess.bindVerificationFormProps}
            onCancel={interactionAccess.securityAccess.closeBindModal}
            onRetry={() => void interactionAccess.securityAccess.onRetryBindVerificationChallenge()}
            onSubmit={interactionAccess.securityAccess.onConfirmBindVerification}
            isMobile={responsive.isMobile}
          />
        )}
      </Modal>
      <UnbindSecondFactorModal
        open={Boolean(interactionAccess.securityAccess.unbindProvider)}
        provider={interactionAccess.securityAccess.unbindProvider}
        challenge={interactionAccess.securityAccess.unbindChallenge}
        challengeLoading={interactionAccess.securityAccess.unbindChallengeLoading}
        submitting={interactionAccess.securityAccess.unbindSubmitting}
        alertMessage={interactionAccess.securityAccess.unbindAlert}
        formProps={interactionAccess.securityAccess.unbindFormProps}
        onCancel={interactionAccess.securityAccess.closeUnbindModal}
        onRetry={() => void interactionAccess.securityAccess.retryUnbindChallenge()}
        onSubmit={interactionAccess.securityAccess.handleConfirmUnbind}
        isMobile={responsive.isMobile}
      />
      <PasskeyVerificationModal
        open={interactionAccess.passkeyAccess.passkeyVerificationOpen}
        action={interactionAccess.passkeyAccess.passkeyVerificationAction}
        verificationProvider={interactionAccess.passkeyAccess.passkeyVerificationFactor}
        verificationChallenge={interactionAccess.passkeyAccess.passkeyVerificationChallenge}
        challengeLoading={interactionAccess.passkeyAccess.passkeyVerificationChallengeLoading}
        submitting={interactionAccess.passkeyAccess.passkeyVerificationSubmitting}
        alertMessage={interactionAccess.passkeyAccess.passkeyVerificationAlert}
        formProps={interactionAccess.passkeyAccess.passkeyVerificationFormProps}
        onCancel={interactionAccess.passkeyAccess.onClosePasskeyVerification}
        onRetry={() => void interactionAccess.passkeyAccess.onRetryPasskeyVerificationChallenge()}
        onSubmit={interactionAccess.passkeyAccess.onConfirmPasskeyVerification}
        isMobile={responsive.isMobile}
      />
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
          interactionAccess.contactBindAccess.contactBindSubmitting
          || interactionAccess.contactBindAccess.contactBindChallengeLoading
          || interactionAccess.contactBindAccess.contactBindCurrentChallengeLoading
        }
        alertMessage={interactionAccess.contactBindAccess.contactBindAlert}
        currentVerificationProvider={interactionAccess.contactBindAccess.contactBindCurrentFactor}
        currentVerificationChallenge={interactionAccess.contactBindAccess.contactBindCurrentChallenge}
        currentVerificationLoading={interactionAccess.contactBindAccess.contactBindCurrentChallengeLoading}
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
        onRetryCurrentVerification={() => void interactionAccess.contactBindAccess.retryContactBindCurrentChallenge()}
      />
    </ManagementPage>
  );
};

export default ProfileCenterPage;
