import { useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Form, InputNumber, Space, Typography, message } from 'antd';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettings';
import { loadSecuritySettings, saveSecuritySettings } from '@/auth/session';
import type { SecuritySettings } from '@/types/api';

const SecuritySettingsPage = () => {
  const [form] = Form.useForm<SecuritySettings>();
  const { setInitialState } = useInitialStateModel();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;
    const load = async () => {
      setLoading(true);
      const settings = await loadSecuritySettings();
      if (!active) {
        return;
      }
      form.setFieldsValue(settings);
      setInitialState((prev) =>
        prev
          ? {
              ...prev,
              securitySettings: settings,
            }
          : prev,
      );
      setLoading(false);
    };

    void load();
    return () => {
      active = false;
    };
  }, [form, setInitialState]);

  const handleSave = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      const settings = await saveSecuritySettings(values);
      form.setFieldsValue(settings);
      setInitialState((prev) =>
        prev
          ? {
              ...prev,
              securitySettings: settings,
            }
          : prev,
      );
      message.success('安全设置已保存，并已立即生效');
    } catch {
      // 错误已经由统一请求层提示，这里只负责收尾。
    } finally {
      setSaving(false);
    }
  };

  const handleReset = () => {
    form.setFieldsValue(DEFAULT_SECURITY_SETTINGS);
  };

  return (
    <PageContainer
      className="saas-management-page saas-crud-page"
      ghost
      title="安全设置"
      style={{ height: '100%', minHeight: 0 }}
      content={null}
    >
      <div className="saas-management-page-body">
        <Card className="saas-crud-form-card" loading={loading} bodyStyle={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          <div>
            <Typography.Title level={5} style={{ marginBottom: 8 }}>
              会话生命周期
            </Typography.Title>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              下列配置单位均为秒。
            </Typography.Paragraph>
          </div>

          <Form form={form} layout="vertical" initialValues={DEFAULT_SECURITY_SETTINGS}>
            <Form.Item
              name="idleTimeoutSeconds"
              label="空闲超时（秒）"
              rules={[{ required: true, message: '请输入空闲超时时间' }, { type: 'number', min: 1, message: '必须大于 0' }]}
            >
              <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="例如：1800" />
            </Form.Item>

            <Form.Item
              name="accessTokenExpireSeconds"
              label="Access Token 过期时间（秒）"
              rules={[{ required: true, message: '请输入 Access Token 过期时间' }, { type: 'number', min: 1, message: '必须大于 0' }]}
            >
              <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="例如：1800" />
            </Form.Item>

            <Form.Item
              name="refreshTokenExpireSeconds"
              label="Refresh Token 刷新时限（秒）"
              rules={[{ required: true, message: '请输入 Refresh Token 刷新时限' }, { type: 'number', min: 1, message: '必须大于 0' }]}
            >
              <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="例如：604800" />
            </Form.Item>
          </Form>

          <Space>
            <Button onClick={handleReset}>恢复默认值</Button>
            <Button type="primary" loading={saving} onClick={handleSave}>
              保存设置
            </Button>
          </Space>
        </Card>
      </div>
    </PageContainer>
  );
};

export default SecuritySettingsPage;
