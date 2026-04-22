import { history, useLocation } from '@umijs/max';
import { LoginFormPage } from '@ant-design/pro-components';
import { useCallback, useEffect, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { Form, message } from 'antd';
import { DEFAULT_BRANDING_SETTINGS, normalizeBrandingSettings, persistBrandingSettings } from '@/branding/settings';
import { isLoggedIn } from '@/auth/session';
import { loadCaptchaChallenge } from '@/auth/captcha';
import { createCaptchaRefreshController } from '@/auth/captchaRefreshController';
import { authService } from '@/services/auth';
import { pluginService } from '@/services/plugin';
import { systemService } from '@/services/system';
import { ApiRequestError } from '@/services/common/request';
import { beginLoginFlow, endLoginFlow } from '@/auth/loginFlowState';
import { encryptLoginPassword } from '@/auth/loginEncryption';
import { initializeAfterLogin } from '@/auth/session';
import { tenantContext } from '@/tenant/context';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { createLoginStorageHandler, resolveLoginRedirectTarget } from '@/auth/loginRedirect';
import { DEFAULT_SECURITY_SETTINGS, normalizeSecuritySettings, persistSecuritySettings } from '@/auth/securitySettings';
import { DEFAULT_WATERMARK_SETTINGS, persistWatermarkSettings } from '@/watermark/settings';
import { LoginFormFields } from '@/pages/user/login/components/LoginFormFields';
import { resolveLoginErrorFeedback } from '@/pages/user/login/loginErrorFeedback';
import type { CaptchaChallenge, LoginEncryptionKey, LoginResponse, SecuritySettings } from '@/types/api';
import './Login.less';

interface LoginFormValues {
  username?: string;
  password?: string;
  remember?: boolean;
  captchaCode?: string;
  verificationCode?: string;
}

const Login = () => {
  const [submitting, setSubmitting] = useState(false);
  const [pendingSecondFactorLogin, setPendingSecondFactorLogin] = useState<LoginResponse | null>(null);
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
  const redirectTarget = resolveLoginRedirectTarget(location.search);
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

  const handleSubmit = async (values: LoginFormValues): Promise<boolean> => {
    if (!pendingSecondFactorLogin) {
      if (securitySettings.captchaEnabled && !captchaChallenge?.captchaId) {
        message.warning('验证码已过期，请刷新后重试');
        return false;
      }

      if (securitySettings.captchaEnabled && !values.captchaCode) {
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
        : await (async () => {
            const encryptionKey = loginEncryptionKey || (await loadLoginEncryptionKey());
            if (!encryptionKey) {
              return null;
            }

            return authService.login({
              username: values.username,
              password: await encryptLoginPassword(values.password || '', encryptionKey),
              captchaId: securitySettings.captchaEnabled ? captchaChallenge?.captchaId : undefined,
              captchaCode: securitySettings.captchaEnabled ? values.captchaCode : undefined,
            });
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
        }));
      });

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

  return (
    <div className="saas-login-page">
      <LoginFormPage<LoginFormValues>
        form={loginForm}
        title={brandingSettings.websiteName}
        subTitle="账号密码登录"
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
        }}
        style={{
          width: '100%',
          minHeight: '100%',
        }}
        mainStyle={{ width: '100%' }}
      >
        <LoginFormFields
          pendingSecondFactorLogin={pendingSecondFactorLogin}
          pendingSecondFactorPrompt={pendingSecondFactorPrompt}
          securityCaptchaEnabled={securitySettings.captchaEnabled}
          captchaChallenge={captchaChallenge}
          captchaLoading={captchaLoading}
          captchaImageLoadFailed={captchaImageLoadFailed}
          loginEncryptionLoading={loginEncryptionLoading}
          onRefreshCaptcha={() => void refreshCaptcha()}
          onCaptchaImageError={() => setCaptchaImageLoadFailed(true)}
        />
      </LoginFormPage>
    </div>
  );
};

export default Login;
