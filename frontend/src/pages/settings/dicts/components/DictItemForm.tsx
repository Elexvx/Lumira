import { Form, Input, InputNumber, Select } from 'antd';
import type { FormProps } from 'antd';
import { dictStatusOptions } from '@/pages/settings/dicts/constants';

interface DictItemFormProps {
  formProps: FormProps;
}

export const DictItemForm = ({ formProps }: DictItemFormProps) => (
  <Form {...formProps}>
    <Form.Item name="itemLabel" label="标签" rules={[{ required: true, message: '请输入标签' }]}>
      <Input />
    </Form.Item>
    <Form.Item name="itemValue" label="值" rules={[{ required: true, message: '请输入值' }]}>
      <Input />
    </Form.Item>
    <Form.Item name="sortNo" label="排序">
      <InputNumber style={{ width: '100%' }} />
    </Form.Item>
    <Form.Item name="status" label="状态">
      <Select options={dictStatusOptions} />
    </Form.Item>
    <Form.Item name="remark" label="备注">
      <Input.TextArea rows={3} />
    </Form.Item>
  </Form>
);
