import {
  CheckCircleOutlined,
  LockOutlined,
  MailOutlined,
  MobileOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { Button, Form, Input, Typography } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { formatMessage } from '@/i18n/formatMessage';
import { request } from '@/services/common/request';
import { message } from '@/theme/antdFeedbackBridge';
import type {
  CaptchaChallenge,
  LoginCapabilities,
  LoginCodeChallenge,
  RegistrationContactAvailability,
  SecuritySettings,
} from '@/types/api';
import type { RegistrationSubmissionValues } from '@/pages/user/login/hooks/useLoginFlowRuntime';
import { PasswordLoginImageCaptcha } from './LoginFormFields';

type ContactKind = 'mobile' | 'email';
type AvailabilityStatus = 'idle' | 'checking' | 'available' | 'unavailable' | 'error';

type RegistrationFormValues = {
  mobile: string;
  mobileVerificationCode?: string;
  email: string;
  emailVerificationCode?: string;
  password: string;
  confirmPassword: string;
  captchaCode: string;
};

type RegistrationPanelProps = {
  loginCapabilities: LoginCapabilities;
  securitySettings: SecuritySettings;
  initialMobile?: string;
  onOpenAgreementPreview: (kind: 'user' | 'privacy') => void;
  onSubmit: (values: RegistrationSubmissionValues) => Promise<boolean>;
  onBackToLogin: () => void;
};

const MOBILE_PATTERN = /^1[3-9]\d{9}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const containsConsecutiveCharacters = (value: string) => {
  const normalized = value.toLowerCase();
  for (let index = 0; index < normalized.length - 2; index += 1) {
    const first = normalized.charCodeAt(index);
    const second = normalized.charCodeAt(index + 1);
    const third = normalized.charCodeAt(index + 2);
    const sameClass =
      ([first, second, third].every((code) => code >= 48 && code <= 57))
      || ([first, second, third].every((code) => code >= 97 && code <= 122));
    if (sameClass && ((second - first === 1 && third - second === 1) || (first - second === 1 && second - third === 1))) {
      return true;
    }
  }
  return false;
};

const normalizeContact = (kind: ContactKind, value: unknown) => {
  const text = String(value ?? '').trim();
  return kind === 'mobile' ? text.replace(/\D/g, '').slice(0, 11) : text.toLowerCase().slice(0, 128);
};

const contactTypeOf = (kind: ContactKind) => (kind === 'mobile' ? 'MOBILE' : 'EMAIL');

export const RegistrationPanel = ({
  loginCapabilities,
  securitySettings,
  initialMobile,
  onOpenAgreementPreview,
  onSubmit,
  onBackToLogin,
}: RegistrationPanelProps) => {
  const [form] = Form.useForm<RegistrationFormValues>();
  const [availability, setAvailability] = useState<Record<ContactKind, AvailabilityStatus>>({ mobile: 'idle', email: 'idle' });
  const [challenges, setChallenges] = useState<Partial<Record<ContactKind, LoginCodeChallenge | null>>>({});
  const [sending, setSending] = useState<ContactKind | null>(null);
  const [cooldownEndsAt, setCooldownEndsAt] = useState<Partial<Record<ContactKind, number>>>({});
  const [clock, setClock] = useState(() => Date.now());
  const [captchaChallenge, setCaptchaChallenge] = useState<CaptchaChallenge | null>(null);
  const [captchaLoading, setCaptchaLoading] = useState(false);
  const [captchaImageLoadFailed, setCaptchaImageLoadFailed] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const availabilitySequence = useRef<Record<ContactKind, number>>({ mobile: 0, email: 0 });
  const captchaSequence = useRef(0);

  const smsRequired = Boolean(loginCapabilities.registrationSmsVerificationRequired);
  const emailRequired = Boolean(loginCapabilities.registrationEmailVerificationRequired);

  useEffect(() => {
    if (!initialMobile) {
      return;
    }
    form.setFieldValue('mobile', normalizeContact('mobile', initialMobile));
    setAvailability((current) => ({ ...current, mobile: 'idle' }));
  }, [form, initialMobile]);

  useEffect(() => {
    const active = Object.values(cooldownEndsAt).some((end) => (end || 0) > clock);
    if (!active) {
      return;
    }
    const timer = window.setInterval(() => setClock(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [clock, cooldownEndsAt]);

  const cooldownSeconds = useMemo(
    () => ({
      mobile: Math.max(0, Math.ceil(((cooldownEndsAt.mobile || 0) - clock) / 1000)),
      email: Math.max(0, Math.ceil(((cooldownEndsAt.email || 0) - clock) / 1000)),
    }),
    [clock, cooldownEndsAt.email, cooldownEndsAt.mobile],
  );

  const refreshCaptcha = useCallback(async () => {
    const sequence = ++captchaSequence.current;
    setCaptchaLoading(true);
    setCaptchaImageLoadFailed(false);
    form.setFieldValue('captchaCode', undefined);
    try {
      const challenge = await request<CaptchaChallenge>('/v1/public/captcha/challenge', {
        method: 'GET',
        params: { captchaType: 'IMAGE' },
        skipAuth: true,
        silent: true,
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
      });
      if (sequence === captchaSequence.current) {
        setCaptchaChallenge(challenge);
      }
    } catch {
      if (sequence === captchaSequence.current) {
        setCaptchaChallenge(null);
        setCaptchaImageLoadFailed(true);
        message.warning(formatMessage({ id: 'page.login.error.refreshCaptcha', defaultMessage: '图形验证码加载失败，请点击重试' }));
      }
    } finally {
      if (sequence === captchaSequence.current) {
        setCaptchaLoading(false);
      }
    }
  }, [form]);

  useEffect(() => {
    void refreshCaptcha();
    return () => {
      captchaSequence.current += 1;
    };
  }, [refreshCaptcha]);

  const invalidateContact = useCallback((kind: ContactKind) => {
    availabilitySequence.current[kind] += 1;
    setAvailability((current) => ({ ...current, [kind]: 'idle' }));
    setChallenges((current) => ({ ...current, [kind]: null }));
    setCooldownEndsAt((current) => ({ ...current, [kind]: 0 }));
    form.setFieldValue(kind === 'mobile' ? 'mobileVerificationCode' : 'emailVerificationCode', undefined);
  }, [form]);

  const checkAvailability = useCallback(async (kind: ContactKind) => {
    try {
      await form.validateFields([kind]);
    } catch {
      return false;
    }
    const contact = normalizeContact(kind, form.getFieldValue(kind));
    form.setFieldValue(kind, contact);
    const sequence = ++availabilitySequence.current[kind];
    setAvailability((current) => ({ ...current, [kind]: 'checking' }));
    try {
      const result = await request<RegistrationContactAvailability>('/v1/auth/registration/contact/availability', {
        method: 'POST',
        data: { contactType: contactTypeOf(kind), contact },
        skipAuth: true,
        silent: true,
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        allowDuplicate: true,
      });
      if (sequence !== availabilitySequence.current[kind] || normalizeContact(kind, form.getFieldValue(kind)) !== contact) {
        return false;
      }
      const nextStatus: AvailabilityStatus = result.available ? 'available' : 'unavailable';
      setAvailability((current) => ({ ...current, [kind]: nextStatus }));
      return result.available;
    } catch {
      if (sequence === availabilitySequence.current[kind]) {
        setAvailability((current) => ({ ...current, [kind]: 'error' }));
      }
      return false;
    }
  }, [form]);

  const sendCode = useCallback(async (kind: ContactKind) => {
    if (cooldownSeconds[kind] > 0) {
      return;
    }
    const available = availability[kind] === 'available' || await checkAvailability(kind);
    if (!available) {
      return;
    }
    const contact = normalizeContact(kind, form.getFieldValue(kind));
    setSending(kind);
    try {
      const challenge = await request<LoginCodeChallenge>('/v1/auth/registration/code/challenge', {
        method: 'POST',
        data: { contactType: contactTypeOf(kind), contact },
        skipAuth: true,
        silent: true,
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
      });
      setChallenges((current) => ({ ...current, [kind]: challenge }));
      const seconds = Math.max(1, Math.floor(challenge.cooldownSeconds || securitySettings.verificationCodeCooldownSeconds));
      setCooldownEndsAt((current) => ({ ...current, [kind]: Date.now() + seconds * 1000 }));
      setClock(Date.now());
      form.setFieldValue(kind === 'mobile' ? 'mobileVerificationCode' : 'emailVerificationCode', undefined);
      message.success(formatMessage({ id: 'page.login.success.codeSent', defaultMessage: '验证码已发送' }));
    } catch (error) {
      message.error(error instanceof Error ? error.message : formatMessage({ id: 'page.login.error.codeSendFailed', defaultMessage: '验证码发送失败，请稍后重试' }));
    } finally {
      setSending(null);
    }
  }, [availability, checkAvailability, cooldownSeconds, form, securitySettings.verificationCodeCooldownSeconds]);

  const availabilityHelp = (kind: ContactKind) => {
    const status = availability[kind];
    if (status === 'checking') {
      return formatMessage({
        id: kind === 'mobile' ? 'page.login.registration.mobileChecking' : 'page.login.registration.emailChecking',
        defaultMessage: kind === 'mobile' ? '正在校验手机号…' : '正在校验邮箱…',
      });
    }
    if (status === 'available') {
      return <span className="saas-registration__available"><CheckCircleOutlined /> {formatMessage({ id: 'page.login.registration.contactAvailable', defaultMessage: '可以注册' })}</span>;
    }
    if (status === 'unavailable') {
      return <span>{formatMessage({
        id: kind === 'mobile' ? 'page.login.registration.mobileExists' : 'page.login.registration.emailExists',
        defaultMessage: kind === 'mobile' ? '该手机号已注册' : '该邮箱已注册',
      })}<Button type="link" size="small" onClick={onBackToLogin}>{formatMessage({ id: 'page.login.registration.goToLogin', defaultMessage: '去登录' })}</Button></span>;
    }
    if (status === 'error') {
      return <span>{formatMessage({ id: 'page.login.registration.contactCheckFailed', defaultMessage: '暂时无法校验，请重试' })}<Button type="link" size="small" onClick={() => void checkAvailability(kind)}>{formatMessage({ id: 'common.retry', defaultMessage: '重试' })}</Button></span>;
    }
    return undefined;
  };

  const passwordRules = [
    { required: true, message: formatMessage({ id: 'page.login.error.pleaseEnterPassword', defaultMessage: '请输入密码' }) },
    {
      validator: async (_: unknown, value?: string) => {
        const password = value || '';
        if (!password) return;
        if (password.length < securitySettings.passwordMinLength) throw new Error(formatMessage({ id: 'page.login.registration.passwordMinLength', defaultMessage: '密码长度不能少于 {minLength} 位' }, { minLength: securitySettings.passwordMinLength }));
        if (securitySettings.passwordRequireUppercase && !/[A-Z]/.test(password)) throw new Error(formatMessage({ id: 'page.login.registration.passwordUppercaseRequired', defaultMessage: '密码必须包含大写字母' }));
        if (securitySettings.passwordRequireLowercase && !/[a-z]/.test(password)) throw new Error(formatMessage({ id: 'page.login.registration.passwordLowercaseRequired', defaultMessage: '密码必须包含小写字母' }));
        if (securitySettings.passwordRequireSpecialCharacter && !/[^A-Za-z0-9]/.test(password)) throw new Error(formatMessage({ id: 'page.login.registration.passwordSpecialRequired', defaultMessage: '密码必须包含特殊字符' }));
        if (!securitySettings.passwordAllowConsecutiveCharacters && containsConsecutiveCharacters(password)) throw new Error(formatMessage({ id: 'page.login.registration.passwordConsecutiveForbidden', defaultMessage: '密码不能包含连续字符' }));
      },
    },
  ];

  const handleFinish = async (values: RegistrationFormValues) => {
    const [mobileAvailable, emailAvailable] = await Promise.all([
      availability.mobile === 'available' ? Promise.resolve(true) : checkAvailability('mobile'),
      availability.email === 'available' ? Promise.resolve(true) : checkAvailability('email'),
    ]);
    if (!mobileAvailable || !emailAvailable || !captchaChallenge?.captchaId) {
      if (!captchaChallenge?.captchaId) {
        message.warning(formatMessage({ id: 'page.login.error.refreshCaptcha', defaultMessage: '请先刷新图形验证码' }));
      }
      return;
    }
    setSubmitting(true);
    try {
      await onSubmit({
        mobile: normalizeContact('mobile', values.mobile),
        email: normalizeContact('email', values.email),
        password: values.password,
        captchaId: captchaChallenge.captchaId,
        captchaCode: values.captchaCode,
        mobileChallengeId: challenges.mobile?.challengeId,
        mobileVerificationCode: values.mobileVerificationCode,
        emailChallengeId: challenges.email?.challengeId,
        emailVerificationCode: values.emailVerificationCode,
      });
    } catch (error) {
      message.error(error instanceof Error ? error.message : formatMessage({ id: 'page.login.registration.failed', defaultMessage: '注册失败，请重试' }));
      await refreshCaptcha();
    } finally {
      setSubmitting(false);
    }
  };

  const contactStatus = (kind: ContactKind) => {
    if (availability[kind] === 'available') return 'success';
    if (availability[kind] === 'unavailable' || availability[kind] === 'error') return 'error';
    if (availability[kind] === 'checking') return 'validating';
    return undefined;
  };

  const passwordRequirements = [
    formatMessage({ id: 'page.login.registration.passwordHintMinLength', defaultMessage: '至少 {minLength} 位' }, { minLength: securitySettings.passwordMinLength }),
    securitySettings.passwordRequireUppercase ? formatMessage({ id: 'page.login.registration.passwordHintUppercase', defaultMessage: '包含大写字母' }) : null,
    securitySettings.passwordRequireLowercase ? formatMessage({ id: 'page.login.registration.passwordHintLowercase', defaultMessage: '包含小写字母' }) : null,
    securitySettings.passwordRequireSpecialCharacter ? formatMessage({ id: 'page.login.registration.passwordHintSpecial', defaultMessage: '包含特殊字符' }) : null,
    !securitySettings.passwordAllowConsecutiveCharacters ? formatMessage({ id: 'page.login.registration.passwordHintNoConsecutive', defaultMessage: '不能包含连续字符' }) : null,
  ].filter(Boolean).join(formatMessage({ id: 'page.login.registration.requirementSeparator', defaultMessage: '，' }));

  return (
    <Form<RegistrationFormValues>
      form={form}
      className="saas-login-page__form saas-registration"
      onFinish={(values) => void handleFinish(values)}
      autoComplete="off"
      data-testid="registration-form"
    >
      <div className="saas-registration__fields">
        <Form.Item
          name="mobile"
          validateStatus={contactStatus('mobile')}
          hasFeedback={availability.mobile !== 'idle'}
          help={availabilityHelp('mobile')}
          rules={[
            { required: true, message: formatMessage({ id: 'page.login.error.pleaseEnterMobile', defaultMessage: '请输入手机号' }) },
            { pattern: MOBILE_PATTERN, message: formatMessage({ id: 'page.login.error.invalidMobile', defaultMessage: '请输入有效手机号' }) },
          ]}
          getValueFromEvent={(event) => normalizeContact('mobile', event?.target?.value)}
        >
          <Input
            size="large"
            prefix={<MobileOutlined className="saas-login-page__field-icon" />}
            inputMode="numeric"
            autoComplete="tel"
            maxLength={11}
            placeholder={formatMessage({ id: 'page.login.error.pleaseEnterMobile', defaultMessage: '请输入手机号' })}
            data-testid="registration-mobile-input"
            onChange={() => invalidateContact('mobile')}
            onBlur={() => void checkAvailability('mobile')}
          />
        </Form.Item>

        {smsRequired ? (
          <div className="saas-login-page__code-row">
            <Form.Item name="mobileVerificationCode" className="saas-login-page__code-input" rules={[{ required: true, message: formatMessage({ id: 'page.login.registration.mobileCodeRequired', defaultMessage: '请输入手机验证码' }) }]}>
              <Input size="large" prefix={<SafetyCertificateOutlined className="saas-login-page__field-icon" />} inputMode="numeric" maxLength={12} autoComplete="one-time-code" placeholder={formatMessage({ id: 'page.login.registration.mobileCodeRequired', defaultMessage: '请输入手机验证码' })} />
            </Form.Item>
            <Button className="saas-login-page__send-code-button" loading={sending === 'mobile'} disabled={availability.mobile !== 'available' || cooldownSeconds.mobile > 0} onClick={() => void sendCode('mobile')}>
              {cooldownSeconds.mobile > 0 ? `${cooldownSeconds.mobile}s` : formatMessage({ id: 'page.login.sendCode', defaultMessage: '发送验证码' })}
            </Button>
          </div>
        ) : null}

        <Form.Item
          name="email"
          validateStatus={contactStatus('email')}
          hasFeedback={availability.email !== 'idle'}
          help={availabilityHelp('email')}
          rules={[
            { required: true, message: formatMessage({ id: 'page.login.error.pleaseEnterEmail', defaultMessage: '请输入邮箱' }) },
            { pattern: EMAIL_PATTERN, message: formatMessage({ id: 'page.login.error.invalidEmail', defaultMessage: '请输入有效邮箱地址' }) },
          ]}
          getValueFromEvent={(event) => normalizeContact('email', event?.target?.value)}
        >
          <Input
            size="large"
            prefix={<MailOutlined className="saas-login-page__field-icon" />}
            inputMode="email"
            autoComplete="email"
            maxLength={128}
            placeholder={formatMessage({ id: 'page.login.error.pleaseEnterEmail', defaultMessage: '请输入邮箱' })}
            data-testid="registration-email-input"
            onChange={() => invalidateContact('email')}
            onBlur={() => void checkAvailability('email')}
          />
        </Form.Item>

        {emailRequired ? (
          <div className="saas-login-page__code-row">
            <Form.Item name="emailVerificationCode" className="saas-login-page__code-input" rules={[{ required: true, message: formatMessage({ id: 'page.login.registration.emailCodeRequired', defaultMessage: '请输入邮箱验证码' }) }]}>
              <Input size="large" prefix={<SafetyCertificateOutlined className="saas-login-page__field-icon" />} inputMode="numeric" maxLength={12} autoComplete="one-time-code" placeholder={formatMessage({ id: 'page.login.registration.emailCodeRequired', defaultMessage: '请输入邮箱验证码' })} />
            </Form.Item>
            <Button className="saas-login-page__send-code-button" loading={sending === 'email'} disabled={availability.email !== 'available' || cooldownSeconds.email > 0} onClick={() => void sendCode('email')}>
              {cooldownSeconds.email > 0 ? `${cooldownSeconds.email}s` : formatMessage({ id: 'page.login.sendCode', defaultMessage: '发送验证码' })}
            </Button>
          </div>
        ) : null}

        <Form.Item name="password" rules={passwordRules}>
          <Input.Password size="large" prefix={<LockOutlined className="saas-login-page__field-icon" />} autoComplete="new-password" maxLength={128} placeholder={formatMessage({ id: 'page.login.registration.passwordPlaceholder', defaultMessage: '设置密码' })} data-testid="registration-password-input" />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          dependencies={['password']}
          rules={[
            { required: true, message: formatMessage({ id: 'page.login.registration.confirmPasswordRequired', defaultMessage: '请再次输入密码' }) },
            ({ getFieldValue }) => ({
              validator: async (_, value) => {
                if (!value || getFieldValue('password') === value) return;
                throw new Error(formatMessage({ id: 'page.login.registration.passwordMismatch', defaultMessage: '两次输入的密码不一致' }));
              },
            }),
          ]}
        >
          <Input.Password size="large" prefix={<LockOutlined className="saas-login-page__field-icon" />} autoComplete="new-password" maxLength={128} placeholder={formatMessage({ id: 'page.login.registration.confirmPasswordPlaceholder', defaultMessage: '再次输入密码' })} data-testid="registration-confirm-password-input" />
        </Form.Item>

        <PasswordLoginImageCaptcha
          captchaChallenge={captchaChallenge}
          captchaLoading={captchaLoading}
          captchaImageLoadFailed={captchaImageLoadFailed}
          onRefreshCaptcha={() => void refreshCaptcha()}
          onCaptchaImageError={() => setCaptchaImageLoadFailed(true)}
        />
      </div>

      <Typography.Paragraph className="saas-registration__password-hint" type="secondary">
        {formatMessage({ id: 'page.login.registration.passwordHint', defaultMessage: '密码要求：{requirements}' }, { requirements: passwordRequirements })}
      </Typography.Paragraph>

      <Button
        block
        size="large"
        type="primary"
        htmlType="submit"
        loading={submitting}
        disabled={availability.mobile !== 'available' || availability.email !== 'available'}
        className="saas-login-page__submit-button"
        data-testid="registration-submit-button"
      >
        {formatMessage({ id: 'page.login.registerAndLogin', defaultMessage: '注册并登录' })}
      </Button>
      <Button className="saas-registration__back-button" type="link" block onClick={onBackToLogin} disabled={submitting} data-testid="auth-entry-back-to-login">
        {formatMessage({ id: 'page.login.backToLogin', defaultMessage: '返回登录' })}
      </Button>
      <div className="saas-login-page__agreement saas-registration__agreement">
        {formatMessage({ id: 'page.login.registration.agreementPrefix', defaultMessage: '注册即代表同意' })}
        <Button type="link" size="small" onClick={() => onOpenAgreementPreview('user')}>{formatMessage({ id: 'page.login.agreement.userPlain', defaultMessage: '用户协议' })}</Button>
        {formatMessage({ id: 'page.login.agreement.and', defaultMessage: '和' })}
        <Button type="link" size="small" onClick={() => onOpenAgreementPreview('privacy')}>{formatMessage({ id: 'page.login.agreement.privacyGuide', defaultMessage: '隐私协议' })}</Button>
      </div>
    </Form>
  );
};
