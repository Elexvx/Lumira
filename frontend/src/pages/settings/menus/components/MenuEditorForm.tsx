import { Form, Input, InputNumber, Select } from 'antd';
import type { FormProps } from 'antd';
import type { MenuRecord } from '@/types/api';

export interface MenuIconOption {
  label: string;
  value: string;
}

interface MenuEditorFormProps {
  formProps: FormProps;
  parentOptions: Array<{ label: string; value: number }>;
  iconOptions: MenuIconOption[];
  iconLoading?: boolean;
}

export const DEFAULT_MENU_ICON_OPTIONS: MenuIconOption[] = [
  { label: '首页 / DashboardOutlined', value: 'DashboardOutlined' },
  { label: '应用 / AppstoreOutlined', value: 'AppstoreOutlined' },
  { label: '设置 / SettingOutlined', value: 'SettingOutlined' },
  { label: '用户 / UserOutlined', value: 'UserOutlined' },
  { label: '团队 / TeamOutlined', value: 'TeamOutlined' },
  { label: '权限 / SafetyOutlined', value: 'SafetyOutlined' },
  { label: '菜单 / MenuOutlined', value: 'MenuOutlined' },
  { label: '字典 / DatabaseOutlined', value: 'DatabaseOutlined' },
  { label: '表单 / FormOutlined', value: 'FormOutlined' },
  { label: '通知 / NotificationOutlined', value: 'NotificationOutlined' },
  { label: '机器人 / RobotOutlined', value: 'RobotOutlined' },
  { label: '接口 / ApiOutlined', value: 'ApiOutlined' },
  { label: '文件 / FolderOpenOutlined', value: 'FolderOpenOutlined' },
  { label: '审计 / AuditOutlined', value: 'AuditOutlined' },
];

const MENU_TYPE_OPTIONS = [
  { label: '目录', value: 'CATALOG' },
  { label: '菜单', value: 'MENU' },
  { label: '按钮', value: 'BUTTON' },
];

const MENU_STATUS_OPTIONS = [
  { label: '启用', value: 'ENABLED' },
  { label: '停用', value: 'DISABLED' },
];

export const buildParentMenuOptions = (menus: Array<MenuRecord & { level: number }>) =>
  menus.map((menu) => ({
    label: `${'　'.repeat(menu.level || 0)}${menu.menuName}`,
    value: menu.id,
  }));

export const MenuEditorForm = ({ formProps, parentOptions, iconOptions, iconLoading }: MenuEditorFormProps) => (
  <Form {...formProps}>
    <Form.Item name="parentId" label="上级菜单">
      <Select allowClear options={parentOptions} />
    </Form.Item>
    <Form.Item name="menuCode" label="菜单编码" rules={[{ required: true, message: '请输入菜单编码' }]}>
      <Input />
    </Form.Item>
    <Form.Item name="menuName" label="菜单名称" rules={[{ required: true, message: '请输入菜单名称' }]}>
      <Input />
    </Form.Item>
    <Form.Item name="menuType" label="菜单类型" rules={[{ required: true, message: '请选择菜单类型' }]}>
      <Select options={MENU_TYPE_OPTIONS} />
    </Form.Item>
    <Form.Item name="path" label="路由">
      <Input />
    </Form.Item>
    <Form.Item name="component" label="组件">
      <Input />
    </Form.Item>
    <Form.Item name="icon" label="图标" extra="图标选项来自字典 menu_icon，可在字典管理中维护。">
      <Select
        allowClear
        showSearch
        loading={iconLoading}
        optionFilterProp="label"
        options={iconOptions.length ? iconOptions : DEFAULT_MENU_ICON_OPTIONS}
        placeholder="请选择图标"
      />
    </Form.Item>
    <Form.Item name="sortNo" label="排序">
      <InputNumber style={{ width: '100%' }} />
    </Form.Item>
    <Form.Item name="permissionKey" label="权限标识">
      <Input />
    </Form.Item>
    <Form.Item name="status" label="状态">
      <Select options={MENU_STATUS_OPTIONS} />
    </Form.Item>
  </Form>
);
