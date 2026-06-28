import { Button, Card, Form, Input, Result, Spin, Typography } from 'antd';
import { history, useLocation } from '@umijs/max';
import { useEffect, useMemo, useState } from 'react';
import { completeAccountActivation, verifyAccountActivationToken, type AccountActivationInfo } from '@/services/accountActivation/api';
import { message } from '@/theme/antdFeedbackBridge';
import { showErrorMessage } from '@/utils/errorMessage';
import './ActivationPage.css';

interface ActivationFormValues {
  password: string;
  confirmPassword: string;
}

const ActivationPage = () => {
  const location = useLocation();
  const [form] = Form.useForm<ActivationFormValues>();
  const [info, setInfo] = useState<AccountActivationInfo>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const token = useMemo(() => new URLSearchParams(location.search).get('token') || '', [location.search]);

  useEffect(() => {
    if (!token) {
      setInfo({ valid: false, reason: '缺少激活 token' });
      setLoading(false);
      return;
    }
    verifyAccountActivationToken(token)
      .then(setInfo)
      .catch((error) => {
        showErrorMessage(error, '激活链接校验失败');
        setInfo({ valid: false, reason: '激活链接校验失败' });
      })
      .finally(() => setLoading(false));
  }, [token]);

  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await completeAccountActivation(token, values.password);
      message.success('账号已激活，请登录');
      history.replace('/user/login');
    } catch (error) {
      showErrorMessage(error, '账号激活失败');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="activation-page">
        <Spin />
      </div>
    );
  }

  if (!info?.valid) {
    return (
      <div className="activation-page">
        <Result
          status="warning"
          title="激活链接不可用"
          subTitle={info?.reason || '链接已失效或已被使用'}
          extra={<Button type="primary" onClick={() => history.replace('/user/login')}>返回登录</Button>}
        />
      </div>
    );
  }

  return (
    <div className="activation-page">
      <Card className="activation-card">
        <Typography.Title level={3}>激活账号</Typography.Title>
        <Typography.Paragraph type="secondary">
          账号 {info.username} 已审批通过，请设置登录密码。
        </Typography.Paragraph>
        <Form form={form} layout="vertical">
          <Form.Item
            name="password"
            label="新密码"
            rules={[{ required: true, message: '请输入新密码' }, { min: 6, message: '密码至少 6 位' }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认密码"
            dependencies={['password']}
            rules={[
              { required: true, message: '请再次输入新密码' },
              ({ getFieldValue }) => ({
                validator: (_, value) => {
                  if (!value || getFieldValue('password') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Button type="primary" block loading={saving} onClick={() => void submit()}>
            完成激活
          </Button>
        </Form>
      </Card>
    </div>
  );
};

export default ActivationPage;
