import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { LoginFormPage, ProFormCheckbox, ProFormText } from '@ant-design/pro-components';
import { useState } from 'react';
import { flushSync } from 'react-dom';
import { history, useLocation } from 'umi';
import { Alert } from 'antd';
import { DEFAULT_BRANDING_SETTINGS, normalizeBrandingSettings, persistBrandingSettings } from '@/branding/settings';
import { authService } from '@/services/auth';
import { pluginService } from '@/services/plugin';
import { ApiRequestError } from '@/services/common/request';
import { initializeAfterLogin } from '@/auth/session';
import { tenantContext } from '@/tenant/context';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { systemService } from '@/services/system';
import './Login.less';

interface LoginFormValues {
  username?: string;
  password?: string;
  remember?: boolean;
}

const Login = () => {
  const [submitting, setSubmitting] = useState(false);
  const [loginError, setLoginError] = useState<string>();
  const { initialState, setInitialState } = useInitialStateModel();
  const location = useLocation();
  const brandingSettings = normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS);

  const handleSubmit = async (values: LoginFormValues): Promise<boolean> => {
    setSubmitting(true);
    setLoginError(undefined);
    try {
      const loginResponse = await authService.login({
        username: values.username,
        password: values.password || '',
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
        }));
      });

      const searchParams = new URLSearchParams(location.search);
      const redirect = searchParams.get('redirect') || '/dashboard/home';
      history.replace(redirect);
      return true;
    } catch (error) {
      if (error instanceof ApiRequestError) {
        setLoginError(error.userMessage || error.message || '登录失败，请稍后重试');
        return false;
      }

      if (error instanceof Error) {
        setLoginError(error.message || '登录失败，请稍后重试');
        return false;
      }

      setLoginError('登录失败，请稍后重试');
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
        logo={
          brandingSettings.websiteLogoUrl ? (
            <img className="saas-login-page__logo" src={brandingSettings.websiteLogoUrl} alt={brandingSettings.websiteName} />
          ) : undefined
        }
        initialValues={{ remember: true }}
        message={loginError ? <Alert showIcon type="error" message={loginError} /> : false}
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
