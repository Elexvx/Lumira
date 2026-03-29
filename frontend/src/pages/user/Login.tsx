import { useState } from 'react';
import { history, useLocation } from 'umi';
import { Button, Form, Input, Typography, message } from 'antd';
import { authService } from '@/services/auth';
import { pluginService } from '@/services/plugin';
import { ApiRequestError } from '@/services/common/request';
import { initializeAfterLogin } from '@/auth/session';
import { tenantContext } from '@/tenant/context';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';

interface LoginFormValues {
  username?: string;
  password?: string;
}

const Login = () => {
  const [submitting, setSubmitting] = useState(false);
  const { setInitialState } = useInitialStateModel();
  const location = useLocation();

  const handleSubmit = async (values: LoginFormValues) => {
    setSubmitting(true);
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
    } catch (error) {
      if (error instanceof ApiRequestError) {
        message.error(error.userMessage || error.message || '登录失败，请稍后重试');
        return;
      }

      if (error instanceof Error) {
        message.error(error.message || '登录失败，请稍后重试');
        return;
      }

      message.error('登录失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        width: '100%',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'stretch',
      }}
    >
      <Typography.Title
        level={2}
        style={{
          textAlign: 'center',
          margin: '0 0 22px',
          fontWeight: 700,
          color: '#222',
          fontSize: 28,
          lineHeight: 1.2,
        }}
      >
        宏翔商道-综合管理系统
      </Typography.Title>
      
      <Form<LoginFormValues>
        layout="vertical"
        onFinish={handleSubmit}
        colon={false}
        style={{ width: '100%', marginTop: '16px' }}
      >
        <Form.Item
          name="username"
          rules={[{ required: true, message: '请输入账号' }]}
          style={{ marginBottom: 16 }}
        >
          <Input placeholder="请输入账号" autoComplete="username" />
        </Form.Item>
        <Form.Item
          name="password"
          rules={[
            { required: true, message: '请输入密码' },
            { min: 6, message: '密码长度不能少于 6 位' },
          ]}
          style={{ marginBottom: 24 }}
        >
          <Input.Password placeholder="请输入密码" autoComplete="current-password" />
        </Form.Item>
        <Button
          type="primary"
          block
          htmlType="submit"
          loading={submitting}
          style={{ height: 32, fontWeight: 600, marginTop: 4 }}
        >
          登录
        </Button>
      </Form>
    </div>
  );
};

export default Login;
