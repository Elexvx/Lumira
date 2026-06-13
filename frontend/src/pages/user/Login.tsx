import { formatMessage } from '@umijs/max';
import { Alert, Button, Form, Input, Modal } from 'antd';
import { type CSSProperties } from 'react';
import { LoginFormPage } from '@ant-design/pro-components';
import type { FormInstance, FormProps } from 'antd';
import { useLoginFlow } from '@/pages/user/login/hooks/useLoginFlow';
import { useResponsive } from '@/hooks/useResponsive';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import type { AgreementSettings, CaptchaChallenge, LoginCapabilities, LoginCodeChallenge, LoginResponse } from '@/types/api';
import { LoginFormFields, type LoginFormValues, type LoginMode } from '@/pages/user/login/components/LoginFormFields';
import { AUTH_AGREEMENT_MODAL_WIDTH_BY_BREAKPOINT } from '@/constants/ui';
import './Login.css';

const INITIAL_PASSWORD = '123456';
type ForcedPasswordChangeFormValues = {
  newPassword: string;
  confirmPassword: string;
};

type LoginPageMainSectionProps = {
  loginForm: FormInstance<LoginFormValues>;
  loginPageStyle: CSSProperties;
  brandingWebsiteName: string;
  loginSubTitle: string;
  submitButtonText: string;
  activeLoginMode: LoginMode;
  availableLoginModes: LoginMode[];
  agreementSettings: AgreementSettings;
  pendingSecondFactorLogin: LoginResponse | null;
  pendingSecondFactorPrompt: string;
  securityCaptchaEnabled: boolean;
  securityCaptchaType: CaptchaChallenge['captchaType'];
  captchaChallenge: CaptchaChallenge | null;
  captchaLoading: boolean;
  captchaImageLoadFailed: boolean;
  sendingLoginType: 'sms' | 'email' | null;
  loginCodeChallenges: Partial<Record<'sms' | 'email', LoginCodeChallenge | null>>;
  loginCodeCooldownSeconds: Partial<Record<'sms' | 'email', number>>;
  loginCapabilities: LoginCapabilities;
  submitting: boolean;
  passkeySubmitting: boolean;
  setActiveLoginMode: (mode: LoginMode) => void;
  openAgreementPreview: (previewKind: 'user' | 'privacy') => void;
  handleSendLoginCode: (mode: 'sms' | 'email') => void;
  handleWechatLogin: () => void;
  handlePasskeyLogin: () => void;
  refreshCaptcha: () => void;
  setCaptchaImageLoadFailed: (value: boolean) => void;
  setCaptchaChallenge: (challenge: CaptchaChallenge | null) => void;
  handleSubmit: (values: LoginFormValues) => Promise<boolean>;
  handleFinishFailed: NonNullable<FormProps<LoginFormValues>['onFinishFailed']>;
  setCaptchaProof: (value: string) => void;
  resetCaptchaProof: () => void;
};

const LoginPageMainSection = ({
  loginForm,
  loginPageStyle,
  brandingWebsiteName,
  loginSubTitle,
  submitButtonText,
  activeLoginMode,
  availableLoginModes,
  agreementSettings,
  pendingSecondFactorLogin,
  pendingSecondFactorPrompt,
  securityCaptchaEnabled,
  securityCaptchaType,
  captchaChallenge,
  captchaLoading,
  captchaImageLoadFailed,
  sendingLoginType,
  loginCodeChallenges,
  loginCodeCooldownSeconds,
  loginCapabilities,
  submitting,
  passkeySubmitting,
  setActiveLoginMode,
  openAgreementPreview,
  handleSendLoginCode,
  handleWechatLogin,
  handlePasskeyLogin,
  refreshCaptcha,
  setCaptchaImageLoadFailed,
  setCaptchaChallenge,
  handleSubmit,
  handleFinishFailed,
  setCaptchaProof,
  resetCaptchaProof,
}: LoginPageMainSectionProps) => (
  <div className="saas-login-page" style={loginPageStyle}>
    <LoginFormPage<LoginFormValues>
      form={loginForm}
      title={brandingWebsiteName}
      subTitle={loginSubTitle}
      onFinish={handleSubmit}
      onFinishFailed={handleFinishFailed}
      submitter={{
        submitButtonProps: {
          children: submitButtonText,
          loading: submitting,
          block: true,
          'data-testid': 'login-submit-button',
          style: activeLoginMode === 'passkey' && !pendingSecondFactorLogin ? { display: 'none' } : undefined,
        },
        resetButtonProps: false,
      }}
      containerStyle={{
        width: '100%',
        maxWidth: 'var(--saas-spacing-536)',
        boxSizing: 'border-box',
      }}
      style={{
        width: '100%',
        minHeight: '100%',
        background: 'transparent',
      }}
      mainStyle={{ width: '100%', maxWidth: 'var(--saas-spacing-440)', margin: '0 auto', background: 'transparent' }}
    >
      <LoginFormFields
        activeLoginMode={activeLoginMode}
        availableLoginModes={availableLoginModes}
        agreementSettings={agreementSettings}
        pendingSecondFactorLogin={pendingSecondFactorLogin}
        pendingSecondFactorPrompt={pendingSecondFactorPrompt}
        securityCaptchaEnabled={securityCaptchaEnabled}
        securityCaptchaType={securityCaptchaType}
        captchaChallenge={captchaChallenge}
        captchaLoading={captchaLoading}
        captchaImageLoadFailed={captchaImageLoadFailed}
        sendingLoginType={sendingLoginType}
        loginCodeChallenges={loginCodeChallenges}
        loginCodeCooldownSeconds={loginCodeCooldownSeconds}
        wechatLoginAvailable={Boolean(loginCapabilities.wechatLoginAvailable)}
        passkeyLoading={passkeySubmitting}
        onModeChange={setActiveLoginMode}
        onSendLoginCode={(mode) => void handleSendLoginCode(mode)}
        onWechatLogin={() => void handleWechatLogin()}
        onPasskeyLogin={() => void handlePasskeyLogin()}
        onRefreshCaptcha={() => void refreshCaptcha()}
        onCaptchaImageError={() => setCaptchaImageLoadFailed(true)}
        onSliderCaptchaChallengeChange={setCaptchaChallenge}
        onSliderCaptchaVerified={setCaptchaProof}
        onSliderCaptchaReset={resetCaptchaProof}
        onOpenAgreementPreview={openAgreementPreview}
      />
    </LoginFormPage>
  </div>
);

const Login = () => {
  const loginFlow = useLoginFlow();
  const responsive = useResponsive();
  const alertBottomGap = resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile);
  const loginSubTitle =
    loginFlow.activeLoginMode === 'password'
      ? formatMessage({ id: 'page.login.passwordSubtitle', defaultMessage: 'Password login' })
      : loginFlow.activeLoginMode === 'passkey'
        ? formatMessage({ id: 'page.login.passkey', defaultMessage: '使用通行密钥登录' })
        : loginFlow.activeLoginMode === 'sms'
          ? formatMessage({ id: 'page.login.smsSubtitle', defaultMessage: 'SMS code login' })
          : formatMessage({ id: 'page.login.emailSubtitle', defaultMessage: 'Email code login' });
  const submitButtonText = loginFlow.viewState.pendingSecondFactorLogin
    ? formatMessage({ id: 'page.login.submit.verify', defaultMessage: 'Verify and log in' })
    : loginFlow.activeLoginMode === 'passkey'
      ? formatMessage({ id: 'page.login.passkey', defaultMessage: '使用通行密钥登录' })
      : formatMessage({ id: 'page.login.submit.login', defaultMessage: 'Log in' });

  return (
    <>
      <LoginPageMainSection
        loginForm={loginFlow.loginForm}
        activeLoginMode={loginFlow.activeLoginMode}
        availableLoginModes={loginFlow.availableLoginModes}
        agreementSettings={loginFlow.agreementSettings}
        pendingSecondFactorLogin={loginFlow.viewState.pendingSecondFactorLogin}
        pendingSecondFactorPrompt={loginFlow.viewState.pendingSecondFactorPrompt}
        securityCaptchaEnabled={loginFlow.viewState.securitySettings.captchaEnabled}
        securityCaptchaType={loginFlow.viewState.securitySettings.captchaType}
        captchaChallenge={loginFlow.viewState.captchaChallenge}
        captchaLoading={loginFlow.viewState.captchaLoading}
        captchaImageLoadFailed={loginFlow.viewState.captchaImageLoadFailed}
        sendingLoginType={loginFlow.viewState.sendingLoginType}
        loginCodeChallenges={loginFlow.viewState.loginCodeChallenges}
        loginCodeCooldownSeconds={loginFlow.viewState.loginCodeCooldownSeconds}
        loginCapabilities={loginFlow.loginCapabilities}
        submitting={loginFlow.viewState.submitting}
        passkeySubmitting={loginFlow.viewState.passkeySubmitting}
        setActiveLoginMode={loginFlow.setActiveLoginMode}
        openAgreementPreview={loginFlow.actions.openAgreementPreview}
        handleSendLoginCode={loginFlow.actions.handleSendLoginCode}
        handleWechatLogin={() => void loginFlow.actions.handleWechatLogin()}
        handlePasskeyLogin={() => void loginFlow.actions.handlePasskeyLogin()}
        refreshCaptcha={() => void loginFlow.actions.refreshCaptcha()}
        setCaptchaImageLoadFailed={loginFlow.actions.setCaptchaImageLoadFailed}
        setCaptchaChallenge={loginFlow.actions.setCaptchaChallenge}
        handleSubmit={loginFlow.actions.handleSubmit}
        handleFinishFailed={loginFlow.actions.handleFinishFailed}
        setCaptchaProof={loginFlow.actions.setCaptchaProof}
        resetCaptchaProof={loginFlow.actions.resetCaptchaProof}
        loginPageStyle={loginFlow.loginPageStyle}
        brandingWebsiteName={loginFlow.brandingWebsiteName}
        loginSubTitle={loginSubTitle}
        submitButtonText={submitButtonText}
      />
      <Modal
        className="saas-login-page__agreement-modal"
        open={loginFlow.dialogState.agreementPreviewOpen}
        onCancel={() => loginFlow.dialogState.setAgreementPreviewOpen(false)}
        footer={null}
        width={resolveResponsiveValue(AUTH_AGREEMENT_MODAL_WIDTH_BY_BREAKPOINT, responsive.isMobile)}
        centered
        title={loginFlow.dialogState.agreementPreviewTitle}
        destroyOnHidden
      >
        {loginFlow.dialogState.agreementPreviewMarkdown ? (
          <div style={{ whiteSpace: 'pre-wrap', lineHeight: 1.75 }}>
            {loginFlow.dialogState.agreementPreviewMarkdown}
          </div>
        ) : (
          <div style={{ color: 'var(--ant-color-text-secondary)' }}>
            {formatMessage({ id: 'page.login.agreement.empty', defaultMessage: 'The backend has not configured this agreement yet.' })}
          </div>
        )}
      </Modal>
      <Modal
        open={loginFlow.viewState.forcedPasswordChangeOpen}
        title={formatMessage({ id: 'page.login.initialPasswordChange.title', defaultMessage: '修改初始密码' })}
        closable={false}
        maskClosable={false}
        keyboard={false}
        footer={null}
        destroyOnClose
      >
        <Alert
          type="warning"
          showIcon
          message={formatMessage({ id: 'page.login.initialPasswordChange.notice', defaultMessage: '当前账号仍在使用初始密码，必须修改后才能进入系统。' })}
          style={{ marginBottom: alertBottomGap }}
        />
        <Form<ForcedPasswordChangeFormValues>
          form={loginFlow.forcedPasswordChangeForm}
          layout="vertical"
          onFinish={loginFlow.dialogState.handleForcedPasswordChange}
        >
          <Form.Item
            name="newPassword"
            label={formatMessage({ id: 'page.login.initialPasswordChange.newPassword', defaultMessage: '新密码' })}
            rules={[
              { required: true, message: formatMessage({ id: 'page.login.initialPasswordChange.newPasswordRequired', defaultMessage: '请输入新密码' }) },
              {
                validator: (_, value) =>
                  value === INITIAL_PASSWORD
                    ? Promise.reject(new Error(formatMessage({ id: 'page.login.initialPasswordChange.notInitial', defaultMessage: '新密码不能继续使用初始密码' })))
                    : Promise.resolve(),
              },
            ]}
          >
            <Input.Password autoComplete="new-password" data-testid="forced-password-new-input" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label={formatMessage({ id: 'page.login.initialPasswordChange.confirmPassword', defaultMessage: '确认新密码' })}
            dependencies={['newPassword']}
            rules={[
              { required: true, message: formatMessage({ id: 'page.login.initialPasswordChange.confirmPasswordRequired', defaultMessage: '请再次输入新密码' }) },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error(formatMessage({ id: 'page.login.initialPasswordChange.passwordMismatch', defaultMessage: '两次输入的新密码不一致' })));
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" data-testid="forced-password-confirm-input" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loginFlow.viewState.passwordChangeSubmitting} data-testid="forced-password-submit">
            {formatMessage({ id: 'page.login.initialPasswordChange.submit', defaultMessage: '确认修改' })}
          </Button>
        </Form>
      </Modal>
    </>
  );
};

export default Login;
