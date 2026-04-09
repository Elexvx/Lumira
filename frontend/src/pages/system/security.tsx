import { useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Form, InputNumber, Radio, Space, Switch, Tabs, Typography, message } from 'antd';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { DEFAULT_SECURITY_SETTINGS, normalizeSecuritySettings } from '@/auth/securitySettings';
import { loadSecuritySettings, saveSecuritySettings } from '@/auth/session';
import type { SecuritySettings } from '@/types/api';

const tokenFieldNames: (keyof SecuritySettings)[] = [
  'idleTimeoutSeconds',
  'accessTokenExpireSeconds',
  'refreshTokenExpireSeconds',
  'allowMultiDeviceLogin',
];

const captchaFieldNames: (keyof SecuritySettings)[] = ['captchaEnabled', 'captchaType'];

const defenseFieldNames: (keyof SecuritySettings)[] = ['loginDefenseWindowMinutes', 'loginMaxValidationAttempts', 'loginMaxFailureCount'];

const passwordFieldNames: (keyof SecuritySettings)[] = [
  'passwordMinLength',
  'passwordRequireUppercase',
  'passwordRequireLowercase',
  'passwordRequireSpecialCharacter',
  'passwordAllowConsecutiveCharacters',
];

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

  const handleSave = async (fieldNames: (keyof SecuritySettings)[]) => {
    setSaving(true);
    try {
      const partialValues = await form.validateFields(fieldNames as string[]);
      const mergedValues = normalizeSecuritySettings({
        ...form.getFieldsValue(true),
        ...partialValues,
      });
      const settings = await saveSecuritySettings(mergedValues);
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
      // 统一请求层会提示错误，这里只做收尾。
    } finally {
      setSaving(false);
    }
  };

  const handleReset = () => {
    form.setFieldsValue(DEFAULT_SECURITY_SETTINGS);
  };

  const renderFooter = (fieldNames: (keyof SecuritySettings)[]) => (
    <Space>
      <Button onClick={handleReset}>恢复默认值</Button>
      <Button type="primary" loading={saving} onClick={() => void handleSave(fieldNames)}>
        保存设置
      </Button>
    </Space>
  );

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
          <Form form={form} initialValues={DEFAULT_SECURITY_SETTINGS} layout="vertical">
            <Tabs
              defaultActiveKey="token"
              items={[
              {
                key: 'token',
                label: 'Token 策略',
                children: (
                  <Space direction="vertical" size={16} style={{ width: '100%' }}>
                    <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                      这部分配置决定登录会话、Access Token 和 Refresh Token 的生命周期。
                    </Typography.Paragraph>
                    <Form.Item
                      name="idleTimeoutSeconds"
                      label="空闲超时（秒）"
                      rules={[{ required: true, message: '请输入空闲超时时间' }, { type: 'number', min: 1, message: '必须大于 0' }]}
                      extra="用户在无操作状态下允许保持登录的秒数。"
                    >
                      <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="例如：1800" />
                    </Form.Item>
                    <Form.Item
                      name="accessTokenExpireSeconds"
                      label="Access Token 过期时间（秒）"
                      rules={[{ required: true, message: '请输入 Access Token 过期时间' }, { type: 'number', min: 1, message: '必须大于 0' }]}
                      extra="Access Token 的有效秒数。"
                    >
                      <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="例如：1800" />
                    </Form.Item>
                    <Form.Item
                      name="refreshTokenExpireSeconds"
                      label="Refresh Token 刷新时限（秒）"
                      rules={[{ required: true, message: '请输入 Refresh Token 刷新时限' }, { type: 'number', min: 1, message: '必须大于 0' }]}
                      extra="Refresh Token 的有效秒数。"
                    >
                      <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="例如：604800" />
                    </Form.Item>
                    <Form.Item
                      name="allowMultiDeviceLogin"
                      label="多设备登录"
                      valuePropName="checked"
                      extra="关闭后，同一账号在新的设备登录时，旧设备的会话将自动失效。"
                    >
                      <Switch checkedChildren="开启" unCheckedChildren="关闭" />
                    </Form.Item>
                    {renderFooter(tokenFieldNames)}
                  </Space>
                ),
              },
              {
                key: 'captcha',
                label: '验证码设置',
                children: (
                  <Space direction="vertical" size={16} style={{ width: '100%' }}>
                    <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                      开启后，登录页会要求完成人机验证码。
                    </Typography.Paragraph>
                    <Form.Item
                      name="captchaEnabled"
                      label="启用人机验证码"
                      valuePropName="checked"
                      extra="关闭后登录页不会展示验证码。"
                    >
                      <Switch checkedChildren="开启" unCheckedChildren="关闭" />
                    </Form.Item>
                    <Form.Item
                      name="captchaType"
                      label="验证码类型"
                      extra="当前仅保留图片验证码。"
                    >
                      <Radio.Group
                        optionType="button"
                        buttonStyle="solid"
                        options={[{ label: '图片验证码', value: 'IMAGE' }]}
                      />
                    </Form.Item>
                    {renderFooter(captchaFieldNames)}
                  </Space>
                ),
              },
              {
                key: 'defense',
                label: '防御阈值',
                children: (
                  <Space direction="vertical" size={16} style={{ width: '100%' }}>
                    <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                      用于限制账号与 IP 维度的高频登录尝试，减少爆破和脚本攻击。
                    </Typography.Paragraph>
                    <Form.Item
                      name="loginDefenseWindowMinutes"
                      label="统计窗口（分钟）"
                      rules={[{ required: true, message: '请输入统计窗口' }, { type: 'number', min: 1, message: '必须大于 0' }]}
                      extra="用于统计高频访问的时间窗口大小。"
                    >
                      <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="例如：5" />
                    </Form.Item>
                    <Form.Item
                      name="loginMaxValidationAttempts"
                      label="最大验证次数"
                      rules={[{ required: true, message: '请输入最大验证次数' }, { type: 'number', min: 1, message: '必须大于 0' }]}
                      extra="统计窗口内允许的最大登录验证请求次数，超过后将拦截。"
                    >
                      <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="例如：100" />
                    </Form.Item>
                    <Form.Item
                      name="loginMaxFailureCount"
                      label="最大错误次数"
                      rules={[{ required: true, message: '请输入最大错误次数' }, { type: 'number', min: 1, message: '必须大于 0' }]}
                      extra="统计窗口内允许的最大登录失败次数，超过后将拦截。"
                    >
                      <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="例如：10" />
                    </Form.Item>
                    {renderFooter(defenseFieldNames)}
                  </Space>
                ),
              },
              {
                key: 'password',
                label: '密码规范设置',
                children: (
                  <Space direction="vertical" size={16} style={{ width: '100%' }}>
                    <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                      这些规则会直接作用于用户新增、重置密码和修改密码的服务端校验。
                    </Typography.Paragraph>
                    <Form.Item
                      name="passwordMinLength"
                      label="最短长度"
                      rules={[{ required: true, message: '请输入最短长度' }, { type: 'number', min: 1, message: '必须大于 0' }]}
                      extra="用户密码允许的最少字符数。"
                    >
                      <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="例如：6" />
                    </Form.Item>
                    <Form.Item
                      name="passwordRequireUppercase"
                      label="必须包含大写字母"
                      valuePropName="checked"
                      extra="强制密码中必须包含 A-Z。"
                    >
                      <Switch checkedChildren="开启" unCheckedChildren="关闭" />
                    </Form.Item>
                    <Form.Item
                      name="passwordRequireLowercase"
                      label="必须包含小写字母"
                      valuePropName="checked"
                      extra="强制密码中必须包含 a-z。"
                    >
                      <Switch checkedChildren="开启" unCheckedChildren="关闭" />
                    </Form.Item>
                    <Form.Item
                      name="passwordRequireSpecialCharacter"
                      label="必须包含特殊字符"
                      valuePropName="checked"
                      extra="强制密码中必须包含特殊字符，例如 !@#$%^&*。"
                    >
                      <Switch checkedChildren="开启" unCheckedChildren="关闭" />
                    </Form.Item>
                    <Form.Item
                      name="passwordAllowConsecutiveCharacters"
                      label="允许连续字符"
                      valuePropName="checked"
                      extra="关闭后，密码中不能包含类似 123 或 abc 的连续字符。"
                    >
                      <Switch checkedChildren="允许" unCheckedChildren="禁止" />
                    </Form.Item>
                    {renderFooter(passwordFieldNames)}
                  </Space>
                ),
              },
              ]}
            />
          </Form>
        </Card>
      </div>
    </PageContainer>
  );
};

export default SecuritySettingsPage;
