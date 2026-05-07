import { useEffect } from 'react';
import { Button, Drawer, Form, Input, Space } from 'antd';
import { useIntl } from '@umijs/max';
import type { FormInstance } from 'antd';
import type { SecuritySettings } from '@/types/api';

interface TopActionsPasswordDrawerProps {
  open: boolean;
  isMobile: boolean;
  form: FormInstance<{
    currentPassword?: string;
    newPassword?: string;
    confirmPassword?: string;
  }>;
  securitySettings: SecuritySettings;
  onClose: () => void;
  onFinish: (values: {
    currentPassword?: string;
    newPassword?: string;
    confirmPassword?: string;
  }) => Promise<void> | void;
}

const buildPasswordPolicyHint = (securitySettings: SecuritySettings, intl: ReturnType<typeof useIntl>) => {
  const parts: string[] = [];
  const minLength = Math.max(1, Number(securitySettings.passwordMinLength || 0));
  parts.push(
    intl.formatMessage(
      { id: 'nav.user.password.minLength', defaultMessage: 'At least {length} characters' },
      { length: minLength },
    ),
  );
  if (securitySettings.passwordRequireUppercase) {
    parts.push(intl.formatMessage({ id: 'nav.user.password.requireUppercase', defaultMessage: 'Must include uppercase letters' }));
  }
  if (securitySettings.passwordRequireLowercase) {
    parts.push(intl.formatMessage({ id: 'nav.user.password.requireLowercase', defaultMessage: 'Must include lowercase letters' }));
  }
  if (securitySettings.passwordRequireSpecialCharacter) {
    parts.push(intl.formatMessage({ id: 'nav.user.password.requireSpecial', defaultMessage: 'Must include special characters' }));
  }
  return parts.join('，');
};

export const TopActionsPasswordDrawer = ({
  open,
  isMobile,
  form,
  securitySettings,
  onClose,
  onFinish,
}: TopActionsPasswordDrawerProps) => {
  const intl = useIntl();
  const passwordPolicyHint = buildPasswordPolicyHint(securitySettings, intl);

  useEffect(() => {
    if (!open) {
      form.resetFields();
    }
  }, [form, open]);

  return (
    <Drawer
      title={intl.formatMessage({ id: 'nav.user.changePassword', defaultMessage: '修改密码' })}
      open={open}
      width={isMobile ? '100%' : 420}
      destroyOnHidden
      onClose={onClose}
      footer={
        <Space className="saas-user-password__footer">
          <Button onClick={onClose}>
            {intl.formatMessage({ id: 'common.cancel', defaultMessage: '取消' })}
          </Button>
          <Button type="primary" onClick={() => void form.submit()}>
            {intl.formatMessage({ id: 'common.save', defaultMessage: '保存' })}
          </Button>
        </Space>
      }
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={onFinish}
        initialValues={{ currentPassword: '', newPassword: '', confirmPassword: '' }}
      >
        <Form.Item
          name="currentPassword"
          label={intl.formatMessage({ id: 'nav.user.password.current', defaultMessage: '当前密码' })}
          rules={[{ required: true, message: intl.formatMessage({ id: 'nav.user.password.enterCurrent', defaultMessage: '请输入当前密码' }) }]}
        >
          <Input.Password autoComplete="current-password" />
        </Form.Item>
        <Form.Item
          name="newPassword"
          label={intl.formatMessage({ id: 'nav.user.password.new', defaultMessage: '新密码' })}
          extra={passwordPolicyHint}
          rules={[
            { required: true, message: intl.formatMessage({ id: 'nav.user.password.enterNew', defaultMessage: '请输入新密码' }) },
            {
              min: Math.max(1, Number(securitySettings.passwordMinLength || 0)),
              message: intl.formatMessage(
                { id: 'nav.user.password.minLength', defaultMessage: '密码长度至少为 {length} 位' },
                { length: securitySettings.passwordMinLength || 1 },
              ),
            },
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          label={intl.formatMessage({ id: 'nav.user.password.confirm', defaultMessage: '确认新密码' })}
          dependencies={['newPassword']}
          rules={[
            { required: true, message: intl.formatMessage({ id: 'nav.user.password.enterConfirm', defaultMessage: '请再次输入新密码' }) },
            ({ getFieldValue }) => ({
              validator: async (_, value) => {
                if (!value || value === getFieldValue('newPassword')) {
                  return Promise.resolve();
                }
                return Promise.reject(new Error(intl.formatMessage({ id: 'nav.user.password.confirmMismatch', defaultMessage: '两次密码输入不一致' })));
              },
            }),
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
      </Form>
    </Drawer>
  );
};
