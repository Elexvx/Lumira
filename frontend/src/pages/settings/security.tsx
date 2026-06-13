import { Button, Card, Form, InputNumber, Radio, Space, Switch, Tabs } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import { useEffect, useState } from 'react';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { useStandardFormProps } from '@/features/form/config';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettingsTypes';
import { normalizeSecuritySettings } from '@/auth/securitySettingsNormalize';
import { loadSecuritySettings, saveSecuritySettings } from '@/auth/sessionSecurity';
import type { SecuritySettings } from '@/types/api';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { useResponsive } from '@/hooks/useResponsive';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { formatMessage } from '@umijs/max';
import { SliderCaptchaBox } from '@/components/captcha/SliderCaptchaBox';

const fieldWidthStyle = { width: '100%' };
const tokenFieldNames: (keyof SecuritySettings)[] = [
  'idleTimeoutSeconds',
  'accessTokenExpireSeconds',
  'refreshTokenExpireSeconds',
  'allowMultiDeviceLogin',
];
const captchaFieldNames: (keyof SecuritySettings)[] = ['captchaEnabled', 'captchaType'];
const defenseFieldNames: (keyof SecuritySettings)[] = [
  'loginDefenseWindowMinutes',
  'loginMaxValidationAttempts',
  'loginMaxFailureCount',
  'verificationCodeExpireSeconds',
  'verificationCodeCooldownSeconds',
];
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
  const actionPermission = useActionPermission();
  const { isMobile } = useResponsive();
  const canUpdate = actionPermission.can('system:config:update');
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const cardPaddingTop = resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile)[0];
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const captchaType = Form.useWatch('captchaType', form) || DEFAULT_SECURITY_SETTINGS.captchaType;
  const formProps = useStandardFormProps({
    form,
    initialValues: DEFAULT_SECURITY_SETTINGS,
  });

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
      message.success(formatMessage({ id: 'page.security.saved', defaultMessage: 'Security settings saved and applied immediately' }));
    } catch {
      // 统一请求层会提示错误，这里只做收尾。
    } finally {
      setSaving(false);
    }
  };

  const renderFooter = (fieldNames: (keyof SecuritySettings)[]) => (
    <Space>
      <Button type="primary" loading={saving} disabled={!canUpdate} onClick={() => void handleSave(fieldNames)}>
        {formatMessage({ id: 'page.security.save', defaultMessage: 'Save settings' })}
      </Button>
    </Space>
  );

  return (
    <ManagementPage className="saas-crud-page" title={formatMessage({ id: 'page.security.title', defaultMessage: 'Security settings' })}>
      <ManagementPageBody>
        <Card
          className="saas-crud-form-card"
          loading={loading}
          bodyStyle={{
            paddingTop: cardPaddingTop,
            display: 'flex',
            flexDirection: 'column',
            gap: sectionGap,
          }}
        >
          <Form {...formProps}>
            <Tabs
              defaultActiveKey="token"
              items={[
                {
                  key: 'token',
                  label: formatMessage({ id: 'page.security.tokenStrategy', defaultMessage: 'Token strategy' }),
                  children: (
                    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
                      <Form.Item
                        name="idleTimeoutSeconds"
                        label={formatMessage({ id: 'page.security.idleTimeout', defaultMessage: 'Idle timeout (seconds)' })}
                        rules={[
                          { required: true, message: formatMessage({ id: 'page.security.idleTimeout', defaultMessage: 'Please enter the idle timeout' }) },
                          { type: 'number', min: 1, message: formatMessage({ id: 'common.mustBeGreaterThanZero', defaultMessage: 'Must be greater than 0' }) },
                        ]}
                        extra={formatMessage({ id: 'page.security.idleTimeout.help', defaultMessage: 'The number of seconds a user can stay logged in while idle.' })}
                      >
                        <InputNumber min={1} precision={0} style={fieldWidthStyle} placeholder={formatMessage({ id: 'page.security.example1800', defaultMessage: 'e.g. 1800' })} />
                      </Form.Item>
                      <Form.Item
                        name="accessTokenExpireSeconds"
                        label={formatMessage({ id: 'page.security.accessTokenExpire', defaultMessage: 'Access Token expiry (seconds)' })}
                        rules={[
                          { required: true, message: formatMessage({ id: 'page.security.accessTokenExpire', defaultMessage: 'Please enter the Access Token expiry time' }) },
                          { type: 'number', min: 1, message: formatMessage({ id: 'common.mustBeGreaterThanZero', defaultMessage: 'Must be greater than 0' }) },
                        ]}
                        extra={formatMessage({ id: 'page.security.accessTokenExpire.help', defaultMessage: 'The number of seconds an Access Token remains valid.' })}
                      >
                        <InputNumber min={1} precision={0} style={fieldWidthStyle} placeholder={formatMessage({ id: 'page.security.example1800', defaultMessage: 'e.g. 1800' })} />
                      </Form.Item>
                      <Form.Item
                        name="refreshTokenExpireSeconds"
                        label={formatMessage({ id: 'page.security.refreshTokenExpire', defaultMessage: 'Refresh Token expiry (seconds)' })}
                        rules={[
                          { required: true, message: formatMessage({ id: 'page.security.refreshTokenExpire', defaultMessage: 'Please enter the Refresh Token expiry time' }) },
                          { type: 'number', min: 1, message: formatMessage({ id: 'common.mustBeGreaterThanZero', defaultMessage: 'Must be greater than 0' }) },
                        ]}
                        extra={formatMessage({ id: 'page.security.refreshTokenExpire.help', defaultMessage: 'The number of seconds a Refresh Token remains valid.' })}
                      >
                        <InputNumber min={1} precision={0} style={fieldWidthStyle} placeholder={formatMessage({ id: 'page.security.example604800', defaultMessage: 'e.g. 604800' })} />
                      </Form.Item>
                      <Form.Item
                        name="allowMultiDeviceLogin"
                        label={formatMessage({ id: 'page.security.multiDeviceLogin', defaultMessage: 'Multi-device login' })}
                        valuePropName="checked"
                        extra={formatMessage({ id: 'page.security.multiDeviceLogin.help', defaultMessage: 'If disabled, the previous session will expire when the account logs in on a new device.' })}
                      >
                        <Switch />
                      </Form.Item>
                      {renderFooter(tokenFieldNames)}
                    </Space>
                  ),
                },
                {
                  key: 'captcha',
                  label: formatMessage({ id: 'page.security.captchaSettings', defaultMessage: 'Captcha settings' }),
                  children: (
                    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
                      <Form.Item
                        name="captchaEnabled"
                        label={formatMessage({ id: 'page.security.enableCaptcha', defaultMessage: 'Enable captcha' })}
                        valuePropName="checked"
                        extra={formatMessage({ id: 'page.security.enableCaptcha.help', defaultMessage: 'When disabled, the login page will not show a captcha.' })}
                      >
                        <Switch />
                      </Form.Item>
                      <Form.Item
                        name="captchaType"
                        label={formatMessage({ id: 'page.security.captchaType', defaultMessage: 'Captcha type' })}
                        extra={formatMessage({ id: 'page.security.captchaType.help', defaultMessage: 'Image captchas require typing characters; slider captchas require dragging the puzzle piece.' })}
                      >
                        <Radio.Group
                          optionType="button"
                          buttonStyle="solid"
                          options={[
                            { label: formatMessage({ id: 'page.security.captcha.image', defaultMessage: 'Image captcha' }), value: 'IMAGE' },
                            { label: formatMessage({ id: 'page.security.captcha.slider', defaultMessage: 'Slider captcha' }), value: 'SLIDER' },
                          ]}
                        />
                      </Form.Item>
                      {captchaType === 'SLIDER' ? (
                        <Card size="small" title={formatMessage({ id: 'page.security.sliderPreview', defaultMessage: 'Background image and puzzle preview' })}>
                          <SliderCaptchaBox
                            onVerified={() => {
                              message.success(formatMessage({ id: 'page.security.sliderPreviewSuccess', defaultMessage: 'Slider captcha preview verified' }));
                            }}
                          />
                        </Card>
                      ) : null}
                      {renderFooter(captchaFieldNames)}
                    </Space>
                  ),
                },
                {
                  key: 'defense',
                  label: formatMessage({ id: 'page.security.defenseThreshold', defaultMessage: 'Defense thresholds' }),
                  children: (
                    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
                      <Form.Item
                        name="loginDefenseWindowMinutes"
                        label={formatMessage({ id: 'page.security.window', defaultMessage: 'Window (minutes)' })}
                        rules={[
                          { required: true, message: formatMessage({ id: 'page.security.window', defaultMessage: 'Please enter the window size' }) },
                          { type: 'number', min: 1, message: formatMessage({ id: 'common.mustBeGreaterThanZero', defaultMessage: 'Must be greater than 0' }) },
                        ]}
                        extra={formatMessage({ id: 'page.security.window.help', defaultMessage: 'The time window used to track high-frequency access.' })}
                      >
                        <InputNumber min={1} precision={0} style={fieldWidthStyle} placeholder={formatMessage({ id: 'page.security.example5', defaultMessage: 'e.g. 5' })} />
                      </Form.Item>
                      <Form.Item
                        name="loginMaxValidationAttempts"
                        label={formatMessage({ id: 'page.security.maxAttempts', defaultMessage: 'Max validation attempts' })}
                        rules={[
                          { required: true, message: formatMessage({ id: 'page.security.maxAttempts', defaultMessage: 'Please enter the max validation attempts' }) },
                          { type: 'number', min: 1, message: formatMessage({ id: 'common.mustBeGreaterThanZero', defaultMessage: 'Must be greater than 0' }) },
                        ]}
                        extra={formatMessage({ id: 'page.security.maxAttempts.help', defaultMessage: 'The max number of login validation requests allowed in the window before blocking.' })}
                      >
                        <InputNumber min={1} precision={0} style={fieldWidthStyle} placeholder={formatMessage({ id: 'page.security.example100', defaultMessage: 'e.g. 100' })} />
                      </Form.Item>
                      <Form.Item
                        name="loginMaxFailureCount"
                        label={formatMessage({ id: 'page.security.maxFailures', defaultMessage: 'Max failure count' })}
                        rules={[
                          { required: true, message: formatMessage({ id: 'page.security.maxFailures', defaultMessage: 'Please enter the max failure count' }) },
                          { type: 'number', min: 1, message: formatMessage({ id: 'common.mustBeGreaterThanZero', defaultMessage: 'Must be greater than 0' }) },
                        ]}
                        extra={formatMessage({ id: 'page.security.maxFailures.help', defaultMessage: 'The max number of login failures allowed in the window before blocking.' })}
                      >
                        <InputNumber min={1} precision={0} style={fieldWidthStyle} placeholder={formatMessage({ id: 'page.security.example10', defaultMessage: 'e.g. 10' })} />
                      </Form.Item>
                      <Form.Item
                        name="verificationCodeExpireSeconds"
                        label={formatMessage({ id: 'page.security.verificationCodeExpire', defaultMessage: 'Verification code expiry (seconds)' })}
                        rules={[
                          { required: true, message: formatMessage({ id: 'page.security.verificationCodeExpire.required', defaultMessage: 'Please enter the verification code expiry time' }) },
                          { type: 'number', min: 1, message: formatMessage({ id: 'common.mustBeGreaterThanZero', defaultMessage: 'Must be greater than 0' }) },
                        ]}
                        extra={formatMessage({ id: 'page.security.verificationCodeExpire.help', defaultMessage: 'SMS and email verification codes expire after this number of seconds.' })}
                      >
                        <InputNumber min={1} precision={0} style={fieldWidthStyle} placeholder={formatMessage({ id: 'page.security.example300', defaultMessage: 'e.g. 300' })} />
                      </Form.Item>
                      <Form.Item
                        name="verificationCodeCooldownSeconds"
                        label={formatMessage({ id: 'page.security.verificationCodeCooldown', defaultMessage: 'Send countdown (seconds)' })}
                        rules={[
                          { required: true, message: formatMessage({ id: 'page.security.verificationCodeCooldown.required', defaultMessage: 'Please enter the send countdown seconds' }) },
                          { type: 'number', min: 1, message: formatMessage({ id: 'common.mustBeGreaterThanZero', defaultMessage: 'Must be greater than 0' }) },
                        ]}
                        extra={formatMessage({ id: 'page.security.verificationCodeCooldown.help', defaultMessage: 'After one verification code is sent, the send button counts down and cannot send again during this time.' })}
                      >
                        <InputNumber min={1} precision={0} style={fieldWidthStyle} placeholder={formatMessage({ id: 'page.security.example60', defaultMessage: 'e.g. 60' })} />
                      </Form.Item>
                      {renderFooter(defenseFieldNames)}
                    </Space>
                  ),
                },
                {
                  key: 'password',
                  label: formatMessage({ id: 'page.security.passwordPolicy', defaultMessage: 'Password policy' }),
                  children: (
                    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
                      <Form.Item
                        name="passwordMinLength"
                        label={formatMessage({ id: 'page.security.password.minLength', defaultMessage: 'Minimum length' })}
                        rules={[
                          { required: true, message: formatMessage({ id: 'page.security.password.minLength', defaultMessage: 'Please enter the minimum length' }) },
                          { type: 'number', min: 1, message: formatMessage({ id: 'common.mustBeGreaterThanZero', defaultMessage: 'Must be greater than 0' }) },
                        ]}
                        extra={formatMessage({ id: 'page.security.password.minLength.help', defaultMessage: 'The minimum number of characters allowed in a user password.' })}
                      >
                        <InputNumber min={1} precision={0} style={fieldWidthStyle} placeholder={formatMessage({ id: 'page.security.example6', defaultMessage: 'e.g. 6' })} />
                      </Form.Item>
                      <Form.Item
                        name="passwordRequireUppercase"
                        label={formatMessage({ id: 'page.security.password.uppercase', defaultMessage: 'Must include uppercase letters' })}
                        valuePropName="checked"
                        extra={formatMessage({ id: 'page.security.password.uppercase.help', defaultMessage: 'Force passwords to include A-Z.' })}
                      >
                        <Switch />
                      </Form.Item>
                      <Form.Item
                        name="passwordRequireLowercase"
                        label={formatMessage({ id: 'page.security.password.lowercase', defaultMessage: 'Must include lowercase letters' })}
                        valuePropName="checked"
                        extra={formatMessage({ id: 'page.security.password.lowercase.help', defaultMessage: 'Force passwords to include a-z.' })}
                      >
                        <Switch />
                      </Form.Item>
                      <Form.Item
                        name="passwordRequireSpecialCharacter"
                        label={formatMessage({ id: 'page.security.password.special', defaultMessage: 'Must include special characters' })}
                        valuePropName="checked"
                        extra={formatMessage({ id: 'page.security.password.special.help', defaultMessage: 'Force passwords to include special characters such as !@#$%^&*.' })}
                      >
                        <Switch />
                      </Form.Item>
                      <Form.Item
                        name="passwordAllowConsecutiveCharacters"
                        label={formatMessage({ id: 'page.security.password.consecutive', defaultMessage: 'Allow consecutive characters' })}
                        valuePropName="checked"
                        extra={formatMessage({ id: 'page.security.password.consecutive.help', defaultMessage: 'When disabled, passwords cannot contain consecutive sequences such as 123 or abc.' })}
                      >
                        <Switch />
                      </Form.Item>
                      {renderFooter(passwordFieldNames)}
                    </Space>
                  ),
                },
              ]}
            />
          </Form>
        </Card>
      </ManagementPageBody>
    </ManagementPage>
  );
};

export default SecuritySettingsPage;
