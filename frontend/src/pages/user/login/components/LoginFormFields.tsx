import { CheckOutlined, KeyOutlined, LockOutlined, MailOutlined, MobileOutlined, SafetyCertificateOutlined, UserOutlined, WechatOutlined } from '@ant-design/icons';
import { formatMessage } from '@umijs/max';
import { Alert, Button, Checkbox, Form, Image, Input, Modal, Segmented, Skeleton, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { SliderCaptchaBox } from '@/components/captcha/SliderCaptchaBox';
import type { CaptchaChallenge, LoginCodeChallenge, LoginResponse, AgreementSettings } from '@/types/api';
import { getCaptchaValueFromEvent, shouldBlockCaptchaKey, shouldBlockCaptchaPaste } from '@/pages/user/login/captchaInput';
import {
  getLoginInputValueFromEvent,
  rejectUnsafeLoginInput,
  shouldBlockLoginInputKey,
  shouldBlockLoginInputPaste,
  type LoginInputKind,
} from '@/pages/user/login/loginInputGuards';

export type LoginMode = 'passkey' | 'sms' | 'email' | 'password';
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

type SliderVerificationStatus = 'idle' | 'verified';

const MODE_META: Record<LoginMode, { label: string; subtitle: string }> = {
  passkey: {
    label: formatMessage({ id: 'page.login.passkeyShort', defaultMessage: '通行密钥' }),
    subtitle: formatMessage({ id: 'page.login.passkey', defaultMessage: '使用通行密钥登录' }),
  },
  password: {
    label: formatMessage({ id: 'page.login.passwordAccount', defaultMessage: '密码登录' }),
    subtitle: formatMessage({ id: 'page.login.passwordSubtitle', defaultMessage: '密码登录' }),
  },
  sms: {
    label: formatMessage({ id: 'page.login.smsCode', defaultMessage: 'SMS code' }),
    subtitle: formatMessage({ id: 'page.login.smsSubtitle', defaultMessage: 'SMS code login' }),
  },
  email: {
    label: formatMessage({ id: 'page.login.emailCode', defaultMessage: 'Email code' }),
    subtitle: formatMessage({ id: 'page.login.emailSubtitle', defaultMessage: 'Email code login' }),
  },
};

const getAccountPlaceholder = (mode: CodeLoginMode) =>
  mode === 'sms'
    ? formatMessage({ id: 'page.login.error.pleaseEnterMobile', defaultMessage: 'Please enter your mobile number' })
    : formatMessage({ id: 'page.login.error.pleaseEnterEmail', defaultMessage: 'Please enter your email' });

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
  const form = Form.useFormInstance<LoginFormValues>();
  const passwordAccount = Form.useWatch('passwordAccount', form);
  const passwordPassword = Form.useWatch('passwordPassword', form);
  const smsAccount = Form.useWatch('smsAccount', form);
  const emailAccount = Form.useWatch('emailAccount', form);
  const [sliderVerificationStatus, setSliderVerificationStatus] = useState<SliderVerificationStatus>('idle');
  const [sliderCaptchaOpen, setSliderCaptchaOpen] = useState(false);
  const previousPasswordCredentialsRef = useRef<{ account?: string; password?: string } | null>(null);
  const hasAgreement = Boolean(agreementSettings.userAgreementMarkdown || agreementSettings.privacyAgreementMarkdown);
  const showModeControl = availableLoginModes.length > 1 || availableLoginModes[0] !== 'password';
  const pendingChallenge = activeLoginMode === 'sms' ? loginCodeChallenges.sms : activeLoginMode === 'email' ? loginCodeChallenges.email : null;
  const unsafeAccountMessage = formatMessage({ id: 'page.login.error.invalidAccountCharacters', defaultMessage: 'The account contains unsupported characters' });
  const unsafeMobileMessage = formatMessage({ id: 'page.login.error.invalidMobile', defaultMessage: 'Please enter a valid mobile number' });
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

  useEffect(() => {
    const previous = previousPasswordCredentialsRef.current;
    previousPasswordCredentialsRef.current = { account: passwordAccount, password: passwordPassword };

    if (!previous) {
      return;
    }

    if (previous.account !== passwordAccount || previous.password !== passwordPassword) {
      setSliderVerificationStatus('idle');
      setSliderCaptchaOpen(false);
      onSliderCaptchaChallengeChange(null);
      onSliderCaptchaReset();
    }
  }, [passwordAccount, passwordPassword, onSliderCaptchaChallengeChange, onSliderCaptchaReset]);

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

  if (pendingSecondFactorLogin) {
    return (
      <>
        <Alert showIcon type="info" message={pendingSecondFactorPrompt} />
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
  }

  const renderPasswordTab = () => (
    <div className="saas-login-page__credentials-stack">
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
        />
      </Form.Item>
      {securityCaptchaEnabled && securityCaptchaType !== 'SLIDER' ? (
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
      ) : null}
      {securityCaptchaEnabled && securityCaptchaType === 'SLIDER' ? (
        <>
          <Form.Item name="captchaProof" hidden rules={[{ required: true, message: formatMessage({ id: 'page.login.error.pleaseCompleteSliderCaptcha', defaultMessage: 'Please complete the slider captcha first' }) }]}>
            <Input />
          </Form.Item>
          {sliderVerificationStatus === 'verified' ? (
            <div className="saas-login-page__slider-verified" aria-live="polite">
              <span>{formatMessage({ id: 'page.login.captcha.sliderVerified', defaultMessage: '已验证' })}</span>
              <CheckOutlined />
            </div>
          ) : (
            <Button
              block
              size="large"
              icon={<SafetyCertificateOutlined />}
              onClick={() => void handleStartSliderCaptcha()}
              className="saas-login-page__slider-placeholder"
            >
              {formatMessage({ id: 'page.login.captcha.startSlider', defaultMessage: '验证' })}
            </Button>
          )}
          <Modal
            centered
            destroyOnHidden
            footer={null}
            open={sliderCaptchaOpen}
            title={formatMessage({ id: 'page.login.captcha.sliderTitle', defaultMessage: '拖动验证' })}
            width={368}
            onCancel={handleCloseSliderCaptcha}
            className="saas-login-page__slider-modal"
          >
            <SliderCaptchaBox
              mode="embed"
              onChallengeChange={onSliderCaptchaChallengeChange}
              onVerified={(result) => {
                onSliderCaptchaVerified(result.captchaProof);
                setSliderVerificationStatus('verified');
                setSliderCaptchaOpen(false);
              }}
              onReset={onSliderCaptchaReset}
            />
          </Modal>
        </>
      ) : null}
    </div>
  );

  const renderCodeTab = (mode: CodeLoginMode) => {
    const accountValue = mode === 'sms' ? smsAccount : emailAccount;
    const challenge = pendingChallenge;
    return (
      <div className="saas-login-page__credentials-stack">
        <Form.Item
          name={mode === 'sms' ? 'smsAccount' : 'emailAccount'}
          getValueFromEvent={getLoginInputValueFromEvent(mode === 'sms' ? 'mobile' : 'email')}
          rules={[
            { required: true, message: mode === 'sms' ? formatMessage({ id: 'page.login.error.pleaseEnterMobile', defaultMessage: 'Please enter your mobile number' }) : formatMessage({ id: 'page.login.error.pleaseEnterEmail', defaultMessage: 'Please enter your email' }) },
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
            placeholder={getAccountPlaceholder(mode)}
            {...guardedInputEvents(mode === 'sms' ? 'mobile' : 'email')}
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
              {...guardedInputEvents('verificationCode')}
            />
          </Form.Item>
          <Button
            size="large"
            onClick={() => onSendLoginCode(mode)}
            loading={sendingLoginType === mode}
            className="saas-login-page__send-code-button"
            disabled={!accountValue}
          >
            {challenge?.challengeId
              ? formatMessage({ id: 'page.login.code.refresh', defaultMessage: 'Resend' })
              : formatMessage({ id: 'page.login.code.send', defaultMessage: 'Send code' })}
          </Button>
        </div>
      </div>
    );
  };

  const renderPasskeyPanel = () => (
    <div className="saas-login-page__passkey-panel">
      <Button
        block
        size="large"
        type="primary"
        icon={<KeyOutlined />}
        loading={passkeyLoading}
        onClick={onPasskeyLogin}
      >
        {formatMessage({ id: 'page.login.passkey', defaultMessage: '使用通行密钥登录' })}
      </Button>
    </div>
  );

  const modeOptions = availableLoginModes.map((mode) => ({
    value: mode,
    label: MODE_META[mode].label,
  }));
  const modeContent =
    activeLoginMode === 'passkey'
      ? renderPasskeyPanel()
      : activeLoginMode === 'password'
        ? renderPasswordTab()
        : renderCodeTab(activeLoginMode);

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
      <div className="saas-login-page__mode-content">{modeContent}</div>
      {!pendingSecondFactorLogin ? (
        <>
          {wechatLoginAvailable ? (
            <Button
              block
              size="large"
              icon={<WechatOutlined />}
              onClick={onWechatLogin}
              className="saas-login-page__wechat-button"
            >
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
                <Checkbox>
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
        </>
      ) : null}
      <div className="saas-login-page__actions">
        <Form.Item noStyle name="remember" valuePropName="checked">
          <Checkbox>{formatMessage({ id: 'page.login.remember', defaultMessage: 'Remember me' })}</Checkbox>
        </Form.Item>
      </div>
    </>
  );
};
