import { LoginFormPage } from '@ant-design/pro-components';
import { formatMessage, history, useLocation } from '@umijs/max';
import { Form, message, type FormProps } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { flushSync } from 'react-dom';
import { DEFAULT_AGREEMENT_SETTINGS, normalizeAgreementSettings } from '@/agreement/settings';
import { DEFAULT_BRANDING_SETTINGS, normalizeBrandingSettings, persistBrandingSettings } from '@/branding/settings';
import { isLoggedIn } from '@/auth/session';
import { loadCaptchaChallenge } from '@/auth/captcha';
import { createCaptchaRefreshController } from '@/auth/captchaRefreshController';
import { beginLoginFlow, endLoginFlow } from '@/auth/loginFlowState';
import { encryptLoginPassword } from '@/auth/loginEncryption';
import { isPasskeySupported, toAuthenticationPayload, toPublicKeyRequestOptions } from '@/auth/passkey';
import { initializeAfterLogin } from '@/auth/session';
import { DEFAULT_SECURITY_SETTINGS, normalizeSecuritySettings, persistSecuritySettings } from '@/auth/securitySettings';
import { createLoginStorageHandler, resolveAuthorizedLoginRedirectTarget, resolveLoginRedirectTarget } from '@/auth/loginRedirect';
import { authService } from '@/services/auth';
import { pluginService } from '@/services/plugin';
import { systemService } from '@/services/system';
import { ApiRequestError } from '@/services/common/request';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { DEFAULT_WATERMARK_SETTINGS, persistWatermarkSettings } from '@/watermark/settings';
import { LoginFormFields, type LoginFormValues, type LoginMode } from '@/pages/user/login/components/LoginFormFields';
import { AgreementPreviewModal } from '@/pages/user/login/components/AgreementPreviewModal';
import { resolveLoginErrorFeedback } from '@/pages/user/login/loginErrorFeedback';
import type {
  AgreementSettings,
  CaptchaChallenge,
  LoginCapabilities,
  LoginCodeChallenge,
  LoginEncryptionKey,
  LoginResponse,
  SecuritySettings,
} from '@/types/api';
import './Login.css';

const DEFAULT_LOGIN_CAPABILITIES: LoginCapabilities = {
  passwordLoginAvailable: true,
  smsLoginAvailable: false,
  emailLoginAvailable: false,
  wechatLoginAvailable: false,
  passkeyLoginAvailable: false,
  passkeyPasswordlessAvailable: false,
};

const getAvailableLoginModes = (capabilities: LoginCapabilities): LoginMode[] => {
  const modes: LoginMode[] = ['password'];
  if (capabilities.smsLoginAvailable) {
    modes.push('sms');
  }
  if (capabilities.emailLoginAvailable) {
    modes.push('email');
  }
  return modes;
};

const defaultLoginMode = (capabilities: LoginCapabilities): LoginMode => {
  if (capabilities.smsLoginAvailable) {
    return 'sms';
  }
  if (capabilities.emailLoginAvailable) {
    return 'email';
  }
  return 'password';
};

const Login = () => {
  const [submitting, setSubmitting] = useState(false);
  const [passkeySubmitting, setPasskeySubmitting] = useState(false);
  const [sendingLoginType, setSendingLoginType] = useState<Exclude<LoginMode, 'password'> | null>(null);
  const [pendingSecondFactorLogin, setPendingSecondFactorLogin] = useState<LoginResponse | null>(null);
  const [activeLoginMode, setActiveLoginMode] = useState<LoginMode>('password');
  const [loginCodeChallenges, setLoginCodeChallenges] = useState<Partial<Record<Exclude<LoginMode, 'password'>, LoginCodeChallenge | null>>>({});
  const [agreementPreviewOpen, setAgreementPreviewOpen] = useState(false);
  const [agreementPreviewKind, setAgreementPreviewKind] = useState<'user' | 'privacy'>('user');
  const [loginForm] = Form.useForm<LoginFormValues>();
  const { initialState, setInitialState } = useInitialStateModel();
  const [securitySettings, setSecuritySettings] = useState<SecuritySettings>(
    normalizeSecuritySettings(initialState?.securitySettings || DEFAULT_SECURITY_SETTINGS),
  );
  const [captchaChallenge, setCaptchaChallenge] = useState<CaptchaChallenge | null>(null);
  const [captchaLoading, setCaptchaLoading] = useState(false);
  const [captchaImageLoadFailed, setCaptchaImageLoadFailed] = useState(false);
  const [loginEncryptionKey, setLoginEncryptionKey] = useState<LoginEncryptionKey | null>(null);
  const [loginEncryptionLoading, setLoginEncryptionLoading] = useState(false);
  const location = useLocation();
  const brandingSettings = normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS);
  const loginPageStyle = useMemo(
    () =>
      ({
        '--saas-login-background-image': brandingSettings.loginBackgroundUrl
          ? `url("${brandingSettings.loginBackgroundUrl.replace(/"/g, '\\"')}")`
          : undefined,
      }) as CSSProperties,
    [brandingSettings.loginBackgroundUrl],
  );
  const agreementSettings = normalizeAgreementSettings(initialState?.agreementSettings || DEFAULT_AGREEMENT_SETTINGS);
  const loginCapabilities = initialState?.loginCapabilities || DEFAULT_LOGIN_CAPABILITIES;
  const availableLoginModes = useMemo(() => getAvailableLoginModes(loginCapabilities), [loginCapabilities]);
  const redirectTarget = resolveLoginRedirectTarget(location.search);
  const securitySettingsRef = useRef(securitySettings);
  const loginEncryptionLoadPromiseRef = useRef<Promise<LoginEncryptionKey | null> | null>(null);
  const wechatCallbackHandledRef = useRef(false);
  const captchaRefreshControllerRef = useRef(
    createCaptchaRefreshController({
      getCaptchaEnabled: () => securitySettingsRef.current.captchaEnabled,
      getCaptchaType: () => securitySettingsRef.current.captchaType,
      loadChallenge: (captchaType) =>
        loadCaptchaChallenge(captchaType, {
          autoRedirectOnUnauthorized: false,
          silent: true,
          skipAuth: true,
        }),
      setCaptchaChallenge,
      setCaptchaLoading,
      setCaptchaImageLoadFailed,
      onRefreshFailure: () => message.warning(formatMessage({ id: 'page.login.error.refreshCaptcha', defaultMessage: 'Captcha refresh failed, please try again later' })),
    }),
  );

  useEffect(() => {
    const normalizedSecuritySettings = normalizeSecuritySettings(initialState?.securitySettings || DEFAULT_SECURITY_SETTINGS);
    persistSecuritySettings(normalizedSecuritySettings);
    setSecuritySettings(normalizedSecuritySettings);
  }, [initialState?.securitySettings]);

  useEffect(() => {
    securitySettingsRef.current = securitySettings;
  }, [securitySettings]);

  useEffect(() => {
    setActiveLoginMode((current) => (availableLoginModes.includes(current) ? current : defaultLoginMode(loginCapabilities)));
  }, [availableLoginModes, loginCapabilities]);

  useEffect(() => {
    let disposed = false;
    void systemService
      .publicLoginCapabilities({
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        silent: true,
      })
      .then((capabilities) => {
        if (disposed) {
          return;
        }
        setInitialState((prev: AppInitialState | undefined) =>
          prev
            ? {
                ...prev,
                loginCapabilities: {
                  ...DEFAULT_LOGIN_CAPABILITIES,
                  ...capabilities,
                },
              }
            : prev,
        );
      })
      .catch(() => {
        // Keep the bootstrap snapshot values when the public capability endpoint is temporarily unavailable.
      });

    return () => {
      disposed = true;
    };
  }, [setInitialState]);

  const loadLoginEncryptionKey = useCallback(async () => {
    if (loginEncryptionKey) {
      return loginEncryptionKey;
    }

    if (!loginEncryptionLoadPromiseRef.current) {
      setLoginEncryptionLoading(true);
      loginEncryptionLoadPromiseRef.current = authService
        .loginEncryptionKey({
          autoRedirectOnUnauthorized: false,
          allowUnauthorizedWithoutRedirect: true,
          silent: true,
        })
        .then((key) => {
          setLoginEncryptionKey(key);
          return key;
        })
      .catch((error) => {
        message.error(error instanceof Error ? error.message : formatMessage({ id: 'page.login.error.loginEncryption', defaultMessage: 'Failed to load login encryption info, please refresh and try again' }));
          return null;
        })
        .finally(() => {
          setLoginEncryptionLoading(false);
          loginEncryptionLoadPromiseRef.current = null;
        });
    }

    return loginEncryptionLoadPromiseRef.current;
  }, [loginEncryptionKey]);

  const refreshCaptcha = useCallback(async () => captchaRefreshControllerRef.current.refresh(), []);
  const pendingSecondFactorOptions = pendingSecondFactorLogin?.secondFactorOptions || [];
  const pendingSecondFactorOption = pendingSecondFactorOptions[0] || null;
  const pendingSecondFactorPrompt =
    pendingSecondFactorOption?.promptMessage ||
    (pendingSecondFactorOption?.factorName
      ? formatMessage(
          { id: 'page.login.secondFactor.prompt', defaultMessage: '{name} requires second-factor verification' },
          { name: pendingSecondFactorOption.factorName },
        )
      : formatMessage({ id: 'page.login.code.secondFactor', defaultMessage: 'Please enter the verification code to complete second-factor verification' }));

  const resetSecondFactorFlow = useCallback(() => {
    setPendingSecondFactorLogin(null);
    loginForm.setFieldsValue({ verificationCode: undefined });
  }, [loginForm]);

  const resetCodeFlow = useCallback((mode: Exclude<LoginMode, 'password'>) => {
    setLoginCodeChallenges((prev) => ({
      ...prev,
      [mode]: null,
    }));
    loginForm.setFieldsValue({
      [mode === 'sms' ? 'smsVerificationCode' : 'emailVerificationCode']: undefined,
    } as Partial<LoginFormValues>);
  }, [loginForm]);

  useEffect(() => {
    void loadLoginEncryptionKey();
  }, [loadLoginEncryptionKey]);

  useEffect(() => {
    if (!securitySettings.captchaEnabled) {
      setCaptchaChallenge(null);
      setCaptchaImageLoadFailed(false);
      setCaptchaLoading(false);
      captchaRefreshControllerRef.current.invalidate();
      return;
    }

    if (securitySettings.captchaType === 'SLIDER') {
      captchaRefreshControllerRef.current.invalidate();
      setCaptchaImageLoadFailed(false);
      setCaptchaLoading(false);
      return;
    }

    if (!captchaChallenge?.captchaId || captchaChallenge.captchaType !== securitySettings.captchaType) {
      void refreshCaptcha();
    }
  }, [captchaChallenge?.captchaId, captchaChallenge?.captchaType, refreshCaptcha, securitySettings.captchaEnabled, securitySettings.captchaType]);

  useEffect(() => {
    if (securitySettings.captchaEnabled) {
      loginForm.setFieldValue('captchaCode', undefined);
      loginForm.setFieldValue('captchaProof', undefined);
    }
  }, [captchaChallenge?.captchaId, loginForm, securitySettings.captchaEnabled]);

  useEffect(() => {
    const alreadyAuthenticated = isLoggedIn() || Boolean(initialState?.currentUser?.sessionId);
    if (!alreadyAuthenticated || submitting) {
      return;
    }

    if (initialState?.currentUser) {
      history.replace(resolveAuthorizedLoginRedirectTarget(location.search, initialState.currentUser, initialState.menuTree));
    } else {
      history.replace(redirectTarget);
    }
  }, [initialState?.currentUser, initialState?.menuTree, location.search, redirectTarget, submitting]);

  useEffect(() => {
    const handleStorage = createLoginStorageHandler(redirectTarget, (target) => {
      window.location.replace(target);
    });

    window.addEventListener('storage', handleStorage);
    return () => window.removeEventListener('storage', handleStorage);
  }, [redirectTarget]);

  const openAgreementPreview = useCallback((kind: 'user' | 'privacy') => {
    setAgreementPreviewKind(kind);
    setAgreementPreviewOpen(true);
  }, []);

  const handleSendLoginCode = useCallback(
    async (mode: Exclude<LoginMode, 'password'>) => {
      if (!availableLoginModes.includes(mode)) {
        message.warning(
          mode === 'sms'
            ? formatMessage({ id: 'page.login.error.smsDisabled', defaultMessage: 'SMS login is not enabled' })
            : formatMessage({ id: 'page.login.error.emailDisabled', defaultMessage: 'Email login is not enabled' }),
        );
        return;
      }
      const accountField = mode === 'sms' ? 'smsAccount' : 'emailAccount';
      const account = loginForm.getFieldValue(accountField);
      if (!account) {
        message.warning(
          mode === 'sms'
            ? formatMessage({ id: 'page.login.error.pleaseEnterMobile', defaultMessage: 'Please enter your mobile number' })
            : formatMessage({ id: 'page.login.error.pleaseEnterEmail', defaultMessage: 'Please enter your email' }),
        );
        return;
      }

      setSendingLoginType(mode);
      try {
        const challenge = await authService.loginCodeChallenge(
          {
            loginType: mode,
            account,
          },
          {
            autoRedirectOnUnauthorized: false,
            silent: true,
          },
        );
        setLoginCodeChallenges((prev) => ({
          ...prev,
          [mode]: challenge,
        }));
        loginForm.setFieldsValue({
          [mode === 'sms' ? 'smsVerificationCode' : 'emailVerificationCode']: undefined,
        } as Partial<LoginFormValues>);
        message.success(formatMessage({ id: 'page.login.success.codeSent', defaultMessage: 'Verification code sent' }));
        if (challenge.debugCode) {
          message.info(formatMessage({ id: 'page.login.code.debug', defaultMessage: 'Debug code: {code}' }, { code: challenge.debugCode }));
        }
      } catch (error) {
        message.error(error instanceof Error ? error.message : formatMessage({ id: 'page.login.error.codeSendFailed', defaultMessage: 'Failed to send the verification code, please try again later' }));
      } finally {
        setSendingLoginType(null);
      }
    },
    [availableLoginModes, loginForm],
  );

  const completeSuccessfulLogin = useCallback(
    async (loginResponse: LoginResponse) => {
      const sessionResult = await initializeAfterLogin(loginResponse);
      const [menuResult, pluginResult, brandingResult, watermarkResult] = await Promise.allSettled([
        pluginService.currentMenus({
          autoRedirectOnUnauthorized: false,
          allowUnauthorizedWithoutRedirect: true,
          silent: true,
        }),
        pluginService.currentAvailable({
          autoRedirectOnUnauthorized: false,
          allowUnauthorizedWithoutRedirect: true,
          silent: true,
        }),
        systemService.brandingSettings({
          autoRedirectOnUnauthorized: false,
          allowUnauthorizedWithoutRedirect: true,
          silent: true,
        }),
        systemService.watermarkSettings({
          autoRedirectOnUnauthorized: false,
          allowUnauthorizedWithoutRedirect: true,
          silent: true,
        }),
      ]);
      const menuTree = menuResult.status === 'fulfilled' ? menuResult.value : [];
      const availablePlugins = pluginResult.status === 'fulfilled' ? pluginResult.value : [];
      const normalizedBrandingSettings = normalizeBrandingSettings(
        brandingResult.status === 'fulfilled'
          ? brandingResult.value
          : initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS,
      );
      const watermarkSettings = watermarkResult.status === 'fulfilled' ? watermarkResult.value : initialState?.watermarkSettings || DEFAULT_WATERMARK_SETTINGS;
      persistBrandingSettings(normalizedBrandingSettings);
      persistWatermarkSettings(watermarkSettings);
      flushSync(() => {
        setInitialState((prev: AppInitialState | undefined) => ({
          ...prev,
          currentUser: sessionResult.currentUser,
          menuTree,
          menuVersion: (prev?.menuVersion ?? 0) + 1,
          availablePlugins,
          securitySettings: sessionResult.securitySettings,
          brandingSettings: normalizedBrandingSettings,
          watermarkSettings,
          agreementSettings: prev?.agreementSettings || agreementSettings,
          loginCapabilities: prev?.loginCapabilities || loginCapabilities,
        }));
      });

      setLoginCodeChallenges({});
      history.replace(resolveAuthorizedLoginRedirectTarget(location.search, sessionResult.currentUser, menuTree));
    },
    [agreementSettings, initialState?.brandingSettings, initialState?.watermarkSettings, location.search, loginCapabilities, setInitialState],
  );

  const handleWechatLogin = useCallback(async () => {
    if (agreementSettings.userAgreementMarkdown || agreementSettings.privacyAgreementMarkdown) {
      const accepted = loginForm.getFieldValue('agreementAccepted');
      if (!accepted) {
        message.warning(formatMessage({ id: 'page.login.agreement.required', defaultMessage: 'Please agree to the terms before logging in' }));
        return;
      }
    }

    setSubmitting(true);
    try {
      message.loading({
        content: formatMessage({ id: 'page.login.wechatStarting', defaultMessage: 'Redirecting to WeChat login...' }),
        key: 'wechat-login',
        duration: 1,
      });
      const result = await authService.wechatAuthorizeUrl({
        autoRedirectOnUnauthorized: false,
        silent: true,
      });
      window.location.assign(result.authorizeUrl);
    } catch (error) {
      message.error(error instanceof Error ? error.message : formatMessage({ id: 'page.login.error.loginFailed', defaultMessage: 'Login failed, please try again later' }));
    } finally {
      setSubmitting(false);
    }
  }, [agreementSettings.privacyAgreementMarkdown, agreementSettings.userAgreementMarkdown, loginForm]);

  const handlePasskeyLogin = useCallback(async () => {
    if (!isPasskeySupported()) {
      message.warning(formatMessage({ id: 'page.login.passkey.unsupported', defaultMessage: '当前浏览器不支持通行密钥' }));
      return;
    }
    if (agreementSettings.userAgreementMarkdown || agreementSettings.privacyAgreementMarkdown) {
      const accepted = loginForm.getFieldValue('agreementAccepted');
      if (!accepted) {
        message.warning(formatMessage({ id: 'page.login.agreement.required', defaultMessage: 'Please agree to the terms before logging in' }));
        return;
      }
    }

    setPasskeySubmitting(true);
    try {
      const options = await authService.passkeyAuthenticationOptions({
        autoRedirectOnUnauthorized: false,
        silent: true,
      });
      const credential = await navigator.credentials.get({
        publicKey: toPublicKeyRequestOptions(options),
      });
      if (!credential) {
        return;
      }
      const loginResponse = await authService.passkeyAuthenticationComplete(
        toAuthenticationPayload(options.challengeId, credential as PublicKeyCredential),
        {
          autoRedirectOnUnauthorized: false,
          silent: true,
        },
      );
      await completeSuccessfulLogin(loginResponse);
    } catch (error) {
      if (error instanceof DOMException && error.name === 'NotAllowedError') {
        message.info(formatMessage({ id: 'page.login.passkey.cancelled', defaultMessage: '已取消通行密钥验证' }));
        return;
      }
      message.error(error instanceof Error ? error.message : formatMessage({ id: 'page.login.error.loginFailed', defaultMessage: 'Login failed, please try again later' }));
    } finally {
      setPasskeySubmitting(false);
    }
  }, [agreementSettings.privacyAgreementMarkdown, agreementSettings.userAgreementMarkdown, completeSuccessfulLogin, loginForm]);

  useEffect(() => {
    if (wechatCallbackHandledRef.current) {
      return;
    }
    const searchParams = new URLSearchParams(location.search || '');
    const code = searchParams.get('code');
    const state = searchParams.get('state');
    if (!code || !state || !loginCapabilities.wechatLoginAvailable) {
      return;
    }

    wechatCallbackHandledRef.current = true;
    setSubmitting(true);
    beginLoginFlow();
    void authService
      .wechatLogin(
        { code, state },
        {
          autoRedirectOnUnauthorized: false,
          silent: true,
        },
      )
      .then(async (loginResponse) => {
        if (loginResponse.requiresSecondFactor) {
          setPendingSecondFactorLogin(loginResponse);
          message.info(loginResponse.secondFactorOptions?.[0]?.promptMessage || formatMessage({ id: 'page.login.code.secondFactor', defaultMessage: 'Please enter the verification code to complete second-factor verification' }));
          history.replace(location.pathname);
          return;
        }
        await completeSuccessfulLogin(loginResponse);
      })
      .catch((error) => {
        message.error(error instanceof Error ? error.message : formatMessage({ id: 'page.login.error.loginFailed', defaultMessage: 'Login failed, please try again later' }));
        history.replace(location.pathname);
      })
      .finally(() => {
        endLoginFlow();
        setSubmitting(false);
      });
  }, [completeSuccessfulLogin, location.pathname, location.search, loginCapabilities.wechatLoginAvailable]);

  const handleSubmit = async (values: LoginFormValues): Promise<boolean> => {
    if (!pendingSecondFactorLogin) {
      if (!availableLoginModes.includes(activeLoginMode)) {
        message.warning(
          activeLoginMode === 'sms'
            ? formatMessage({ id: 'page.login.error.smsDisabled', defaultMessage: 'SMS login is not enabled' })
            : activeLoginMode === 'email'
              ? formatMessage({ id: 'page.login.error.emailDisabled', defaultMessage: 'Email login is not enabled' })
              : formatMessage({ id: 'page.login.error.loginModeUnavailable', defaultMessage: 'Current login mode is unavailable' }),
        );
        return false;
      }

      if (activeLoginMode === 'password' && securitySettings.captchaEnabled && !captchaChallenge?.captchaId) {
        message.warning(formatMessage({ id: 'page.login.error.captchaExpired', defaultMessage: 'The captcha has expired, please refresh and try again' }));
        return false;
      }

      if (activeLoginMode === 'password' && securitySettings.captchaEnabled && securitySettings.captchaType === 'IMAGE' && !values.captchaCode) {
        message.warning(formatMessage({ id: 'page.login.error.pleaseEnterCaptcha', defaultMessage: 'Please enter the captcha' }));
        return false;
      }

      if (activeLoginMode === 'password' && securitySettings.captchaEnabled && securitySettings.captchaType === 'SLIDER' && !values.captchaProof) {
        message.warning(formatMessage({ id: 'page.login.error.pleaseCompleteSliderCaptcha', defaultMessage: 'Please complete the slider captcha first' }));
        return false;
      }
    }

    setSubmitting(true);
    beginLoginFlow();
    try {
      const loginResponse = pendingSecondFactorLogin
        ? await authService.secondFactorComplete({
            factorCode: pendingSecondFactorOption?.factorCode || '',
            challengeId: pendingSecondFactorOption?.challengeId || '',
            verificationCode: values.verificationCode || '',
          })
        : activeLoginMode === 'password'
          ? await (async () => {
              const encryptionKey = loginEncryptionKey || (await loadLoginEncryptionKey());
              if (!encryptionKey) {
                return null;
              }

              return authService.login({
                account: values.passwordAccount,
                username: values.passwordAccount,
                password: await encryptLoginPassword(values.passwordPassword || '', encryptionKey),
                captchaId: securitySettings.captchaEnabled ? captchaChallenge?.captchaId : undefined,
                captchaCode: securitySettings.captchaEnabled && securitySettings.captchaType === 'IMAGE' ? values.captchaCode : undefined,
                captchaProof: securitySettings.captchaEnabled && securitySettings.captchaType === 'SLIDER' ? values.captchaProof : undefined,
              });
            })()
          : await (async () => {
              const mode = activeLoginMode as Exclude<LoginMode, 'password'>;
              const challenge = loginCodeChallenges[mode];
              if (!challenge?.challengeId) {
                message.warning(formatMessage({ id: 'page.login.error.pleaseSendCode', defaultMessage: 'Please send the verification code first' }));
                return null;
              }

              const verificationCode = mode === 'sms' ? values.smsVerificationCode : values.emailVerificationCode;
              if (!verificationCode) {
                message.warning(formatMessage({ id: 'page.login.error.pleaseEnterCaptcha', defaultMessage: 'Please enter the verification code' }));
                return null;
              }

              return authService.loginCodeComplete(
                {
                  challengeId: challenge.challengeId,
                  verificationCode,
                },
                {
                  autoRedirectOnUnauthorized: false,
                  silent: true,
                },
              );
            })();

      if (!loginResponse) {
        return false;
      }

      if (pendingSecondFactorLogin) {
        resetSecondFactorFlow();
      }

      if (loginResponse.requiresSecondFactor) {
        setPendingSecondFactorLogin(loginResponse);
        message.info(loginResponse.secondFactorOptions?.[0]?.promptMessage || formatMessage({ id: 'page.login.code.secondFactor', defaultMessage: 'Please enter the verification code to complete second-factor verification' }));
        return false;
      }

      await completeSuccessfulLogin(loginResponse);
      return true;
    } catch (error) {
      if (error instanceof ApiRequestError) {
        const feedback = resolveLoginErrorFeedback(error);
        message.open({
          type: feedback.type,
          content: feedback.message,
        });
        if (securitySettings.captchaEnabled && securitySettings.captchaType === 'IMAGE') {
          void refreshCaptcha();
        }
        return false;
      }

      if (error instanceof Error) {
        message.error(error.message || formatMessage({ id: 'page.login.error.loginFailed', defaultMessage: 'Login failed, please try again later' }));
        if (securitySettings.captchaEnabled && securitySettings.captchaType === 'IMAGE') {
          void refreshCaptcha();
        }
        return false;
      }

      message.error(formatMessage({ id: 'page.login.error.loginFailed', defaultMessage: 'Login failed, please try again later' }));
      if (securitySettings.captchaEnabled && securitySettings.captchaType === 'IMAGE') {
        void refreshCaptcha();
      }
      return false;
    } finally {
      endLoginFlow();
      setSubmitting(false);
    }
  };

  const handleFinishFailed: FormProps<LoginFormValues>['onFinishFailed'] = ({ errorFields }) => {
    const hasSliderCaptchaError = errorFields.some((field) => field.name.includes('captchaProof'));
    if (!hasSliderCaptchaError) {
      return;
    }

    message.warning(formatMessage({ id: 'page.login.error.pleaseCompleteSliderCaptcha', defaultMessage: 'Please complete the slider captcha first' }));
  };

  const agreementPreviewTitle = agreementPreviewKind === 'user'
    ? formatMessage({ id: 'page.login.agreement.user', defaultMessage: 'User Agreement' })
    : formatMessage({ id: 'page.login.agreement.privacy', defaultMessage: 'Privacy Policy' });
  const agreementPreviewMarkdown =
    agreementPreviewKind === 'user' ? agreementSettings.userAgreementMarkdown : agreementSettings.privacyAgreementMarkdown;

  return (
    <div className="saas-login-page" style={loginPageStyle}>
      <LoginFormPage<LoginFormValues>
        form={loginForm}
        title={brandingSettings.websiteName}
        subTitle={
          activeLoginMode === 'password'
            ? formatMessage({ id: 'page.login.passwordSubtitle', defaultMessage: 'Password login' })
            : activeLoginMode === 'sms'
              ? formatMessage({ id: 'page.login.smsSubtitle', defaultMessage: 'SMS code login' })
              : formatMessage({ id: 'page.login.emailSubtitle', defaultMessage: 'Email code login' })
        }
        actions={null}
        initialValues={{ remember: true }}
        message={false}
        onFinish={handleSubmit}
        onFinishFailed={handleFinishFailed}
        submitter={{
          submitButtonProps: {
            children: pendingSecondFactorLogin
              ? formatMessage({ id: 'page.login.submit.verify', defaultMessage: 'Verify and log in' })
              : formatMessage({ id: 'page.login.submit.login', defaultMessage: 'Log in' }),
            loading: submitting,
            block: true,
          },
          resetButtonProps: false,
        }}
        containerStyle={{
          width: '100%',
          maxWidth: 536,
          boxSizing: 'border-box',
        }}
        style={{
          width: '100%',
          minHeight: '100%',
          background: 'transparent',
        }}
        mainStyle={{ width: '100%', maxWidth: 440, margin: '0 auto', background: 'transparent' }}
      >
        <LoginFormFields
          activeLoginMode={activeLoginMode}
          availableLoginModes={availableLoginModes}
          agreementSettings={agreementSettings}
          pendingSecondFactorLogin={pendingSecondFactorLogin}
          pendingSecondFactorPrompt={pendingSecondFactorPrompt}
          securityCaptchaEnabled={securitySettings.captchaEnabled}
          securityCaptchaType={securitySettings.captchaType}
          captchaChallenge={captchaChallenge}
          captchaLoading={captchaLoading}
          captchaImageLoadFailed={captchaImageLoadFailed}
          loginEncryptionLoading={loginEncryptionLoading}
          sendingLoginType={sendingLoginType}
          loginCodeChallenges={loginCodeChallenges}
          wechatLoginAvailable={Boolean(loginCapabilities.wechatLoginAvailable)}
          passkeyLoginAvailable={Boolean(loginCapabilities.passkeyLoginAvailable && loginCapabilities.passkeyPasswordlessAvailable)}
          passkeyLoading={passkeySubmitting}
          onModeChange={setActiveLoginMode}
          onSendLoginCode={(mode) => void handleSendLoginCode(mode)}
          onWechatLogin={() => void handleWechatLogin()}
          onPasskeyLogin={() => void handlePasskeyLogin()}
          onRefreshCaptcha={() => void refreshCaptcha()}
          onCaptchaImageError={() => setCaptchaImageLoadFailed(true)}
          onSliderCaptchaChallengeChange={setCaptchaChallenge}
          onSliderCaptchaVerified={(captchaProof) => loginForm.setFieldValue('captchaProof', captchaProof)}
          onSliderCaptchaReset={() => loginForm.setFieldValue('captchaProof', undefined)}
          onOpenAgreementPreview={openAgreementPreview}
        />
      </LoginFormPage>

      <AgreementPreviewModal
        open={agreementPreviewOpen}
        onClose={() => setAgreementPreviewOpen(false)}
        title={agreementPreviewTitle}
        markdown={agreementPreviewMarkdown}
      />
    </div>
  );
};

export default Login;
