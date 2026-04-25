import { Alert, Form, Input, Modal, Space, type FormProps } from 'antd';
import { useEffect } from 'react';
import type { SecondFactorChallenge } from '@/types/api';

interface ContactBindModalProps {
  open: boolean;
  title: string;
  description: string;
  label: string;
  placeholder: string;
  autoComplete?: string;
  inputMode?: React.HTMLAttributes<HTMLInputElement>['inputMode'];
  submitting: boolean;
  alertMessage: string | null;
  verificationRequired: boolean;
  verificationChallenge: SecondFactorChallenge | null;
  okText: string;
  initialValue?: string;
  formProps: FormProps;
  onCancel: () => void;
  onConfirm: () => void;
}

export const ContactBindModal = ({
  open,
  title,
  description,
  label,
  placeholder,
  autoComplete,
  inputMode,
  submitting,
  alertMessage,
  verificationRequired,
  verificationChallenge,
  okText,
  initialValue,
  formProps,
  onCancel,
  onConfirm,
}: ContactBindModalProps) => {
  useEffect(() => {
    if (!open) {
      return;
    }
    formProps.form?.setFieldsValue({ value: initialValue || '', verificationCode: undefined });
  }, [formProps.form, initialValue, open]);

  return (
    <Modal
      title={title}
      open={open}
      onCancel={onCancel}
      onOk={onConfirm}
      confirmLoading={submitting}
      okText={okText}
      cancelText="取消"
      destroyOnClose
      maskClosable={false}
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Alert showIcon type="info" message={title} description={description} />
        {verificationRequired ? (
          <Alert
            showIcon
            type="info"
            message="需要验证码确认"
            description={
              verificationChallenge
                ? verificationChallenge.promptMessage ||
                  (verificationChallenge.maskedContact
                    ? `验证码已发送至 ${verificationChallenge.maskedContact}，请输入收到的验证码继续。`
                    : '验证码已发送，请输入收到的验证码继续。')
                : '点击发送验证码后，需要输入收到的验证码才能完成绑定。'
            }
          />
        ) : null}
        {alertMessage ? <Alert showIcon type="error" message={alertMessage} /> : null}
        <Form {...formProps}>
          <Form.Item
            name="value"
            label={label}
            rules={[
              { required: true, message: `请输入${label}` },
              ...(label === '邮箱' ? [{ type: 'email' as const, message: '请输入有效邮箱地址' }] : []),
              ...(label === '手机号' ? [{ pattern: /^1[3-9]\d{9}$/, message: '请输入有效手机号' }] : []),
            ]}
          >
            <Input placeholder={placeholder} autoComplete={autoComplete} inputMode={inputMode} />
          </Form.Item>
          {verificationRequired && verificationChallenge ? (
            <Form.Item
              name="verificationCode"
              label="验证码"
              rules={[
                { required: true, message: '请输入验证码' },
                { pattern: /^\d{6}$/, message: '验证码必须为 6 位数字' },
              ]}
            >
              <Input placeholder="请输入收到的 6 位验证码" autoComplete="one-time-code" inputMode="numeric" maxLength={6} />
            </Form.Item>
          ) : null}
        </Form>
      </Space>
    </Modal>
  );
};
