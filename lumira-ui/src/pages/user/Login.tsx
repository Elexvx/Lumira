import { getLocale, setLocale, useLocation } from '@umijs/max';
import { formatMessage } from '@/i18n/formatMessage';
import { App, Button, Form, Input, Modal, Select, Steps, message } from 'antd';
import { GlobalOutlined, MoonOutlined, SunOutlined } from '@ant-design/icons';
import { useEffect, useState, type CSSProperties } from 'react';
import type { FormInstance, FormProps } from 'antd';
import { useLoginFlow } from '@/pages/user/login/hooks/useLoginFlow';
import { useResponsive } from '@/hooks/useResponsive';
import { resolveResponsiveValue } from '@/theme/spacing';
import type { AgreementSettings, CaptchaChallenge, LoginCapabilities, LoginCodeChallenge, LoginResponse } from '@/types/api';
import { LoginFormFields, type LoginFormValues, type LoginMode } from '@/pages/user/login/components/LoginFormFields';
import { resolvePresentedLoginMode, resolvePresentedLoginModes } from '@/pages/user/login/utils/loginModePresentation';
import { AUTH_AGREEMENT_MODAL_WIDTH_BY_BREAKPOINT } from '@/constants/ui';
import { request } from '@/services/common/request';
import { isSessionExpiredLoginSearch } from '@/auth/sessionLifecycle';
import { showSessionExpiredNotice } from '@/pages/user/login/utils/sessionExpiredNotice';
import { useThemePreference } from '@/theme/ThemePreferenceProvider';
import policeBeianIcon from '@/assets/police-beian.png';
import { isPoliceBeianText, resolvePoliceBeianQueryUrl } from '@/branding/beian';
import './Login.css';

type ForcedPasswordChangeFormValues = {
  newPassword: string;
  confirmPassword: string;
};

type PasswordResetStep = 0 | 1 | 2;
type PasswordResetFormValues = {
  account?: string;
  contactType?: 'sms' | 'email';
  contact?: string;
  verificationCode?: string;
  newPassword?: string;
  confirmPassword?: string;
};

type LoginPageMainSectionProps = {
  loginForm: FormInstance<LoginFormValues>;
  loginPageStyle: CSSProperties;
  brandingWebsiteName: string;
  brandingFooterItems: string[];
  submitButtonText: string;
  activeLoginMode: LoginMode;
  availableLoginModes: LoginMode[];
  pendingSecondFactorLogin: LoginResponse | null;
  pendingSecondFactorPrompt: string;
  agreementSettings: AgreementSettings;
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
  openPasswordReset: () => void;
};

const LoginPageMainSection = ({ loginForm, loginPageStyle, brandingWebsiteName, brandingFooterItems, submitButtonText, activeLoginMode, availableLoginModes, pendingSecondFactorLogin, pendingSecondFactorPrompt, agreementSettings, securityCaptchaEnabled, securityCaptchaType, captchaChallenge, captchaLoading, captchaImageLoadFailed, sendingLoginType, loginCodeChallenges, loginCodeCooldownSeconds, loginCapabilities, submitting, passkeySubmitting, setActiveLoginMode, openAgreementPreview, handleSendLoginCode, handleWechatLogin, handlePasskeyLogin, refreshCaptcha, setCaptchaImageLoadFailed, setCaptchaChallenge, handleSubmit, handleFinishFailed, setCaptchaProof, resetCaptchaProof, openPasswordReset }: LoginPageMainSectionProps) => {
  const { message: messageApi } = App.useApp();
  const { resolvedColorMode, setThemePreference } = useThemePreference();
  const currentLocale = getLocale();
  const nextLocale = currentLocale.startsWith('zh') ? 'en-US' : 'zh-CN';
  const nextColorMode = resolvedColorMode === 'dark' ? 'light' : 'dark';
  const themeToggleLabel = currentLocale.startsWith('zh')
    ? nextColorMode === 'light'
      ? '切换为日间模式'
      : '切换为夜间模式'
    : nextColorMode === 'light'
      ? 'Switch to light mode'
      : 'Switch to dark mode';
  const joinMode = availableLoginModes.includes('sms')
    ? 'sms'
    : availableLoginModes.includes('email')
      ? 'email'
      : null;

  return (
    <div className="saas-login-page" style={loginPageStyle}>
      <section className="saas-login-page__hero" aria-label={brandingWebsiteName} />

      <section className="saas-login-page__auth-shell">
        <div className="saas-login-page__utility-actions">
          <Button
            type="text"
            className="saas-login-page__theme-switch"
            icon={resolvedColorMode === 'dark' ? <SunOutlined /> : <MoonOutlined />}
            aria-label={themeToggleLabel}
            title={themeToggleLabel}
            onClick={() => setThemePreference(nextColorMode)}
          />
          <Button
            type="text"
            className="saas-login-page__locale-switch"
            icon={<GlobalOutlined />}
            aria-label={formatMessage({
              id: 'app.locale.switch',
              defaultMessage: '切换语言',
            })}
            onClick={() => setLocale(nextLocale, true)}
          >
            {nextLocale === 'en-US' ? 'EN' : '中'}
          </Button>
        </div>

        <main className="saas-login-page__stage">
          <header className="saas-login-page__brand">
            <div className="saas-login-page__brand-title">
              {formatMessage(
                {
                  id: 'page.login.welcomeTitle',
                  defaultMessage: '欢迎登录 {websiteName}',
                },
                { websiteName: brandingWebsiteName },
              )}
            </div>
          </header>

          <section
            className="saas-login-page__panel"
            aria-label={formatMessage({
              id: 'page.login.title',
              defaultMessage: '登录',
            })}
          >
            <Form<LoginFormValues> form={loginForm} className="saas-login-page__form" onFinish={handleSubmit} onFinishFailed={handleFinishFailed}>
              <LoginFormFields
                activeLoginMode={activeLoginMode}
                availableLoginModes={availableLoginModes}
                pendingSecondFactorLogin={pendingSecondFactorLogin}
                pendingSecondFactorPrompt={pendingSecondFactorPrompt}
                agreementSettings={agreementSettings}
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
                onForgotPassword={openPasswordReset}
              />
              {(activeLoginMode === 'passkey' || activeLoginMode === 'wechat') && !pendingSecondFactorLogin ? null : (
                <>
                  <Button
                    block
                    size="large"
                    type="primary"
                    loading={submitting}
                    data-testid="login-submit-button"
                    className="saas-login-page__submit-button"
                    htmlType="submit"
                    onClick={() => {
                      const formValues = loginForm.getFieldsValue(true);
                      const accountInput = document.querySelector<HTMLInputElement>('[data-testid="login-account-input"]');
                      const passwordInput = document.querySelector<HTMLInputElement>('[data-testid="login-password-input"]');
                      const nextValues = {
                        ...formValues,
                        passwordAccount: formValues.passwordAccount || accountInput?.value,
                        passwordPassword: formValues.passwordPassword || passwordInput?.value,
                      };
                      loginForm.setFieldsValue(nextValues);
                    }}
                  >
                    {submitButtonText}
                  </Button>
                  <button
                    type="button"
                    data-testid="login-join-button"
                    className="saas-login-page__join-button"
                    onClick={() => {
                      if (joinMode) {
                        setActiveLoginMode(joinMode);
                        return;
                      }
                      messageApi.info(
                        formatMessage({
                          id: 'page.login.registrationUnavailable',
                          defaultMessage: currentLocale.startsWith('zh')
                            ? '注册通道暂未开放，请联系管理员'
                            : 'Registration is not available yet. Please contact an administrator.',
                        }),
                      );
                    }}
                  >
                    <span className="saas-login-page__join-prompt">
                      {formatMessage({
                        id: 'page.login.noAccount',
                        defaultMessage: currentLocale.startsWith('zh') ? '没有账号？' : 'No account?',
                      })}
                    </span>
                    <span className="saas-login-page__join-action">
                      {formatMessage({
                        id: 'page.login.joinUs',
                        defaultMessage: currentLocale.startsWith('zh') ? '加入我们' : 'Join us',
                      })}
                    </span>
                  </button>
                </>
              )}
            </Form>
          </section>
        </main>

        <footer className="saas-login-page__footer">
          {brandingFooterItems.map((item) =>
            isPoliceBeianText(item) ? (
              <a
                key={item}
                className="saas-login-page__footer-link"
                href={resolvePoliceBeianQueryUrl(item)}
                target="_blank"
                rel="noreferrer"
                data-testid="login-police-beian-link"
              >
                <img className="saas-police-beian-icon" src={policeBeianIcon} alt="" aria-hidden="true" />
                <span>{item}</span>
              </a>
            ) : (
              <span key={item}>{item}</span>
            ),
          )}
        </footer>
      </section>
    </div>
  );
};

const PasswordResetModal = ({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) => {
  const [form] = Form.useForm<PasswordResetFormValues>();
  const [step, setStep] = useState<PasswordResetStep>(0);
  const [challengeId, setChallengeId] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const resetState = () => {
    setStep(0);
    setChallengeId('');
    setSubmitting(false);
    form.resetFields();
  };

  const handleCancel = () => {
    resetState();
    onClose();
  };

  const handleNext = async () => {
    if (step === 0) {
      await form.validateFields(['account']);
      setStep(1);
      return;
    }

    if (step === 1) {
      const values = await form.validateFields(['account', 'contactType', 'contact']);
      setSubmitting(true);
      try {
        const challenge = await request<LoginCodeChallenge>('/v1/auth/password-reset/challenge', {
          method: 'POST',
          skipAuth: true,
          autoRedirectOnUnauthorized: false,
          data: {
            account: values.account,
            contactType: values.contactType,
            contact: values.contact,
          },
        });
        setChallengeId(challenge.challengeId);
        setStep(2);
        message.success(formatMessage({ id: 'page.login.passwordReset.codeSent', defaultMessage: '验证码已发送' }));
      } finally {
        setSubmitting(false);
      }
      return;
    }

    const values = await form.validateFields(['verificationCode', 'newPassword', 'confirmPassword']);
    setSubmitting(true);
    try {
      await request<boolean>('/v1/auth/password-reset/complete', {
        method: 'POST',
        skipAuth: true,
        autoRedirectOnUnauthorized: false,
        data: {
          challengeId,
          verificationCode: values.verificationCode,
          newPassword: values.newPassword,
        },
      });
      message.success(formatMessage({ id: 'page.login.passwordReset.success', defaultMessage: '密码已重置，请使用新密码登录' }));
      handleCancel();
    } finally {
      setSubmitting(false);
    }
  };

  const contactType = Form.useWatch('contactType', form);

  return (
    <Modal
      open={open}
      forceRender
      title={formatMessage({ id: 'page.login.passwordReset.title', defaultMessage: '重置密码' })}
      onCancel={handleCancel}
      destroyOnHidden
      footer={[
        step > 0 ? (
          <Button key="back" onClick={() => setStep((current) => Math.max(0, current - 1) as PasswordResetStep)} disabled={submitting}>
            {formatMessage({ id: 'page.login.passwordReset.back', defaultMessage: '上一步' })}
          </Button>
        ) : null,
        <Button key="next" type="primary" loading={submitting} onClick={() => void handleNext()}>
          {step === 2
            ? formatMessage({ id: 'page.login.passwordReset.submit', defaultMessage: '确认重置' })
            : formatMessage({ id: 'page.login.passwordReset.next', defaultMessage: '下一步' })}
        </Button>,
      ]}
    >
      <Steps
        size="small"
        current={step}
        className="saas-login-page__reset-steps"
        items={[
          { title: formatMessage({ id: 'page.login.passwordReset.stepAccount', defaultMessage: '账号' }) },
          { title: formatMessage({ id: 'page.login.passwordReset.stepContact', defaultMessage: '验证' }) },
          { title: formatMessage({ id: 'page.login.passwordReset.stepPassword', defaultMessage: '新密码' }) },
        ]}
      />
      <Form<PasswordResetFormValues>
        form={form}
        layout="vertical"
        preserve
        initialValues={{ contactType: 'email' }}
        className="saas-login-page__reset-form"
      >
        {step === 0 ? (
          <Form.Item
            name="account"
            label={formatMessage({ id: 'page.login.passwordReset.account', defaultMessage: '账号' })}
            rules={[
              { required: true, message: formatMessage({ id: 'page.login.error.pleaseEnterAccount', defaultMessage: '请输入账号、手机号或邮箱' }) },
              { max: 128, message: formatMessage({ id: 'page.login.error.accountLength', defaultMessage: '账号长度不能超过 128 个字符' }) },
            ]}
          >
            <Input autoComplete="username" maxLength={128} />
          </Form.Item>
        ) : null}
        {step === 1 ? (
          <>
            <Form.Item
              name="contactType"
              label={formatMessage({ id: 'page.login.passwordReset.contactType', defaultMessage: '验证方式' })}
              rules={[{ required: true }]}
            >
              <Select
                options={[
                  { label: formatMessage({ id: 'page.login.passwordReset.email', defaultMessage: '绑定邮箱' }), value: 'email' },
                  { label: formatMessage({ id: 'page.login.passwordReset.sms', defaultMessage: '绑定手机号' }), value: 'sms' },
                ]}
              />
            </Form.Item>
            <Form.Item
              name="contact"
              label={contactType === 'sms'
                ? formatMessage({ id: 'page.login.passwordReset.mobile', defaultMessage: '绑定手机号' })
                : formatMessage({ id: 'page.login.passwordReset.emailAddress', defaultMessage: '绑定邮箱' })}
              rules={[
                { required: true, message: formatMessage({ id: 'page.login.passwordReset.contactRequired', defaultMessage: '请输入账号绑定的邮箱或手机号' }) },
                ...(contactType === 'sms'
                  ? [{ pattern: /^1[3-9]\d{9}$/, message: formatMessage({ id: 'page.login.error.invalidMobile', defaultMessage: '请输入有效手机号' }) }]
                  : [{ type: 'email' as const, message: formatMessage({ id: 'page.login.error.invalidEmail', defaultMessage: '请输入有效邮箱地址' }) }]),
              ]}
            >
              <Input autoComplete={contactType === 'sms' ? 'tel' : 'email'} maxLength={128} />
            </Form.Item>
          </>
        ) : null}
        {step === 2 ? (
          <>
            <Form.Item
              name="verificationCode"
              label={formatMessage({ id: 'page.login.passwordReset.code', defaultMessage: '验证码' })}
              rules={[{ required: true, message: formatMessage({ id: 'page.login.error.pleaseEnterCaptcha', defaultMessage: '请输入验证码' }) }]}
            >
              <Input autoComplete="one-time-code" inputMode="numeric" maxLength={12} />
            </Form.Item>
            <Form.Item
              name="newPassword"
              label={formatMessage({ id: 'page.login.passwordReset.newPassword', defaultMessage: '新密码' })}
              rules={[
                { required: true, message: formatMessage({ id: 'page.login.initialPasswordChange.newPasswordRequired', defaultMessage: '请输入新密码' }) },
                { min: 6, message: formatMessage({ id: 'page.login.error.passwordLength', defaultMessage: '密码长度不能少于 6 位' }) },
              ]}
            >
              <Input.Password autoComplete="new-password" />
            </Form.Item>
            <Form.Item
              name="confirmPassword"
              label={formatMessage({ id: 'page.login.passwordReset.confirmPassword', defaultMessage: '确认新密码' })}
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
              <Input.Password autoComplete="new-password" />
            </Form.Item>
          </>
        ) : null}
      </Form>
    </Modal>
  );
};

const Login = () => {
  const loginFlow = useLoginFlow();
  const location = useLocation();
  const responsive = useResponsive();
  const { message: messageApi } = App.useApp();
  const [passwordResetOpen, setPasswordResetOpen] = useState(false);
  const sessionExpired = isSessionExpiredLoginSearch(location.search);
  const presentedLoginModes = resolvePresentedLoginModes(responsive.isMobile, loginFlow.availableLoginModes);
  const presentedLoginMode = resolvePresentedLoginMode(responsive.isMobile, loginFlow.activeLoginMode, loginFlow.availableLoginModes);
  const initialPasswordChangeNotice = formatMessage({
    id: 'page.login.initialPasswordChange.notice',
    defaultMessage: '当前账号仍在使用初始密码，必须修改后才能进入系统。',
  });
  const submitButtonText = loginFlow.viewState.pendingSecondFactorLogin
    ? formatMessage({ id: 'page.login.submit.verify', defaultMessage: 'Verify and log in' })
    : presentedLoginMode === 'passkey'
      ? formatMessage({ id: 'page.login.passkey', defaultMessage: '使用通行密钥登录' })
      : presentedLoginMode === 'wechat'
        ? formatMessage({ id: 'page.login.wechat', defaultMessage: 'WeChat login' })
      : formatMessage({ id: 'page.login.submit.login', defaultMessage: 'Log in' });

  useEffect(() => {
    const viewportClassName = 'saas-login-viewport';
    document.documentElement.classList.add(viewportClassName);
    document.body.classList.add(viewportClassName);

    return () => {
      document.documentElement.classList.remove(viewportClassName);
      document.body.classList.remove(viewportClassName);
    };
  }, []);

  useEffect(() => {
    if (!loginFlow.viewState.forcedPasswordChangeOpen) {
      return;
    }
    messageApi.warning({
      key: 'initial-password-change-notice',
      content: initialPasswordChangeNotice,
      duration: 4,
    });
  }, [initialPasswordChangeNotice, loginFlow.viewState.forcedPasswordChangeOpen, messageApi]);

  useEffect(() => {
    if (!sessionExpired) {
      return;
    }
    showSessionExpiredNotice(
      message,
      formatMessage({ id: 'common.sessionExpired', defaultMessage: '登录状态已失效，请重新登录' }),
    );
  }, [sessionExpired]);

  return (
    <>
      <LoginPageMainSection
        loginForm={loginFlow.loginForm}
        activeLoginMode={presentedLoginMode}
        availableLoginModes={presentedLoginModes}
        pendingSecondFactorLogin={loginFlow.viewState.pendingSecondFactorLogin}
        pendingSecondFactorPrompt={loginFlow.viewState.pendingSecondFactorPrompt}
        agreementSettings={loginFlow.agreementSettings}
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
        openPasswordReset={() => setPasswordResetOpen(true)}
        loginPageStyle={loginFlow.loginPageStyle}
        brandingWebsiteName={loginFlow.brandingWebsiteName}
        brandingFooterItems={loginFlow.brandingFooterItems}
        submitButtonText={submitButtonText}
      />
      <PasswordResetModal open={passwordResetOpen} onClose={() => setPasswordResetOpen(false)} />
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
