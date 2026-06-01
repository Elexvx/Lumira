import { DatePicker, Form, Input, Select } from 'antd';
import type { FormProps } from 'antd';
import type { Rule } from 'antd/es/form';
import { GENDER_OPTIONS, USER_STATUS_OPTIONS } from '@/pages/system/users/constants';
import type { SecuritySettings } from '@/types/api';
import { trimString, validateOptionalChinaIdCard, validateOptionalChinaMobile } from '@/utils/validators';

interface UserEditorFormProps {
  formProps: FormProps;
  editingId: number | null;
  roleOptions: { label: string; value: number }[];
  departmentOptions: { label: string; value: number }[];
  protectedAdminSelected: boolean;
  securitySettings: SecuritySettings;
}

const containsConsecutiveCharacters = (value: string) => {
  const lower = value.toLowerCase();
  for (let index = 0; index < lower.length - 2; index += 1) {
    const first = lower.charCodeAt(index);
    const second = lower.charCodeAt(index + 1);
    const third = lower.charCodeAt(index + 2);
    const sameClass =
      (isDigit(first) && isDigit(second) && isDigit(third)) ||
      (isLetter(first) && isLetter(second) && isLetter(third));
    if (sameClass && ((second - first === 1 && third - second === 1) || (first - second === 1 && second - third === 1))) {
      return true;
    }
  }
  return false;
};

const isDigit = (charCode: number) => charCode >= 48 && charCode <= 57;

const isLetter = (charCode: number) => charCode >= 97 && charCode <= 122;

const buildPasswordPolicyRules = (editingId: number | null, securitySettings: SecuritySettings): Rule[] => [
  ...(!editingId ? [{ required: true, message: '请输入密码' }] : []),
  {
    validator: async (_, value?: string) => {
      if (!value) {
        return Promise.resolve();
      }
      const minLength = Math.max(1, Number(securitySettings.passwordMinLength || 0));
      if (value.length < minLength) {
        return Promise.reject(new Error(`密码长度不能少于 ${minLength} 位`));
      }
      if (securitySettings.passwordRequireUppercase && !/[A-Z]/.test(value)) {
        return Promise.reject(new Error('密码必须包含大写字母'));
      }
      if (securitySettings.passwordRequireLowercase && !/[a-z]/.test(value)) {
        return Promise.reject(new Error('密码必须包含小写字母'));
      }
      if (securitySettings.passwordRequireSpecialCharacter && !/[^A-Za-z0-9]/.test(value)) {
        return Promise.reject(new Error('密码必须包含特殊字符'));
      }
      if (!securitySettings.passwordAllowConsecutiveCharacters && containsConsecutiveCharacters(value)) {
        return Promise.reject(new Error('密码不能包含连续字符'));
      }
      return Promise.resolve();
    },
  },
];

const buildPasswordPolicyHint = (securitySettings: SecuritySettings) => {
  const parts = [`至少 ${Math.max(1, Number(securitySettings.passwordMinLength || 0))} 位`];
  if (securitySettings.passwordRequireUppercase) {
    parts.push('包含大写字母');
  }
  if (securitySettings.passwordRequireLowercase) {
    parts.push('包含小写字母');
  }
  if (securitySettings.passwordRequireSpecialCharacter) {
    parts.push('包含特殊字符');
  }
  if (!securitySettings.passwordAllowConsecutiveCharacters) {
    parts.push('不能包含连续字符');
  }
  return parts.join('，');
};

export const UserEditorForm = ({ formProps, editingId, roleOptions, departmentOptions, protectedAdminSelected, securitySettings }: UserEditorFormProps) => (
  <Form {...formProps}>
    <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="roleIds" label="角色" rules={[{ required: true, message: '请选择角色' }]} extra="可为用户分配一个或多个角色">
      <Select mode="multiple" allowClear options={roleOptions} placeholder="请选择角色" />
    </Form.Item>
    <Form.Item name="deptIds" label="所属部门" extra="部门用于本部门、本部门及下级等数据权限范围">
      <Select mode="multiple" allowClear options={departmentOptions} placeholder="请选择部门" />
    </Form.Item>
    <Form.Item name="primaryDeptId" label="主部门">
      <Select allowClear options={departmentOptions} placeholder="请选择主部门" />
    </Form.Item>
    <Form.Item
      name="password"
      label={editingId ? '重置密码（可选）' : '初始密码'}
      extra={buildPasswordPolicyHint(securitySettings)}
      rules={buildPasswordPolicyRules(editingId, securitySettings)}
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
    <Form.Item name="status" label="状态" rules={[{ required: true, message: '请选择状态' }]}>
      <Select disabled={protectedAdminSelected} options={protectedAdminSelected ? USER_STATUS_OPTIONS.slice(0, 1) : USER_STATUS_OPTIONS} />
    </Form.Item>
    <Form.Item name="availableTime" label="可工作时间" normalize={trimString}>
      <Input.TextArea rows={2} placeholder="请输入可工作时间，如：周一至周五 09:00-18:00" />
    </Form.Item>
  </Form>
);
