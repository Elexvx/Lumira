import { DatePicker, Form, Input, Select } from 'antd';
import type { FormProps } from 'antd';
import { GENDER_OPTIONS, USER_STATUS_OPTIONS } from '@/pages/system/users/constants';
import { trimString, validateOptionalChinaIdCard, validateOptionalChinaMobile } from '@/utils/validators';

interface UserEditorFormProps {
  formProps: FormProps;
  editingId: number | null;
  roleOptions: { label: string; value: number }[];
  protectedAdminSelected: boolean;
}

export const UserEditorForm = ({ formProps, editingId, roleOptions, protectedAdminSelected }: UserEditorFormProps) => (
  <Form {...formProps}>
    <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="roleIds" label="角色" extra="可为用户分配一个或多个角色">
      <Select mode="multiple" allowClear options={roleOptions} placeholder="请选择角色" />
    </Form.Item>
    <Form.Item
      name="password"
      label={editingId ? '重置密码（可选）' : '初始密码'}
      rules={!editingId ? [{ required: true, message: '请输入密码' }] : undefined}
    >
      <Input.Password placeholder="输入密码" />
    </Form.Item>
    <Form.Item name="mobile" label="手机号" rules={[{ validator: validateOptionalChinaMobile }]} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="idCardNumber" label="身份证号码" rules={[{ validator: validateOptionalChinaIdCard }]} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="nickname" label="昵称" normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="realName" label="姓名" normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="email" label="邮箱" rules={[{ type: 'email', message: '请输入有效邮箱地址' }]} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="avatarUrl" label="头像地址" normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="birthMonth" label="出生年月">
      <DatePicker picker="month" placeholder="请选择出生年月" format="YYYY年MM月" style={{ width: '100%' }} />
    </Form.Item>
    <Form.Item name="gender" label="性别">
      <Select allowClear options={GENDER_OPTIONS} placeholder="请选择性别" />
    </Form.Item>
    <Form.Item name="region" label="所在地区" normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="status" label="状态">
      <Select disabled={protectedAdminSelected} options={protectedAdminSelected ? USER_STATUS_OPTIONS.slice(0, 1) : USER_STATUS_OPTIONS} />
    </Form.Item>
    <Form.Item name="availableTime" label="可工作时间" normalize={trimString}>
      <Input.TextArea rows={2} placeholder="请输入可工作时间，如：周一至周五 09:00-18:00" />
    </Form.Item>
  </Form>
);
