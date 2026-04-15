import { history, useLocation } from '@umijs/max';
import { LoginFormPage } from '@ant-design/pro-components';
import { useCallback, useEffect, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { Alert, Form, message } from 'antd';
import { DEFAULT_BRANDING_SETTINGS, normalizeBrandingSettings, persistBrandingSettings } from '@/branding/settings';
import { loadCaptchaChallenge } from '@/auth/captcha';
import { createCaptchaRefreshController } from '@/auth/captchaRefreshController';
import { authService } from '@/services/auth';
import { pluginService } from '@/services/plugin';
import { systemService } from '@/services/system';
import { ApiRequestError } from '@/services/common/request';
import { resolveApiErrorFeedback } from '@/services/common/errorFeedback';
import { beginLoginFlow, endLoginFlow } from '@/auth/loginFlowState';
import { encryptLoginPassword } from '@/auth/loginEncryption';
import { initializeAfterLogin } from '@/auth/session';
import { tenantContext } from '@/tenant/context';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { DEFAULT_SECURITY_SETTINGS, normalizeSecuritySettings, persistSecuritySettings } from '@/auth/securitySettings';
import { DEFAULT_WATERMARK_SETTINGS, persistWatermarkSettings } from '@/watermark/settings';
import { LoginFormFields } from '@/pages/user/login/components/LoginFormFields';
import type { CaptchaChallenge, LoginEncryptionKey, LoginResponse, SecuritySettings } from '@/types/api';
import './Login.less';

interface LoginFormValues {
  username?: string;
  password?: string;
  remember?: boolean;
  captchaCode?: string;
  verificationCode?: string;
}

interface LoginErrorState {
  type: 'info' | 'warning' | 'error';
  message: string;
}

const Login = () => {
  const [submitting, setSubmitting] = useState(false);
  const [loginError, setLoginError] = useState<LoginErrorState>();
  const [pendingSecondFactorLogin, setPendingSecondFactorLogin] = useState<LoginResponse | null>(null);
  const [selectedSecondFactorChallengeId, setSelectedSecondFactorChallengeId] = useState<string | null>(null);
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
          setLoginError({
            type: 'error',
            message: error instanceof Error ? error.message : '登录加密信息加载失败，请刷新后重试',
          });
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
  const pendingSecondFactorOption =
    pendingSecondFactorOptions.find((option) => option.challengeId === selectedSecondFactorChallengeId) ||
    pendingSecondFactorOptions[0] ||
    null;
  const pendingSecondFactorPrompt =
    pendingSecondFactorOption?.promptMessage ||
    (pendingSecondFactorLogin?.secondFactorPluginName
      ? `${pendingSecondFactorLogin.secondFactorPluginName} 需要完成二次验证`
      : '请输入收到的验证码完成二次验证');

  const resetSecondFactorFlow = useCallback(() => {
    setPendingSecondFactorLogin(null);
    setSelectedSecondFactorChallengeId(null);
    setLoginError(undefined);
    loginForm.setFieldsValue({ verificationCode: undefined });
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

  const handleSubmit = async (values: LoginFormValues): Promise<boolean> => {
    if (!pendingSecondFactorLogin) {
      if (securitySettings.captchaEnabled && !captchaChallenge?.captchaId) {
        setLoginError({
          type: 'warning',
          message: '验证码已过期，请刷新后重试',
        });
        return false;
      }

      if (securitySettings.captchaEnabled && !values.captchaCode) {
        setLoginError({
          type: 'warning',
          message: '请输入验证码',
        });
        return false;
      }
    }

    setSubmitting(true);
    setLoginError(undefined);
    beginLoginFlow();
    try {
      const loginResponse = pendingSecondFactorLogin
        ? await authService.secondFactorComplete({
            pluginCode: pendingSecondFactorOption?.pluginCode || pendingSecondFactorLogin.secondFactorPluginCode || '',
            challengeId: pendingSecondFactorOption?.challengeId || pendingSecondFactorLogin.secondFactorChallengeId || '',
            verificationCode: values.verificationCode || '',
          })
        : await (async () => {
            const encryptionKey = loginEncryptionKey || (await loadLoginEncryptionKey());
            if (!encryptionKey) {
              throw new Error('登录加密信息加载失败，请刷新后重试');
            }

            return authService.login({
              username: values.username,
              password: await encryptLoginPassword(values.password || '', encryptionKey),
              captchaId: securitySettings.captchaEnabled ? captchaChallenge?.captchaId : undefined,
              captchaCode: securitySettings.captchaEnabled ? values.captchaCode : undefined,
            });
          })();

      if (pendingSecondFactorLogin) {
        resetSecondFactorFlow();
      }

      if (loginResponse.requiresSecondFactor) {
        const nextOption = loginResponse.secondFactorOptions?.[0] || null;
        setPendingSecondFactorLogin(loginResponse);
        setSelectedSecondFactorChallengeId(nextOption?.challengeId || null);
        setLoginError({
          type: 'info',
          message: nextOption?.promptMessage || '请输入验证码完成二次验证',
        });
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
        }));
      });

      const searchParams = new URLSearchParams(location.search);
      const redirect = searchParams.get('redirect') || '/dashboard/home';
      history.replace(redirect);
      return true;
    } catch (error) {
      if (error instanceof ApiRequestError) {
        const feedback = resolveApiErrorFeedback(error, false);
        setLoginError({
          // redirectToLogin messages (e.g. "请先登录后再继续操作") are meant for
          // the global 401 auto-redirect flow and make no sense on the login page
          // itself. Show a generic failure message instead.
          type: feedback.redirectToLogin ? 'error' : feedback.type,
          message: feedback.redirectToLogin ? '登录失败，请稍后重试' : feedback.message,
        });
        if (securitySettings.captchaEnabled) {
          void refreshCaptcha();
        }
        return false;
      }

      if (error instanceof Error) {
        setLoginError({
          type: 'error',
          message: error.message || '登录失败，请稍后重试',
        });
        if (securitySettings.captchaEnabled) {
          void refreshCaptcha();
        }
        return false;
      }

      setLoginError({
        type: 'error',
        message: '登录失败，请稍后重试',
      });
      if (securitySettings.captchaEnabled) {
        void refreshCaptcha();
      }
      return false;
    } finally {
      endLoginFlow();
      setSubmitting(false);
    }
  };

  return (
    <div className="saas-login-page">
      <LoginFormPage<LoginFormValues>
        form={loginForm}
        title={brandingSettings.websiteName}
        subTitle="后台管理系统登录"
        initialValues={{ remember: true }}
        message={loginError ? <Alert showIcon type={loginError.type} message={loginError.message} /> : false}
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
          maxWidth: 360,
        }}
        style={{
          width: '100%',
          height: '100%',
        }}
      >
        <LoginFormFields
          pendingSecondFactorLogin={pendingSecondFactorLogin}
          pendingSecondFactorOptions={pendingSecondFactorOptions}
          pendingSecondFactorOption={pendingSecondFactorOption}
          pendingSecondFactorPrompt={pendingSecondFactorPrompt}
          selectedSecondFactorChallengeId={selectedSecondFactorChallengeId}
          securityCaptchaEnabled={securitySettings.captchaEnabled}
          captchaChallenge={captchaChallenge}
          captchaLoading={captchaLoading}
          captchaImageLoadFailed={captchaImageLoadFailed}
          loginEncryptionLoading={loginEncryptionLoading}
          onSecondFactorChange={setSelectedSecondFactorChallengeId}
          onResetSecondFactorFlow={resetSecondFactorFlow}
          onRefreshCaptcha={() => void refreshCaptcha()}
          onCaptchaImageError={() => setCaptchaImageLoadFailed(true)}
        />
      </LoginFormPage>
    </div>
  );
};

export default Login;
