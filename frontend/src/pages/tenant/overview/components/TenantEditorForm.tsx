import { Form, Input, Select } from 'antd';
import type { FormProps } from 'antd';

interface TenantEditorFormProps {
  formProps: FormProps;
}

export const TenantEditorForm = ({ formProps }: TenantEditorFormProps) => (
  <Form {...formProps}>
    <Form.Item name="tenantCode" label="租户编码" rules={[{ required: true, message: '请输入租户编码' }]}>
      <Input maxLength={64} placeholder="例如：acme" />
    </Form.Item>
    <Form.Item name="tenantName" label="租户名称" rules={[{ required: true, message: '请输入租户名称' }]}>
      <Input maxLength={128} placeholder="租户显示名称" />
    </Form.Item>
    <Form.Item name="tenantShortName" label="租户简称">
      <Input maxLength={64} placeholder="可选，用于顶部切换器展示" />
    </Form.Item>
    <Form.Item name="status" label="状态" rules={[{ required: true, message: '请选择状态' }]}>
      <Select
        options={[
          { label: '启用', value: 'ENABLED' },
          { label: '停用', value: 'DISABLED' },
        ]}
      />
    </Form.Item>
  </Form>
);
