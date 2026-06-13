import type { CaptchaChallenge, LoginCodeChallenge, LoginResponse, AgreementSettings } from '@/types/api';
import { CheckOutlined, KeyOutlined, SafetyCertificateOutlined, UserOutlined, WechatOutlined } from '@ant-design/icons';
import { formatMessage } from '@umijs/max';
import { Alert, Button, Checkbox, Form, Image, Input, Modal, Segmented, Skeleton, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { LockOutlined } from '@ant-design/icons';
import { useResponsive } from '@/hooks/useResponsive';
import { resolveResponsiveValue } from '@/theme/spacing';
import {
  getLoginInputValueFromEvent,
  rejectUnsafeLoginInput,
  shouldBlockLoginInputKey,
  shouldBlockLoginInputPaste,
  type LoginInputKind,
} from '@/pages/user/login/hooks/useLoginFlowRuntime';
import { SliderCaptchaBox } from '@/components/captcha/SliderCaptchaBox';
import { MailOutlined, MobileOutlined } from '@ant-design/icons';
import { LOGIN_SLIDER_CAPTCHA_MODAL_WIDTH_BY_BREAKPOINT } from '@/constants/ui';

const CAPTCHA_ALLOWED_CHAR_PATTERN = /^[A-Za-z0-9]$/;
const CAPTCHA_SANITIZE_PATTERN = /[^A-Za-z0-9]/g;

const sanitizeCaptchaValue = (value: string) => value.replace(CAPTCHA_SANITIZE_PATTERN, '');

const getCaptchaValueFromEvent = (event: { target?: { value?: unknown } } | string | number | null | undefined) => {
  if (typeof event === 'string' || typeof event === 'number') {
    return sanitizeCaptchaValue(String(event));
  }

  return sanitizeCaptchaValue(String(event?.target?.value ?? ''));
};

const shouldBlockCaptchaKey = (event: {
  altKey?: boolean;
  ctrlKey?: boolean;
  isComposing?: boolean;
  key?: string;
  keyCode?: number;
  metaKey?: boolean;
}) => {
  if (event.isComposing || event.keyCode === 229) {
    return true;
  }

  if (event.ctrlKey || event.altKey || event.metaKey) {
    return false;
  }

  if (!event.key || event.key.length !== 1) {
    return false;
  }

  return !CAPTCHA_ALLOWED_CHAR_PATTERN.test(event.key);
};

const shouldBlockCaptchaPaste = (event: { clipboardData?: { getData: (type: string) => string } }) => {
  const text = event.clipboardData?.getData('text') ?? '';
  return sanitizeCaptchaValue(text) !== text;
};

const MODE_META: Record<LoginMode, { label: string }> = {
  passkey: { label: formatMessage({ id: 'page.login.passkeyShort', defaultMessage: '通行密钥' }) },
  password: { label: formatMessage({ id: 'page.login.passwordAccount', defaultMessage: '密码登录' }) },
  sms: { label: formatMessage({ id: 'page.login.smsCode', defaultMessage: 'SMS code' }) },
  email: { label: formatMessage({ id: 'page.login.emailCode', defaultMessage: 'Email code' }) },
};

const LoginModeSwitcher = ({
  activeLoginMode,
  availableLoginModes,
  onModeChange,
  onPasskeyLogin,
  passkeyLoading,
  modeContent,
}: {
  activeLoginMode: LoginMode;
  availableLoginModes: LoginMode[];
  onModeChange: (mode: LoginMode) => void;
  onPasskeyLogin: () => void;
  passkeyLoading?: boolean;
  modeContent: React.ReactNode;
}) => {
  const showModeControl = availableLoginModes.length > 1 || availableLoginModes[0] !== 'password';
  const modeOptions = availableLoginModes.map((mode) => ({
    value: mode,
    label: MODE_META[mode].label,
  }));

  return (
    <>
      {showModeControl ? (
        <Segmented
          block
          value={activeLoginMode}
          options={modeOptions}
          onChange={(key) => onModeChange(key as LoginMode)}
          className="saas-login-page__mode-segmented"
        />
      ) : null}
      <div className="saas-login-page__mode-content">
        {activeLoginMode === 'passkey' ? (
          <div className="saas-login-page__passkey-panel">
            <Button block size="large" type="primary" icon={<KeyOutlined />} loading={passkeyLoading} onClick={onPasskeyLogin}>
              {formatMessage({ id: 'page.login.passkey', defaultMessage: '使用通行密钥登录' })}
            </Button>
          </div>
        ) : (
          modeContent
        )}
      </div>
    </>
  );
};

const LoginSecondFactorPrompt = ({ prompt }: { prompt: string }) => {
  const unsafeCodeMessage = formatMessage({ id: 'page.login.error.invalidCodeCharacters', defaultMessage: 'Verification code can only contain letters and numbers' });

  const guardedInputEvents = (kind: LoginInputKind) => ({
    onKeyDown: (event: React.KeyboardEvent<HTMLInputElement>) => {
      if (shouldBlockLoginInputKey(kind, event)) {
        event.preventDefault();
      }
    },
    onPaste: (event: React.ClipboardEvent<HTMLInputElement>) => {
      if (shouldBlockLoginInputPaste(kind, event)) {
        event.preventDefault();
      }
    },
  });

  return (
    <>
      <Alert showIcon type="info" message={prompt} />
      <Form.Item
        name="verificationCode"
        rules={[
          { required: true, message: formatMessage({ id: 'page.login.error.pleaseEnterCaptcha', defaultMessage: 'Please enter the verification code' }) },
          { validator: (_, value) => rejectUnsafeLoginInput(_, value, 'verificationCode', unsafeCodeMessage) },
        ]}
        getValueFromEvent={getLoginInputValueFromEvent('verificationCode')}
      >
        <Input
          size="large"
          autoComplete="one-time-code"
          inputMode="numeric"
          maxLength={12}
          placeholder={formatMessage({ id: 'page.login.error.pleaseEnterCaptcha', defaultMessage: 'Please enter the verification code' })}
          {...guardedInputEvents('verificationCode')}
        />
      </Form.Item>
    </>
  );
};

const CodeLoginPanel = ({
  mode,
  loginCodeChallenge,
  cooldownSeconds,
  sending,
  onSendLoginCode,
}: {
  mode: CodeLoginMode;
  loginCodeChallenge: LoginCodeChallenge | null;
  cooldownSeconds: number;
  sending: boolean;
  onSendLoginCode: (mode: CodeLoginMode) => void;
}) => {
  const form = Form.useFormInstance<LoginFormValues>();
  const accountValue = Form.useWatch(mode === 'sms' ? 'smsAccount' : 'emailAccount', form);
  const unsafeAccountMessage = formatMessage({ id: 'page.login.error.invalidAccountCharacters', defaultMessage: 'The account contains unsupported characters' });
  const unsafeMobileMessage = formatMessage({ id: 'page.login.error.invalidMobile', defaultMessage: 'Please enter a valid mobile number' });
  const unsafeCodeMessage = formatMessage({ id: 'page.login.error.invalidCodeCharacters', defaultMessage: 'Verification code can only contain letters and numbers' });

  return (
    <div className="saas-login-page__credentials-stack">
      <Form.Item
        name={mode === 'sms' ? 'smsAccount' : 'emailAccount'}
        getValueFromEvent={getLoginInputValueFromEvent(mode === 'sms' ? 'mobile' : 'email')}
        rules={[
          {
            required: true,
            message:
              mode === 'sms'
                ? formatMessage({ id: 'page.login.error.pleaseEnterMobile', defaultMessage: 'Please enter your mobile number' })
                : formatMessage({ id: 'page.login.error.pleaseEnterEmail', defaultMessage: 'Please enter your email' }),
          },
          ...(mode === 'sms'
            ? [
                { pattern: /^1[3-9]\d{9}$/, message: unsafeMobileMessage },
                { validator: (_: unknown, value: unknown) => rejectUnsafeLoginInput(_, value, 'mobile', unsafeMobileMessage) },
              ]
            : [
                { type: 'email' as const, message: formatMessage({ id: 'page.login.error.invalidEmail', defaultMessage: 'Please enter a valid email address' }) },
                { max: 128, message: formatMessage({ id: 'page.login.error.accountLength', defaultMessage: 'Account cannot exceed 128 characters' }) },
                { validator: (_: unknown, value: unknown) => rejectUnsafeLoginInput(_, value, 'email', unsafeAccountMessage) },
              ]),
        ]}
      >
        <Input
          size="large"
          prefix={mode === 'sms' ? <MobileOutlined className="saas-login-page__field-icon" /> : <MailOutlined className="saas-login-page__field-icon" />}
          autoComplete={mode === 'sms' ? 'tel' : 'email'}
          inputMode={mode === 'sms' ? 'numeric' : 'email'}
          maxLength={mode === 'sms' ? 11 : 128}
          placeholder={
            mode === 'sms'
              ? formatMessage({ id: 'page.login.error.pleaseEnterMobile', defaultMessage: 'Please enter your mobile number' })
              : formatMessage({ id: 'page.login.error.pleaseEnterEmail', defaultMessage: 'Please enter your email' })
          }
          onKeyDown={(event) => {
            if (shouldBlockLoginInputKey(mode === 'sms' ? 'mobile' : 'email', event)) {
              event.preventDefault();
            }
          }}
          onPaste={(event) => {
            if (shouldBlockLoginInputPaste(mode === 'sms' ? 'mobile' : 'email', event)) {
              event.preventDefault();
            }
          }}
        />
      </Form.Item>
      <div className="saas-login-page__code-row">
        <Form.Item
          name={mode === 'sms' ? 'smsVerificationCode' : 'emailVerificationCode'}
          rules={[
            { required: true, message: formatMessage({ id: 'page.login.error.pleaseEnterCaptcha', defaultMessage: 'Please enter the verification code' }) },
            { validator: (_, value) => rejectUnsafeLoginInput(_, value, 'verificationCode', unsafeCodeMessage) },
          ]}
          getValueFromEvent={getLoginInputValueFromEvent('verificationCode')}
          className="saas-login-page__code-input saas-login-page__feedback-reserved"
        >
          <Input
            size="large"
            autoComplete="one-time-code"
            inputMode="numeric"
            maxLength={12}
            placeholder={formatMessage({ id: 'page.login.error.pleaseEnterCaptcha', defaultMessage: 'Please enter the verification code' })}
            onKeyDown={(event) => {
              if (shouldBlockLoginInputKey('verificationCode', event)) {
                event.preventDefault();
              }
            }}
            onPaste={(event) => {
              if (shouldBlockLoginInputPaste('verificationCode', event)) {
                event.preventDefault();
              }
            }}
          />
        </Form.Item>
        <Button
          size="large"
          onClick={() => onSendLoginCode(mode)}
          loading={sending}
          className="saas-login-page__send-code-button"
          disabled={!accountValue || cooldownSeconds > 0}
        >
          {cooldownSeconds > 0
            ? formatMessage({ id: 'page.login.code.countdown', defaultMessage: '{seconds}s 后重发' }, { seconds: cooldownSeconds })
            : loginCodeChallenge?.challengeId
              ? formatMessage({ id: 'page.login.code.refresh', defaultMessage: 'Resend' })
              : formatMessage({ id: 'page.login.code.send', defaultMessage: 'Send code' })}
        </Button>
      </div>
    </div>
  );
};

const PasswordLoginImageCaptcha = ({
  captchaChallenge,
  captchaLoading,
  captchaImageLoadFailed,
  onRefreshCaptcha,
  onCaptchaImageError,
}: {
  captchaChallenge: CaptchaChallenge | null;
  captchaLoading: boolean;
  captchaImageLoadFailed: boolean;
  onRefreshCaptcha: () => void;
  onCaptchaImageError: () => void;
}) => (
  <div className="saas-login-page__captcha-row">
    <Form.Item
      key={captchaChallenge?.captchaId || 'captcha-code'}
      name="captchaCode"
      rules={[{ required: true, message: formatMessage({ id: 'page.login.error.pleaseEnterCaptcha', defaultMessage: 'Please enter the captcha' }) }]}
      getValueFromEvent={getCaptchaValueFromEvent}
      className="saas-login-page__captcha-input"
    >
      <Input
        size="large"
        autoComplete="off"
        autoCapitalize="off"
        autoCorrect="off"
        lang="en"
        spellCheck={false}
        inputMode="text"
        maxLength={8}
        placeholder={formatMessage({ id: 'page.login.error.pleaseEnterCaptcha', defaultMessage: 'Please enter the captcha' })}
        aria-label={formatMessage({ id: 'page.login.error.pleaseEnterCaptcha', defaultMessage: 'Captcha' })}
        className="saas-login-page__captcha-native-input"
        data-testid="login-captcha-input"
        onCompositionStart={(event) => event.preventDefault()}
        onKeyDown={(event) => {
          if (shouldBlockCaptchaKey(event)) {
            event.preventDefault();
          }
        }}
        onPaste={(event) => {
          if (shouldBlockCaptchaPaste(event)) {
            event.preventDefault();
          }
        }}
      />
    </Form.Item>
    <Button
      size="large"
      aria-label={formatMessage({ id: 'page.login.captcha.refresh', defaultMessage: 'Refresh captcha' })}
      title={formatMessage({ id: 'page.login.captcha.refresh', defaultMessage: 'Click to refresh the captcha' })}
      onClick={onRefreshCaptcha}
      className="saas-login-page__captcha-image-button"
      data-testid="login-captcha-refresh"
    >
      {captchaLoading ? (
        <Skeleton.Image active className="saas-login-page__captcha-skeleton" />
      ) : captchaImageLoadFailed ? (
        <Typography.Text type="secondary">{formatMessage({ id: 'page.login.captcha.retry', defaultMessage: 'Click to retry' })}</Typography.Text>
      ) : captchaChallenge?.imageUrl ? (
        <Image
          src={captchaChallenge.imageUrl}
          alt={formatMessage({ id: 'page.login.captcha.alt', defaultMessage: 'Captcha' })}
          preview={false}
          onError={onCaptchaImageError}
          className="saas-login-page__captcha-image"
        />
      ) : (
        <Typography.Text type="secondary">{formatMessage({ id: 'page.login.captcha.refreshText', defaultMessage: 'Click to refresh' })}</Typography.Text>
      )}
    </Button>
  </div>
);

const PasswordLoginSliderCaptcha = ({
  sliderVerificationStatus,
  sliderCaptchaOpen,
  onStartSliderCaptcha,
  onCloseSliderCaptcha,
  onSliderCaptchaChallengeChange,
  onSliderCaptchaVerified,
  onSliderCaptchaReset,
}: {
  sliderVerificationStatus: SliderVerificationStatus;
  sliderCaptchaOpen: boolean;
  onStartSliderCaptcha: () => void;
  onCloseSliderCaptcha: () => void;
  onSliderCaptchaChallengeChange: (challenge: CaptchaChallenge | null) => void;
  onSliderCaptchaVerified: (captchaProof: string) => void;
  onSliderCaptchaReset: () => void;
}) => {
  const responsive = useResponsive();

  return (
    <>
      <Form.Item
        name="captchaProof"
        hidden
        rules={[{ required: true, message: formatMessage({ id: 'page.login.error.pleaseCompleteSliderCaptcha', defaultMessage: 'Please complete the slider captcha first' }) }]}
      >
        <Input />
      </Form.Item>
      {sliderVerificationStatus === 'verified' ? (
        <div className="saas-login-page__slider-verified" aria-live="polite">
          <span>{formatMessage({ id: 'page.login.captcha.sliderVerified', defaultMessage: '已验证' })}</span>
          <CheckOutlined />
        </div>
      ) : (
        <Button block size="large" icon={<SafetyCertificateOutlined />} onClick={onStartSliderCaptcha} className="saas-login-page__slider-placeholder">
          {formatMessage({ id: 'page.login.captcha.startSlider', defaultMessage: '验证' })}
        </Button>
      )}
      <Modal
        centered
        destroyOnHidden
        footer={null}
        open={sliderCaptchaOpen}
        title={formatMessage({ id: 'page.login.captcha.sliderTitle', defaultMessage: '拖动验证' })}
        width={resolveResponsiveValue(LOGIN_SLIDER_CAPTCHA_MODAL_WIDTH_BY_BREAKPOINT, responsive.isMobile)}
        onCancel={onCloseSliderCaptcha}
        className="saas-login-page__slider-modal"
      >
        <SliderCaptchaBox
          mode="embed"
          onChallengeChange={onSliderCaptchaChallengeChange}
          onVerified={(result) => {
            onSliderCaptchaVerified(result.captchaProof);
          }}
          onReset={onSliderCaptchaReset}
        />
      </Modal>
    </>
  );
};

export type LoginMode = 'passkey' | 'sms' | 'email' | 'password';
type SliderVerificationStatus = 'idle' | 'verified';
type CodeLoginMode = Extract<LoginMode, 'sms' | 'email'>;

export interface LoginFormValues {
  passwordAccount?: string;
  passwordPassword?: string;
  smsAccount?: string;
  smsVerificationCode?: string;
  emailAccount?: string;
  emailVerificationCode?: string;
  remember?: boolean;
  captchaCode?: string;
  captchaProof?: string;
  agreementAccepted?: boolean;
  verificationCode?: string;
}

interface LoginFormFieldsProps {
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
  sendingLoginType: CodeLoginMode | null;
  loginCodeChallenges: Partial<Record<CodeLoginMode, LoginCodeChallenge | null>>;
  loginCodeCooldownSeconds: Partial<Record<CodeLoginMode, number>>;
  wechatLoginAvailable?: boolean;
  passkeyLoading?: boolean;
  onModeChange: (mode: LoginMode) => void;
  onSendLoginCode: (mode: CodeLoginMode) => void;
  onWechatLogin: () => void;
  onPasskeyLogin: () => void;
  onRefreshCaptcha: () => void;
  onCaptchaImageError: () => void;
  onSliderCaptchaChallengeChange: (challenge: CaptchaChallenge | null) => void;
  onSliderCaptchaVerified: (captchaProof: string) => void;
  onSliderCaptchaReset: () => void;
  onOpenAgreementPreview: (previewKind: 'user' | 'privacy') => void;
}

const PasswordLoginCredentialsFields = () => {
  const unsafeAccountMessage = formatMessage({ id: 'page.login.error.invalidAccountCharacters', defaultMessage: 'The account contains unsupported characters' });

  const guardedInputEvents = (kind: LoginInputKind) => ({
    onKeyDown: (event: React.KeyboardEvent<HTMLInputElement>) => {
      if (shouldBlockLoginInputKey(kind, event)) {
        event.preventDefault();
      }
    },
    onPaste: (event: React.ClipboardEvent<HTMLInputElement>) => {
      if (shouldBlockLoginInputPaste(kind, event)) {
        event.preventDefault();
      }
    },
  });

  return (
    <>
      <Form.Item
        name="passwordAccount"
        rules={[
          { required: true, message: formatMessage({ id: 'page.login.error.pleaseEnterAccount', defaultMessage: 'Please enter your account, mobile number, or email' }) },
          { max: 128, message: formatMessage({ id: 'page.login.error.accountLength', defaultMessage: 'Account cannot exceed 128 characters' }) },
          { validator: (_, value) => rejectUnsafeLoginInput(_, value, 'account', unsafeAccountMessage) },
        ]}
        getValueFromEvent={getLoginInputValueFromEvent('account')}
      >
        <Input
          size="large"
          prefix={<UserOutlined className="saas-login-page__field-icon" />}
          autoComplete="username"
          maxLength={128}
          placeholder={formatMessage({ id: 'page.login.error.pleaseEnterAccount', defaultMessage: 'Please enter your account, mobile number, or email' })}
          data-testid="login-account-input"
          {...guardedInputEvents('account')}
        />
      </Form.Item>
      <Form.Item
        name="passwordPassword"
        className="saas-login-page__password-item saas-login-page__feedback-reserved"
        rules={[
          { required: true, message: formatMessage({ id: 'page.login.error.pleaseEnterPassword', defaultMessage: 'Please enter your password' }) },
          { min: 6, message: formatMessage({ id: 'page.login.error.passwordLength', defaultMessage: 'Password must be at least 6 characters' }) },
        ]}
      >
        <Input.Password
          size="large"
          prefix={<LockOutlined className="saas-login-page__field-icon" />}
          autoComplete="current-password"
          placeholder={formatMessage({ id: 'page.login.error.pleaseEnterPassword', defaultMessage: 'Please enter your password' })}
          data-testid="login-password-input"
        />
      </Form.Item>
    </>
  );
};

const PasswordLoginPanel = ({
  securityCaptchaEnabled,
  securityCaptchaType,
  captchaChallenge,
  captchaLoading,
  captchaImageLoadFailed,
  onRefreshCaptcha,
  onCaptchaImageError,
  onSliderCaptchaChallengeChange,
  onSliderCaptchaVerified,
  onSliderCaptchaReset,
}: {
  securityCaptchaEnabled: boolean;
  securityCaptchaType: CaptchaChallenge['captchaType'];
  captchaChallenge: CaptchaChallenge | null;
  captchaLoading: boolean;
  captchaImageLoadFailed: boolean;
  onRefreshCaptcha: () => void;
  onCaptchaImageError: () => void;
  onSliderCaptchaChallengeChange: (challenge: CaptchaChallenge | null) => void;
  onSliderCaptchaVerified: (captchaProof: string) => void;
  onSliderCaptchaReset: () => void;
}) => {
  const form = Form.useFormInstance<LoginFormValues>();
  const passwordAccount = Form.useWatch('passwordAccount', form);
  const passwordPassword = Form.useWatch('passwordPassword', form);
  const [sliderVerificationStatus, setSliderVerificationStatus] = useState<'idle' | 'verified'>('idle');
  const [sliderCaptchaOpen, setSliderCaptchaOpen] = useState(false);
  const previousPasswordCredentialsRef = useRef<{ account?: string; password?: string } | null>(null);

  useEffect(() => {
    const previous = previousPasswordCredentialsRef.current;
    previousPasswordCredentialsRef.current = { account: passwordAccount, password: passwordPassword };

    if (securityCaptchaType !== 'SLIDER') {
      return;
    }

    if (!previous) {
      return;
    }

    if (previous.account !== passwordAccount || previous.password !== passwordPassword) {
      setSliderVerificationStatus('idle');
      setSliderCaptchaOpen(false);
      onSliderCaptchaChallengeChange(null);
      onSliderCaptchaReset();
    }
  }, [onSliderCaptchaChallengeChange, onSliderCaptchaReset, passwordAccount, passwordPassword, securityCaptchaType]);

  const handleStartSliderCaptcha = async () => {
    try {
      await form.validateFields(['passwordAccount', 'passwordPassword']);
      setSliderCaptchaOpen(true);
    } catch {
      // Ant Design will surface the field-level validation errors.
    }
  };

  const handleCloseSliderCaptcha = () => {
    setSliderCaptchaOpen(false);
    onSliderCaptchaChallengeChange(null);
  };

  return (
    <div className="saas-login-page__credentials-stack">
      <PasswordLoginCredentialsFields />
      {securityCaptchaEnabled ? (
        securityCaptchaType !== 'SLIDER' ? (
          <PasswordLoginImageCaptcha
            captchaChallenge={captchaChallenge}
            captchaLoading={captchaLoading}
            captchaImageLoadFailed={captchaImageLoadFailed}
            onRefreshCaptcha={onRefreshCaptcha}
            onCaptchaImageError={onCaptchaImageError}
          />
        ) : (
          <PasswordLoginSliderCaptcha
            sliderVerificationStatus={sliderVerificationStatus as SliderVerificationStatus}
            sliderCaptchaOpen={sliderCaptchaOpen}
            onStartSliderCaptcha={() => void handleStartSliderCaptcha()}
            onCloseSliderCaptcha={handleCloseSliderCaptcha}
            onSliderCaptchaChallengeChange={onSliderCaptchaChallengeChange}
            onSliderCaptchaVerified={(captchaProof) => {
              onSliderCaptchaVerified(captchaProof);
              setSliderVerificationStatus('verified');
              setSliderCaptchaOpen(false);
            }}
            onSliderCaptchaReset={onSliderCaptchaReset}
          />
        )
      ) : null}
    </div>
  );
};

export const LoginFormFields = ({
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
  wechatLoginAvailable,
  passkeyLoading,
  onModeChange,
  onSendLoginCode,
  onWechatLogin,
  onPasskeyLogin,
  onRefreshCaptcha,
  onCaptchaImageError,
  onSliderCaptchaChallengeChange,
  onSliderCaptchaVerified,
  onSliderCaptchaReset,
  onOpenAgreementPreview,
}: LoginFormFieldsProps) => {
  const codeLoginMode: CodeLoginMode = activeLoginMode === 'sms' ? 'sms' : 'email';
  const hasAgreement = Boolean(agreementSettings.userAgreementMarkdown || agreementSettings.privacyAgreementMarkdown);

  const modeContent =
    pendingSecondFactorLogin ? (
      <LoginSecondFactorPrompt prompt={pendingSecondFactorPrompt} />
    ) : activeLoginMode === 'password' ? (
      <PasswordLoginPanel
        securityCaptchaEnabled={securityCaptchaEnabled}
        securityCaptchaType={securityCaptchaType}
        captchaChallenge={captchaChallenge}
        captchaLoading={captchaLoading}
        captchaImageLoadFailed={captchaImageLoadFailed}
        onRefreshCaptcha={onRefreshCaptcha}
        onCaptchaImageError={onCaptchaImageError}
        onSliderCaptchaChallengeChange={onSliderCaptchaChallengeChange}
        onSliderCaptchaVerified={onSliderCaptchaVerified}
        onSliderCaptchaReset={onSliderCaptchaReset}
      />
    ) : (
      <CodeLoginPanel
        mode={codeLoginMode}
        loginCodeChallenge={(codeLoginMode === 'sms' ? loginCodeChallenges.sms : loginCodeChallenges.email) ?? null}
        cooldownSeconds={loginCodeCooldownSeconds[codeLoginMode] || 0}
        sending={sendingLoginType === codeLoginMode}
        onSendLoginCode={onSendLoginCode}
      />
    );

  return (
    <>
      <LoginModeSwitcher
        activeLoginMode={activeLoginMode}
        availableLoginModes={availableLoginModes}
        onModeChange={onModeChange}
        onPasskeyLogin={onPasskeyLogin}
        passkeyLoading={passkeyLoading}
        modeContent={modeContent}
      />
      {activeLoginMode !== 'passkey' && !pendingSecondFactorLogin ? (
        <>
          {wechatLoginAvailable ? (
            <Button block size="large" icon={<WechatOutlined />} onClick={onWechatLogin} className="saas-login-page__wechat-button">
              {formatMessage({ id: 'page.login.wechat', defaultMessage: 'WeChat login' })}
            </Button>
          ) : null}
          <div className="saas-login-page__agreement">
            {hasAgreement ? (
              <Form.Item
                name="agreementAccepted"
                valuePropName="checked"
                rules={[
                  {
                    validator: async (_, value) => {
                      if (hasAgreement && !value) {
                        throw new Error(formatMessage({ id: 'page.login.agreement.required', defaultMessage: 'Please agree to the terms before logging in' }));
                      }
                    },
                  },
                ]}
              >
                <Checkbox data-testid="login-agreement-checkbox">
                  {formatMessage({ id: 'page.login.agreement.accept', defaultMessage: 'I have read and agree to' })}
                  <Button type="link" size="small" onClick={() => onOpenAgreementPreview('user')}>
                    {formatMessage({ id: 'page.login.agreement.user', defaultMessage: 'User Agreement' })}
                  </Button>
                  {formatMessage({ id: 'page.login.agreement.and', defaultMessage: 'and' })}
                  <Button type="link" size="small" onClick={() => onOpenAgreementPreview('privacy')}>
                    {formatMessage({ id: 'page.login.agreement.privacy', defaultMessage: 'Privacy Policy' })}
                  </Button>
                </Checkbox>
              </Form.Item>
            ) : null}
          </div>
          <div className="saas-login-page__actions">
            <Form.Item noStyle name="remember" valuePropName="checked">
              <Checkbox>{formatMessage({ id: 'page.login.remember', defaultMessage: 'Remember me' })}</Checkbox>
            </Form.Item>
          </div>
        </>
      ) : null}
    </>
  );
};
