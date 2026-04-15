import { Form, Input, Select } from 'antd';
import type { FormProps } from 'antd';
import { dictStatusOptions } from '@/pages/system/dicts/constants';

interface DictTypeFormProps {
  formProps: FormProps;
}

export const DictTypeForm = ({ formProps }: DictTypeFormProps) => (
  <Form {...formProps}>
    <Form.Item name="dictCode" label="字典编码" rules={[{ required: true, message: '请输入字典编码' }]}>
      <Input />
    </Form.Item>
    <Form.Item name="dictName" label="字典名称" rules={[{ required: true, message: '请输入字典名称' }]}>
      <Input />
    </Form.Item>
    <Form.Item name="status" label="状态">
      <Select options={dictStatusOptions} />
    </Form.Item>
    <Form.Item name="remark" label="备注">
      <Input.TextArea rows={3} />
    </Form.Item>
  </Form>
);
