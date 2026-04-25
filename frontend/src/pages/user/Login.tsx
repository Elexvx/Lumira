import { LoginFormPage } from '@ant-design/pro-components';
import { history, useLocation } from '@umijs/max';
import MarkdownPreview from '@uiw/react-markdown-preview';
import '@uiw/react-markdown-preview/markdown.css';
import { Form, Modal, message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { DEFAULT_AGREEMENT_SETTINGS, normalizeAgreementSettings } from '@/agreement/settings';
import { DEFAULT_BRANDING_SETTINGS, normalizeBrandingSettings, persistBrandingSettings } from '@/branding/settings';
import { isLoggedIn } from '@/auth/session';
import { loadCaptchaChallenge } from '@/auth/captcha';
import { createCaptchaRefreshController } from '@/auth/captchaRefreshController';
import { beginLoginFlow, endLoginFlow } from '@/auth/loginFlowState';
import { encryptLoginPassword } from '@/auth/loginEncryption';
import { initializeAfterLogin } from '@/auth/session';
import { DEFAULT_SECURITY_SETTINGS, normalizeSecuritySettings, persistSecuritySettings } from '@/auth/securitySettings';
import { createLoginStorageHandler, resolveLoginRedirectTarget } from '@/auth/loginRedirect';
import { authService } from '@/services/auth';
import { pluginService } from '@/services/plugin';
import { systemService } from '@/services/system';
import { ApiRequestError } from '@/services/common/request';
import { tenantContext } from '@/tenant/context';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { DEFAULT_WATERMARK_SETTINGS, persistWatermarkSettings } from '@/watermark/settings';
import { LoginFormFields, type LoginFormValues, type LoginMode } from '@/pages/user/login/components/LoginFormFields';
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
import './Login.less';

const DEFAULT_LOGIN_CAPABILITIES: LoginCapabilities = {
  passwordLoginAvailable: true,
  smsLoginAvailable: false,
  emailLoginAvailable: false,
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
  const agreementSettings = normalizeAgreementSettings(initialState?.agreementSettings || DEFAULT_AGREEMENT_SETTINGS);
  const loginCapabilities = initialState?.loginCapabilities || DEFAULT_LOGIN_CAPABILITIES;
  const availableLoginModes = useMemo(() => getAvailableLoginModes(loginCapabilities), [loginCapabilities]);
  const redirectTarget = resolveLoginRedirectTarget(location.search);
  const securitySettingsRef = useRef(securitySettings);
  const loginEncryptionLoadPromiseRef = useRef<Promise<LoginEncryptionKey | null> | null>(null);
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
      onRefreshFailure: () => message.warning('验证码刷新失败，请稍后重试'),
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
          message.error(error instanceof Error ? error.message : '登录加密信息加载失败，请刷新后重试');
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
    pendingSecondFactorOption?.promptMessage || (pendingSecondFactorOption?.factorName ? `${pendingSecondFactorOption.factorName} 需要完成二次验证` : '请输入收到的验证码完成二次验证');

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

    if (!captchaChallenge?.captchaId || captchaChallenge.captchaType !== securitySettings.captchaType) {
      void refreshCaptcha();
    }
  }, [captchaChallenge?.captchaId, captchaChallenge?.captchaType, refreshCaptcha, securitySettings.captchaEnabled, securitySettings.captchaType]);

  useEffect(() => {
    if (securitySettings.captchaEnabled) {
      loginForm.setFieldValue('captchaCode', undefined);
    }
  }, [captchaChallenge?.captchaId, loginForm, securitySettings.captchaEnabled]);

  useEffect(() => {
    const alreadyAuthenticated = isLoggedIn() || Boolean(initialState?.currentUser?.sessionId);
    if (!alreadyAuthenticated) {
      return;
    }

    window.location.replace(redirectTarget);
  }, [initialState?.currentUser?.sessionId, redirectTarget]);

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
      const accountField = mode === 'sms' ? 'smsAccount' : 'emailAccount';
      const account = loginForm.getFieldValue(accountField);
      if (!account) {
        message.warning(mode === 'sms' ? '请输入手机号' : '请输入邮箱');
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
        if (challenge.promptMessage) {
          message.success(challenge.promptMessage);
        } else {
          message.success('验证码已发送');
        }
      } catch (error) {
        message.error(error instanceof Error ? error.message : '验证码发送失败，请稍后重试');
      } finally {
        setSendingLoginType(null);
      }
    },
    [loginForm],
  );

  const handleSubmit = async (values: LoginFormValues): Promise<boolean> => {
    if (!pendingSecondFactorLogin) {
      if (activeLoginMode === 'password' && securitySettings.captchaEnabled && !captchaChallenge?.captchaId) {
        message.warning('验证码已过期，请刷新后重试');
        return false;
      }

      if (activeLoginMode === 'password' && securitySettings.captchaEnabled && !values.captchaCode) {
        message.warning('请输入验证码');
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
                username: values.passwordAccount,
                password: await encryptLoginPassword(values.passwordPassword || '', encryptionKey),
                captchaId: securitySettings.captchaEnabled ? captchaChallenge?.captchaId : undefined,
                captchaCode: securitySettings.captchaEnabled ? values.captchaCode : undefined,
              });
            })()
          : await (async () => {
              const mode = activeLoginMode as Exclude<LoginMode, 'password'>;
              const challenge = loginCodeChallenges[mode];
              if (!challenge?.challengeId) {
                message.warning('请先发送验证码');
                return null;
              }

              const verificationCode = mode === 'sms' ? values.smsVerificationCode : values.emailVerificationCode;
              if (!verificationCode) {
                message.warning('请输入验证码');
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
        message.info(loginResponse.secondFactorOptions?.[0]?.promptMessage || '请输入验证码完成二次验证');
        return false;
      }

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
          currentTenant: tenantContext.getCurrentTenant(),
          myTenants: tenantContext.getMyTenants(),
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
      history.replace(redirectTarget);
      return true;
    } catch (error) {
      if (error instanceof ApiRequestError) {
        const feedback = resolveLoginErrorFeedback(error);
        message.open({
          type: feedback.type,
          content: feedback.message,
        });
        if (securitySettings.captchaEnabled) {
          void refreshCaptcha();
        }
        return false;
      }

      if (error instanceof Error) {
        message.error(error.message || '登录失败，请稍后重试');
        if (securitySettings.captchaEnabled) {
          void refreshCaptcha();
        }
        return false;
      }

      message.error('登录失败，请稍后重试');
      if (securitySettings.captchaEnabled) {
        void refreshCaptcha();
      }
      return false;
    } finally {
      endLoginFlow();
      setSubmitting(false);
    }
  };

  const agreementPreviewTitle = agreementPreviewKind === 'user' ? '用户协议预览' : '隐私政策预览';
  const agreementPreviewMarkdown =
    agreementPreviewKind === 'user' ? agreementSettings.userAgreementMarkdown : agreementSettings.privacyAgreementMarkdown;

  return (
    <div className="saas-login-page">
      <LoginFormPage<LoginFormValues>
        form={loginForm}
        title={brandingSettings.websiteName}
        subTitle={activeLoginMode === 'password' ? '账号密码登录' : activeLoginMode === 'sms' ? '短信验证码登录' : '邮箱验证码登录'}
        actions={null}
        initialValues={{ remember: true }}
        message={false}
        onFinish={handleSubmit}
        submitter={{
          submitButtonProps: {
            children: pendingSecondFactorLogin ? '验证并登录' : '登录',
            loading: submitting,
            block: true,
          },
          resetButtonProps: false,
        }}
        containerStyle={{
          width: '100%',
          maxWidth: 440,
          background: 'transparent',
          boxShadow: 'none',
          border: 'none',
        }}
        style={{
          width: '100%',
          minHeight: '100%',
          background: 'transparent',
        }}
        mainStyle={{ width: '100%', background: 'transparent' }}
      >
        <LoginFormFields
          activeLoginMode={activeLoginMode}
          availableLoginModes={availableLoginModes}
          agreementSettings={agreementSettings}
          pendingSecondFactorLogin={pendingSecondFactorLogin}
          pendingSecondFactorPrompt={pendingSecondFactorPrompt}
          securityCaptchaEnabled={securitySettings.captchaEnabled}
          captchaChallenge={captchaChallenge}
          captchaLoading={captchaLoading}
          captchaImageLoadFailed={captchaImageLoadFailed}
          loginEncryptionLoading={loginEncryptionLoading}
          sendingLoginType={sendingLoginType}
          loginCodeChallenges={loginCodeChallenges}
          onModeChange={setActiveLoginMode}
          onSendLoginCode={(mode) => void handleSendLoginCode(mode)}
          onRefreshCaptcha={() => void refreshCaptcha()}
          onCaptchaImageError={() => setCaptchaImageLoadFailed(true)}
          onOpenAgreementPreview={openAgreementPreview}
        />
      </LoginFormPage>

      <Modal
        open={agreementPreviewOpen}
        onCancel={() => setAgreementPreviewOpen(false)}
        footer={null}
        width={840}
        title={agreementPreviewTitle}
        destroyOnClose
      >
        {agreementPreviewMarkdown ? (
          <MarkdownPreview source={agreementPreviewMarkdown} />
        ) : (
          <div style={{ color: 'var(--saas-text-secondary)' }}>后台暂未配置该条款内容。</div>
        )}
      </Modal>
    </div>
  );
};

export default Login;
