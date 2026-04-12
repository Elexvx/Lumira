import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { history, useLocation } from '@umijs/max';
import { LoginFormPage, ProFormCheckbox, ProFormText } from '@ant-design/pro-components';
import { useCallback, useEffect, useState } from 'react';
import { flushSync } from 'react-dom';
import { Alert, Form, Input, Spin, Typography } from 'antd';
import { DEFAULT_BRANDING_SETTINGS, normalizeBrandingSettings, persistBrandingSettings } from '@/branding/settings';
import { loadCaptchaChallenge } from '@/auth/captcha';
import { authService } from '@/services/auth';
import { pluginService } from '@/services/plugin';
import { ApiRequestError } from '@/services/common/request';
import { resolveApiErrorFeedback } from '@/services/common/errorFeedback';
import { initializeAfterLogin } from '@/auth/session';
import { tenantContext } from '@/tenant/context';
import { systemService } from '@/services/system';
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
  const loginBootstrap = initialState?.loginBootstrap;
  const [securitySettings, setSecuritySettings] = useState<SecuritySettings>(
    loginBootstrap?.securitySettings || initialState?.securitySettings || DEFAULT_SECURITY_SETTINGS,
  );
  const [captchaChallenge, setCaptchaChallenge] = useState<CaptchaChallenge | null>(loginBootstrap?.captchaChallenge || null);
  const [captchaLoading, setCaptchaLoading] = useState(false);
  const location = useLocation();
  const brandingSettings = normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS);

  useEffect(() => {
    if (loginBootstrap) {
      setSecuritySettings(loginBootstrap.securitySettings);
      setCaptchaChallenge(loginBootstrap.captchaChallenge);
      return;
    }

    setSecuritySettings(initialState?.securitySettings || DEFAULT_SECURITY_SETTINGS);
  }, [initialState?.securitySettings, loginBootstrap]);

  useEffect(() => {
    if (loginBootstrap) {
      return;
    }

    let active = true;
    const loadPublicSecuritySettings = async () => {
      try {
        const settings = normalizeSecuritySettings(
          await systemService.publicSecuritySettings({ autoRedirectOnUnauthorized: false, silent: true }),
        );
        if (!active) {
          return;
        }
        persistSecuritySettings(settings);
        setSecuritySettings(settings);
      } catch {
        if (!active) {
          return;
        }
        setSecuritySettings(initialState?.securitySettings || DEFAULT_SECURITY_SETTINGS);
      }
    };

    void loadPublicSecuritySettings();
    return () => {
      active = false;
    };
  }, [initialState?.securitySettings, loginBootstrap]);

  const refreshCaptcha = useCallback(async () => {
    if (!securitySettings.captchaEnabled) {
      setCaptchaChallenge(null);
      return null;
    }

    setCaptchaLoading(true);
    try {
      const challenge = await loadCaptchaChallenge(securitySettings.captchaType, {
        autoRedirectOnUnauthorized: false,
        silent: true,
        skipAuth: true,
      });
      setCaptchaChallenge(challenge);
      return challenge;
    } catch (error) {
      setCaptchaChallenge(null);
      setLoginError({
        type: 'error',
        message: error instanceof Error ? error.message : '验证码加载失败，请稍后重试',
      });
      return null;
    } finally {
      setCaptchaLoading(false);
    }
  }, [securitySettings.captchaEnabled, securitySettings.captchaType]);

  useEffect(() => {
    if (!securitySettings.captchaEnabled) {
      setCaptchaChallenge(null);
      return;
    }

    if (!captchaChallenge?.captchaId) {
      void refreshCaptcha();
    }
  }, [captchaChallenge?.captchaId, refreshCaptcha, securitySettings.captchaEnabled]);

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
    try {
      const loginResponse = await authService.login({
        username: values.username,
        password: values.password || '',
        captchaId: securitySettings.captchaEnabled ? captchaChallenge?.captchaId : undefined,
        captchaCode: securitySettings.captchaEnabled ? values.captchaCode : undefined,
      });

      const sessionResult = await initializeAfterLogin(loginResponse);
      const [menuTree, availablePlugins, latestBrandingSettings] = await Promise.all([
        pluginService.currentMenus({ autoRedirectOnUnauthorized: false }),
        pluginService.currentAvailable({ autoRedirectOnUnauthorized: false }),
        systemService.brandingSettings({ autoRedirectOnUnauthorized: false, silent: true }),
      ]);
      const normalizedBrandingSettings = normalizeBrandingSettings(latestBrandingSettings);
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
          // Clear stale pre-login bootstrap data so re-logins use fresh captcha
          // and security settings rather than cached data from the initial load.
          loginBootstrap: undefined,
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
          type: feedback.type,
          message: feedback.message,
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
                ) : captchaChallenge?.imageUrl ? (
                  <img src={captchaChallenge.imageUrl} alt="验证码" />
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
