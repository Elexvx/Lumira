import { AppstoreOutlined, LockOutlined, UserOutlined } from '@ant-design/icons';
import { LoginFormPage, ProFormCheckbox, ProFormText } from '@ant-design/pro-components';
import { useState } from 'react';
import { history, useLocation } from 'umi';
import { Alert, Typography } from 'antd';
import { authService } from '@/services/auth';
import { pluginService } from '@/services/plugin';
import { ApiRequestError } from '@/services/common/request';
import { initializeAfterLogin } from '@/auth/session';
import { tenantContext } from '@/tenant/context';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import UserLayout from '@/layouts/UserLayout';
import './Login.less';

interface LoginFormValues {
  username?: string;
  password?: string;
  remember?: boolean;
}

const Login = () => {
  const [submitting, setSubmitting] = useState(false);
  const [loginError, setLoginError] = useState<string>();
  const { setInitialState } = useInitialStateModel();
  const location = useLocation();

  const handleSubmit = async (values: LoginFormValues): Promise<boolean> => {
    setSubmitting(true);
    setLoginError(undefined);
    try {
      const loginResponse = await authService.login({
        username: values.username,
        password: values.password || '',
      });

      const sessionResult = await initializeAfterLogin(loginResponse);
      const [menuTree, availablePlugins] = await Promise.all([
        pluginService.currentMenus({ autoRedirectOnUnauthorized: false }),
        pluginService.currentAvailable({ autoRedirectOnUnauthorized: false }),
      ]);
      setInitialState((prev: AppInitialState | undefined) => ({
        ...prev,
        currentUser: sessionResult.currentUser,
        currentTenant: tenantContext.getCurrentTenant(),
        myTenants: tenantContext.getMyTenants(),
        menuTree,
        availablePlugins,
      }));

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
    <UserLayout>
      <div className="saas-login-page">
        <LoginFormPage<LoginFormValues>
          logo={
            <span className="saas-login-page__brand-mark" aria-hidden="true">
              <AppstoreOutlined />
            </span>
          }
          title="宏翔商道"
          subTitle="企业级智能协同管理平台"
          initialValues={{ remember: true }}
          message={loginError ? <Alert showIcon type="error" message={loginError} /> : false}
          onFinish={handleSubmit}
          submitter={{
            submitButtonProps: {
              size: 'large',
              loading: submitting,
            },
          }}
          containerStyle={{
            minWidth: 0,
          }}
          style={{
            minHeight: '100vh',
            background: 'transparent',
          }}
        >
          <div className="saas-login-page__heading">
            <Typography.Text className="saas-login-page__eyebrow">账号密码登录</Typography.Text>
            <Typography.Paragraph className="saas-login-page__helper">
              使用平台账号登录，进入宏翔商道后台工作台。
            </Typography.Paragraph>
          </div>
          <ProFormText
            name="username"
            fieldProps={{
              size: 'large',
              prefix: <UserOutlined className="saas-login-page__field-icon" />,
              autoComplete: 'username',
            }}
            placeholder="请输入账号"
            rules={[{ required: true, message: '请输入账号' }]}
          />
          <ProFormText.Password
            name="password"
            fieldProps={{
              size: 'large',
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
    </UserLayout>
  );
};

export default Login;
