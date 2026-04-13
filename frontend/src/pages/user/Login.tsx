import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { history, useLocation } from '@umijs/max';
import { LoginFormPage, ProFormCheckbox, ProFormText } from '@ant-design/pro-components';
import { useCallback, useEffect, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { Alert, Form, Input, Spin, Typography, message } from 'antd';
import { DEFAULT_BRANDING_SETTINGS, normalizeBrandingSettings, persistBrandingSettings } from '@/branding/settings';
import { loadCaptchaChallenge } from '@/auth/captcha';
import { createCaptchaRefreshController } from '@/auth/captchaRefreshController';
import { authService } from '@/services/auth';
import { pluginService } from '@/services/plugin';
import { systemService } from '@/services/system';
import { ApiRequestError } from '@/services/common/request';
import { resolveApiErrorFeedback } from '@/services/common/errorFeedback';
import { beginLoginFlow, endLoginFlow } from '@/auth/loginFlowState';
import { initializeAfterLogin } from '@/auth/session';
import { tenantContext } from '@/tenant/context';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { DEFAULT_SECURITY_SETTINGS, normalizeSecuritySettings, persistSecuritySettings } from '@/auth/securitySettings';
import type { CaptchaChallenge, SecuritySettings } from '@/types/api';
import './Login.less';

interface LoginFormValues {
  username?: string;
  password?: string;
  remember?: boolean;
  captchaCode?: string;
}

interface LoginErrorState {
  type: 'info' | 'warning' | 'error';
  message: string;
}

const Login = () => {
  const [submitting, setSubmitting] = useState(false);
  const [loginError, setLoginError] = useState<LoginErrorState>();
  const { initialState, setInitialState } = useInitialStateModel();
  const [securitySettings, setSecuritySettings] = useState<SecuritySettings>(
    normalizeSecuritySettings(initialState?.securitySettings || DEFAULT_SECURITY_SETTINGS),
  );
  const [captchaChallenge, setCaptchaChallenge] = useState<CaptchaChallenge | null>(null);
  const [captchaLoading, setCaptchaLoading] = useState(false);
  const [captchaImageLoadFailed, setCaptchaImageLoadFailed] = useState(false);
  const location = useLocation();
  const brandingSettings = normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS);
  const securitySettingsRef = useRef(securitySettings);
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

  const refreshCaptcha = useCallback(async () => captchaRefreshControllerRef.current.refresh(), []);

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

  const handleSubmit = async (values: LoginFormValues): Promise<boolean> => {
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

    setSubmitting(true);
    setLoginError(undefined);
    beginLoginFlow();
    try {
      const loginResponse = await authService.login({
        username: values.username,
        password: values.password || '',
        captchaId: securitySettings.captchaEnabled ? captchaChallenge?.captchaId : undefined,
        captchaCode: securitySettings.captchaEnabled ? values.captchaCode : undefined,
      });

      const sessionResult = await initializeAfterLogin(loginResponse);
      const [menuResult, pluginResult, brandingResult] = await Promise.allSettled([
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
      ]);
      const menuTree = menuResult.status === 'fulfilled' ? menuResult.value : [];
      const availablePlugins = pluginResult.status === 'fulfilled' ? pluginResult.value : [];
      const normalizedBrandingSettings = normalizeBrandingSettings(
        brandingResult.status === 'fulfilled'
          ? brandingResult.value
          : initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS,
      );
      persistBrandingSettings(normalizedBrandingSettings);
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
        title={brandingSettings.websiteName}
        subTitle="后台管理系统登录"
        initialValues={{ remember: true }}
        message={loginError ? <Alert showIcon type={loginError.type} message={loginError.message} /> : false}
        onFinish={handleSubmit}
        submitter={{
          submitButtonProps: {
            children: '登录',
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
        <ProFormText
          name="username"
          fieldProps={{
            prefix: <UserOutlined className="saas-login-page__field-icon" />,
            autoComplete: 'username',
          }}
          placeholder="请输入账号"
          rules={[{ required: true, message: '请输入账号' }]}
        />
        <ProFormText.Password
          name="password"
          fieldProps={{
            prefix: <LockOutlined className="saas-login-page__field-icon" />,
            autoComplete: 'current-password',
          }}
          placeholder="请输入密码"
          rules={[
            { required: true, message: '请输入密码' },
            { min: 6, message: '密码长度不能少于 6 位' },
          ]}
        />
        {securitySettings.captchaEnabled ? (
          <div className="saas-login-page__captcha-section">
            <div className="saas-login-page__captcha-input">
              <Form.Item
                key={captchaChallenge?.captchaId || 'captcha-code'}
                name="captchaCode"
                rules={[{ required: true, message: '请输入验证码' }]}
              >
                <Input
                  size="large"
                  autoComplete="off"
                  spellCheck={false}
                  maxLength={8}
                  placeholder="请输入验证码"
                  aria-label="验证码"
                />
              </Form.Item>
            </div>
            <button
              type="button"
              className="saas-login-page__captcha-media"
              title="刷新验证码"
              aria-label="刷新验证码"
              onClick={() => void refreshCaptcha()}
            >
              <span className="saas-login-page__captcha-image">
                {captchaLoading ? (
                  <span className="saas-login-page__captcha-loading">
                    <Spin size="small" />
                  </span>
                ) : captchaImageLoadFailed ? (
                  <Typography.Text className="saas-login-page__captcha-placeholder">
                    图片加载失败，点击重试
                  </Typography.Text>
                ) : captchaChallenge?.imageUrl ? (
                  <img
                    src={captchaChallenge.imageUrl}
                    alt="验证码"
                    onError={() => setCaptchaImageLoadFailed(true)}
                  />
                ) : (
                  <Typography.Text className="saas-login-page__captcha-placeholder">点击刷新验证码</Typography.Text>
                )}
              </span>
            </button>
          </div>
        ) : null}
        <div className="saas-login-page__actions">
          <ProFormCheckbox noStyle name="remember">
            保持登录状态
          </ProFormCheckbox>
        </div>
      </LoginFormPage>
    </div>
  );
};

export default Login;
