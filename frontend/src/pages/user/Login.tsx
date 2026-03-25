import { Button, Form, Input, Typography } from 'antd';

const Login = () => {
  return (
    <>
      <Typography.Title level={3}>SaaS 平台登录</Typography.Title>
      <Form layout="vertical">
        <Form.Item label="账号" name="username">
          <Input placeholder="请输入账号" />
        </Form.Item>
        <Form.Item label="密码" name="password">
          <Input.Password placeholder="请输入密码" />
        </Form.Item>
        <Button type="primary" block>
          登录（占位）
        </Button>
      </Form>
    </>
  );
};

export default Login;
