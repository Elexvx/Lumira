import { MailOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Alert, Button, Card, Form, Input, InputNumber, Space, Switch, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { useStandardFormProps } from '@/features/form/config';
import { systemService } from '@/services/system';
import type { SmtpSettings, SmtpTestPayload } from '@/types/api';

const SmtpSettingsPage = () => {
  const [settingsForm] = Form.useForm<SmtpSettings>();
  const [testForm] = Form.useForm<SmtpTestPayload>();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const settingsFormProps = useStandardFormProps({
    form: settingsForm,
    initialValues: { port: 25, authEnabled: true, startTlsEnabled: true, sslEnabled: false },
  });
  const testFormProps = useStandardFormProps({
    form: testForm,
    initialValues: { subject: 'SMTP 测试邮件', content: '这是一封来自系统的 SMTP 测试邮件。' },
  });

  const loadSettings = async () => {
    setLoading(true);
    try {
      const settings = await systemService.smtpSettings({ autoRedirectOnUnauthorized: false });
      settingsForm.setFieldsValue({
        ...settings,
        password: '',
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadSettings();
  }, []);

  const handleSave = async () => {
    setSaving(true);
    try {
      const values = await settingsForm.validateFields();
      await systemService.updateSmtpSettings(values, { autoRedirectOnUnauthorized: false });
      message.success('SMTP 配置已保存');
      await loadSettings();
    } finally {
      setSaving(false);
    }
  };

  const handleTest = async () => {
    setTesting(true);
    try {
      const values = await testForm.validateFields();
      const result = await systemService.testSmtpSettings(values, { autoRedirectOnUnauthorized: false });
      message.success(result.message || '测试邮件已发送');
    } finally {
      setTesting(false);
    }
  };

  return (
    <PageContainer
      title="SMTP 配置"
      content={
        <Alert
          showIcon
          type="info"
          message="默认邮件服务"
          description="系统默认只提供 SMTP 基础服务配置。当前页面只维护邮件基础设施，不承担短信验证码或 2FA 业务逻辑。"
        />
      }
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card
          title={
            <Space>
              <MailOutlined />
              SMTP 基础配置
            </Space>
          }
          loading={loading}
          extra={<Button onClick={() => void loadSettings()}>刷新</Button>}
        >
          <Form {...settingsFormProps}>
            <Form.Item name="host" label="SMTP 主机" rules={[{ required: true, message: '请输入 SMTP 主机' }]}>
              <Input placeholder="smtp.example.com" />
            </Form.Item>
            <Form.Item name="port" label="SMTP 端口" rules={[{ required: true, message: '请输入 SMTP 端口' }]}>
              <InputNumber style={{ width: '100%' }} min={1} max={65535} />
            </Form.Item>
            <Form.Item name="username" label="SMTP 用户名" rules={[{ required: true, message: '请输入 SMTP 用户名' }]}>
              <Input placeholder="username@example.com" />
            </Form.Item>
            <Form.Item name="password" label="SMTP 密码">
              <Input.Password placeholder="留空则保留现有密码" />
            </Form.Item>
            <Form.Item name="from" label="发件人地址" rules={[{ required: true, message: '请输入发件人地址' }]}>
              <Input placeholder="noreply@example.com" />
            </Form.Item>
            <Form.Item name="authEnabled" label="启用认证" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="startTlsEnabled" label="启用 STARTTLS" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="sslEnabled" label="启用 SSL" valuePropName="checked">
              <Switch />
            </Form.Item>
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <Button type="primary" loading={saving} onClick={() => void handleSave()}>
                保存
              </Button>
            </div>
          </Form>
        </Card>

        <Card title="SMTP 测试发送" loading={loading}>
          <Form {...testFormProps}>
            <Form.Item name="toEmail" label="收件人邮箱" rules={[{ required: true, message: '请输入收件人邮箱' }, { type: 'email', message: '请输入有效邮箱地址' }]}>
              <Input placeholder="recipient@example.com" />
            </Form.Item>
            <Form.Item name="subject" label="邮件主题">
              <Input />
            </Form.Item>
            <Form.Item name="content" label="邮件内容">
              <Input.TextArea rows={6} />
            </Form.Item>
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <Button type="primary" loading={testing} onClick={() => void handleTest()}>
                发送测试邮件
              </Button>
            </div>
          </Form>
        </Card>

        <Card title="说明">
          <Typography.Paragraph style={{ marginBottom: 0 }}>
            SMTP 是平台默认邮件通道。当前页面只维护邮件基础设施，不承担短信或 2FA 业务逻辑。
          </Typography.Paragraph>
        </Card>
      </Space>
    </PageContainer>
  );
};

export default SmtpSettingsPage;
