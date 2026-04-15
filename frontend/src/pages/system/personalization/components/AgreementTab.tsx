import { Button, Form, Space, Typography } from 'antd';
import type { FormProps } from 'antd';
import MDEditor from '@uiw/react-md-editor';

interface AgreementTabProps {
  formProps: FormProps;
  agreementSaving: boolean;
  onClearUserAgreement: () => void;
  onClearPrivacyAgreement: () => void;
  onSave: () => void;
}

export const AgreementTab = ({
  formProps,
  agreementSaving,
  onClearUserAgreement,
  onClearPrivacyAgreement,
  onSave,
}: AgreementTabProps) => (
  <Space direction="vertical" size={16} style={{ width: '100%' }}>
    <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
      使用 Markdown 编辑器编写协议内容，保存后会同步到登录页。
    </Typography.Paragraph>
    <Form {...formProps}>
      <Form.Item name="userAgreementMarkdown" label="用户协议" getValueFromEvent={(value) => value ?? ''}>
        <MDEditor
          preview="edit"
          height={320}
          style={{ width: '100%' }}
          textareaProps={{ placeholder: '请输入用户协议 Markdown 内容' }}
          data-color-mode="light"
        />
      </Form.Item>
      <Form.Item name="privacyAgreementMarkdown" label="隐私协议" getValueFromEvent={(value) => value ?? ''}>
        <MDEditor
          preview="edit"
          height={320}
          style={{ width: '100%' }}
          textareaProps={{ placeholder: '请输入隐私协议 Markdown 内容' }}
          data-color-mode="light"
        />
      </Form.Item>
    </Form>

    <Space wrap style={{ justifyContent: 'flex-end', width: '100%' }}>
      <Button danger onClick={onClearUserAgreement}>
        清空用户协议
      </Button>
      <Button danger onClick={onClearPrivacyAgreement}>
        清空隐私协议
      </Button>
      <Button type="primary" loading={agreementSaving} onClick={onSave}>
        保存设置
      </Button>
    </Space>
  </Space>
);
