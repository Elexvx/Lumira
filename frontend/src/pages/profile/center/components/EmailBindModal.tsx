import { Alert, Form, Input, Modal, Space, type FormProps } from 'antd';

interface EmailBindModalProps {
  open: boolean;
  submitting: boolean;
  alertMessage: string | null;
  formProps: FormProps;
  onCancel: () => void;
  onConfirm: () => void;
}

export const EmailBindModal = ({ open, submitting, alertMessage, formProps, onCancel, onConfirm }: EmailBindModalProps) => (
  <Modal
    title="补充邮箱"
    open={open}
    onCancel={onCancel}
    onOk={onConfirm}
    confirmLoading={submitting}
    okText="保存并继续"
    cancelText="取消"
    destroyOnClose
    maskClosable={false}
  >
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Alert
        showIcon
        type="info"
        message="先绑定邮箱，再继续验证绑定"
        description="当前选择的验证方式需要邮箱。补充邮箱后，系统会自动返回继续绑定验证方式。"
      />
      {alertMessage ? <Alert showIcon type="error" message={alertMessage} /> : null}
      <Form {...formProps}>
        <Form.Item
          name="email"
          label="邮箱"
          rules={[
            { required: true, message: '请输入邮箱' },
            { type: 'email', message: '请输入有效邮箱地址' },
          ]}
        >
          <Input placeholder="请输入邮箱地址" autoComplete="email" />
        </Form.Item>
      </Form>
    </Space>
  </Modal>
);
