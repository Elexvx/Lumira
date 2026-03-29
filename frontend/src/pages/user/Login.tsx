import { useState } from 'react';
import { history, useLocation } from 'umi';
import { Alert, Button, Form, Input, Tabs, Typography } from 'antd';
import { authService } from '@/services/auth';
import { pluginService } from '@/services/plugin';
import { ApiRequestError } from '@/services/common/request';
import { initializeAfterLogin } from '@/auth/session';
import { tenantContext } from '@/tenant/context';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';

type LoginMode = 'username' | 'mobile';

interface LoginFormValues {
  username?: string;
  mobile?: string;
  password: string;
}

interface LoginErrorState {
  message: string;
  requestId?: string;
}

const Login = () => {
  const [loginMode, setLoginMode] = useState<LoginMode>('username');
  const [submitting, setSubmitting] = useState(false);
  const [loginError, setLoginError] = useState<LoginErrorState | null>(null);
  const { setInitialState } = useInitialStateModel();
  const location = useLocation();

  const handleSubmit = async (values: LoginFormValues) => {
    setSubmitting(true);
    setLoginError(null);
    try {
      const loginResponse = await authService.login({
        username: loginMode === 'username' ? values.username : undefined,
        mobile: loginMode === 'mobile' ? values.mobile : undefined,
        password: values.password,
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
    } catch (error) {
      if (error instanceof ApiRequestError) {
        setLoginError({
          message: error.userMessage || error.message || '登录失败，请稍后重试',
          requestId: error.requestId,
        });
        return;
      }

      if (error instanceof Error) {
        setLoginError({
          message: error.message || '登录失败，请稍后重试',
        });
        return;
      }

      setLoginError({
        message: '登录失败，请稍后重试',
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <Typography.Title level={3}>SaaS 平台登录</Typography.Title>
      <Typography.Paragraph type="secondary">登录后可进入租户上下文并进行租户切换。</Typography.Paragraph>
      {loginError ? (
        <div style={{ marginBottom: 16 }}>
          <Alert type="error" message={loginError.message} showIcon />
          {loginError.requestId ? (
            <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
              请求编号：{loginError.requestId}
            </Typography.Text>
          ) : null}
        </div>
      ) : null}
      <Tabs
        activeKey={loginMode}
        onChange={(next) => setLoginMode(next as LoginMode)}
        items={[
          { key: 'username', label: '用户名登录' },
          { key: 'mobile', label: '手机号登录' },
        ]}
      />
      <Form<LoginFormValues> layout="vertical" onFinish={handleSubmit}>
        {loginMode === 'username' ? (
          <Form.Item
            label="用户名"
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input placeholder="请输入用户名" autoComplete="username" />
          </Form.Item>
        ) : (
          <Form.Item
            label="手机号"
            name="mobile"
            rules={[
              { required: true, message: '请输入手机号' },
              { pattern: /^1\d{10}$/, message: '请输入有效的手机号' },
            ]}
          >
            <Input placeholder="请输入手机号" autoComplete="tel" maxLength={11} />
          </Form.Item>
        )}
        <Form.Item
          label="密码"
          name="password"
          rules={[
            { required: true, message: '请输入密码' },
            { min: 6, message: '密码长度不能少于 6 位' },
          ]}
        >
          <Input.Password placeholder="请输入密码" autoComplete="current-password" />
        </Form.Item>
        <Button type="primary" block htmlType="submit" loading={submitting}>
          登录
        </Button>
      </Form>
    </>
  );
};

export default Login;
