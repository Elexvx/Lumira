import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementTable } from '@/features/management/ManagementTable';
import type { ProDescriptionsItemProps } from '@ant-design/pro-components';
import { ProDescriptions } from '@ant-design/pro-components';
import { ApartmentOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Card, DatePicker, Empty, Form, Input, Select, Spin, Tree } from 'antd';
import type { DataNode } from 'antd/es/tree';
import { useEffect, useMemo, useState } from 'react';
import { useUserManagement } from './users/hooks/useUserManagement';
import './users.css';
import type { DepartmentRecord, UserDetail } from '@/types/api';
import { maskEmail, maskIdCardNumber, maskMobile } from '@/utils/sensitive';
import type { FormProps } from 'antd';
import type { Rule } from 'antd/es/form';
import type { SecuritySettings } from '@/types/api';
import { trimString, validateOptionalChinaIdCard, validateOptionalChinaMobile } from '@/utils/validators';

const ALL_DEPARTMENTS_KEY = 'all';

const departmentTreeKey = (id: number) => `dept-${id}`;

const parseDepartmentTreeKey = (key: unknown) => {
  const value = String(key);
  if (value === ALL_DEPARTMENTS_KEY) {
    return null;
  }
  if (!value.startsWith('dept-')) {
    return null;
  }
  const id = Number(value.slice(5));
  return Number.isFinite(id) ? id : null;
};

const flattenDepartmentIds = (department: DepartmentRecord): number[] => [
  department.id,
  ...(department.children || []).flatMap((child) => flattenDepartmentIds(child)),
];

const departmentTitleMatches = (department: DepartmentRecord, keyword: string) =>
  !keyword ||
  department.deptName.toLowerCase().includes(keyword) ||
  department.deptCode.toLowerCase().includes(keyword);

const buildDepartmentTreeNodes = (items: DepartmentRecord[], keyword: string): DataNode[] =>
  items
    .map((department) => {
      const children = buildDepartmentTreeNodes(department.children || [], keyword);
      const matched = departmentTitleMatches(department, keyword);
      if (keyword && !matched && children.length === 0) {
        return null;
      }
      return {
        key: departmentTreeKey(department.id),
        title: (
          <span className="saas-user-department-tree__node">
            <span className="saas-user-department-tree__name">{department.deptName}</span>
            <span className="saas-user-department-tree__count">{department.userCount ?? 0}</span>
          </span>
        ),
        children,
      };
    })
    .filter(Boolean) as DataNode[];

const DepartmentTreeFilter = ({
  departments,
  loading,
  selectedDepartmentId,
  onSelectedDepartmentChange,
  onRefresh,
}: {
  departments: DepartmentRecord[];
  loading: boolean;
  selectedDepartmentId: number | null;
  onSelectedDepartmentChange: (departmentId: number | null) => void;
  onRefresh: () => void;
}) => {
  const [departmentKeyword, setDepartmentKeyword] = useState('');
  const [expandedDepartmentKeys, setExpandedDepartmentKeys] = useState<string[]>([ALL_DEPARTMENTS_KEY]);
  const normalizedDepartmentKeyword = departmentKeyword.trim().toLowerCase();
  const allDepartmentIds = useMemo(() => departments.flatMap((department) => flattenDepartmentIds(department)), [departments]);
  const departmentTreeData = useMemo(
    () => [
      {
        key: ALL_DEPARTMENTS_KEY,
        title: (
          <span className="saas-user-department-tree__node">
            <span className="saas-user-department-tree__name">全部部门</span>
          </span>
        ),
        children: buildDepartmentTreeNodes(departments, normalizedDepartmentKeyword),
      },
    ],
    [departments, normalizedDepartmentKeyword],
  );
  const selectedDepartmentKey = selectedDepartmentId ? departmentTreeKey(selectedDepartmentId) : ALL_DEPARTMENTS_KEY;

  useEffect(() => {
    setExpandedDepartmentKeys(
      normalizedDepartmentKeyword ? [ALL_DEPARTMENTS_KEY, ...allDepartmentIds.map(departmentTreeKey)] : [ALL_DEPARTMENTS_KEY],
    );
  }, [allDepartmentIds, normalizedDepartmentKeyword]);

  return (
    <Card
      className="saas-user-department-card"
      title={
        <span className="saas-user-department-card__title">
          <ApartmentOutlined />
          组织部门
        </span>
      }
      extra={<Button type="text" aria-label="刷新部门" icon={<ReloadOutlined />} loading={loading} onClick={onRefresh} />}
    >
      <Input.Search
        allowClear
        className="saas-user-department-card__search"
        placeholder="搜索部门"
        value={departmentKeyword}
        onChange={(event) => setDepartmentKeyword(event.target.value)}
      />
      {loading && departments.length === 0 ? (
        <div className="saas-user-department-card__loading">
          <Spin />
        </div>
      ) : departmentTreeData[0]?.children?.length || !normalizedDepartmentKeyword ? (
        <Tree
          blockNode
          className="saas-user-department-tree"
          treeData={departmentTreeData}
          selectedKeys={[selectedDepartmentKey]}
          expandedKeys={expandedDepartmentKeys}
          onExpand={(keys) => setExpandedDepartmentKeys(keys.map(String))}
          onSelect={(_, info) => {
            onSelectedDepartmentChange(parseDepartmentTreeKey(info.node.key));
          }}
        />
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无匹配部门" />
      )}
    </Card>
  );
};

const userDetailColumns: ProDescriptionsItemProps<UserDetail>[] = [
  { title: '用户ID', dataIndex: 'id' },
  { title: '用户编号', dataIndex: 'userNo', renderText: (value) => value || '-' },
  { title: '用户名', dataIndex: 'username' },
  { title: '昵称', dataIndex: 'nickname', renderText: (value) => value || '-' },
  { title: '姓名', dataIndex: 'realName', renderText: (value) => value || '-' },
  { title: '手机号', dataIndex: 'mobile', renderText: (value) => maskMobile(value) || '-' },
  {
    title: '身份证号码',
    dataIndex: 'idCardNumber',
    renderText: (value) => maskIdCardNumber(value) || '-',
  },
  { title: '邮箱', dataIndex: 'email', renderText: (value) => maskEmail(value) || '-' },
  { title: '头像地址', dataIndex: 'avatarUrl', renderText: (value) => value || '-' },
  { title: '出生年月', dataIndex: 'birthMonth', renderText: (value) => value || '-' },
  { title: '性别', dataIndex: 'gender', renderText: (value) => value || '-' },
  { title: '所在地区', dataIndex: 'region', renderText: (value) => value || '-' },
  { title: '可工作时间', dataIndex: 'availableTime', renderText: (value) => value || '-' },
  { title: '状态', dataIndex: 'status' },
  { title: '来源', dataIndex: 'source', renderText: (value) => value || '-' },
  { title: '注册时间', dataIndex: 'registeredAt', renderText: (value) => value || '-' },
  { title: '最近登录', dataIndex: 'lastLoginAt', renderText: (value) => value || '-' },
  {
    title: '角色',
    dataIndex: 'roleNames',
    renderText: (value) => (Array.isArray(value) && value.length ? value.join(', ') : '-'),
  },
  {
    title: '部门',
    dataIndex: 'deptNames',
    renderText: (value) => (Array.isArray(value) && value.length ? value.join(', ') : '-'),
  },
  { title: '创建时间', dataIndex: 'createdAt', renderText: (value) => value || '-' },
  { title: '更新时间', dataIndex: 'updatedAt', renderText: (value) => value || '-' },
];

const GENDER_OPTIONS = [
  { label: '男', value: 'MALE' },
  { label: '女', value: 'FEMALE' },
  { label: '其他', value: 'OTHER' },
];

const USER_STATUS_OPTIONS = [
  { label: '启用', value: 'ENABLED' },
  { label: '禁用', value: 'DISABLED' },
];

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

const buildPasswordPolicyRules = (editingId: number | null, securitySettings: SecuritySettings, containsConsecutiveCharacters: (value: string) => boolean): Rule[] => [
  ...(!editingId ? [{ required: true, message: '请输入密码' }] : []),
  {
    validator: async (_: unknown, value?: string) => {
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

const UserEditorForm = ({ formProps, editingId, roleOptions, departmentOptions, protectedAdminSelected, securitySettings }: {
  formProps: FormProps;
  editingId: number | null;
  roleOptions: { label: string; value: number }[];
  departmentOptions: { label: string; value: number }[];
  protectedAdminSelected: boolean;
  securitySettings: SecuritySettings;
}) => (
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
      rules={buildPasswordPolicyRules(editingId, securitySettings, containsConsecutiveCharacters)}
    >
      <Input.Password placeholder="输入密码" />
    </Form.Item>
    <Form.Item name="status" label="状态" rules={[{ required: true, message: '请选择状态' }]}>
      <Select disabled={protectedAdminSelected} options={protectedAdminSelected ? USER_STATUS_OPTIONS.slice(0, 1) : USER_STATUS_OPTIONS} />
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
    <Form.Item name="availableTime" label="可工作时间" normalize={trimString}>
      <Input.TextArea rows={2} placeholder="请输入可工作时间，如：周一至周五 09:00-18:00" />
    </Form.Item>
  </Form>
);

const UserManagementPage = () => {
  const userManagement = useUserManagement();

  const {
    actionRef,
    responsive,
    searchConfig,
    buildToolbarButtons,
    columns,
    tableRequest,
    drawer,
    detail,
    editorFormProps,
    saving,
    canSaveUser,
    protectedAdminSelected,
    securitySettings,
    roleOptions,
    departmentOptions,
    departments,
    departmentLoading,
    selectedDepartmentId,
    setSelectedDepartmentId,
    selectedUserDetail,
    detailProps,
    openCreate,
    saveUser,
    loadDepartments,
  } = userManagement;

  return (
    <ManagementPage title="用户管理" className="saas-user-management-page">
      <div className="saas-user-management-layout">
        <DepartmentTreeFilter
          departments={departments}
          loading={departmentLoading}
          selectedDepartmentId={selectedDepartmentId}
          onSelectedDepartmentChange={setSelectedDepartmentId}
          onRefresh={() => void loadDepartments()}
        />

        <div className="saas-user-management-main">
          <ManagementTable
            actionRef={actionRef}
            rowKey="id"
            columns={columns}
            isMobile={responsive.isMobile}
            search={searchConfig}
            request={tableRequest}
            toolBarRender={() =>
              buildToolbarButtons([
                {
                  key: 'create',
                  permission: 'system:user:create',
                  type: 'primary',
                  label: '新增用户',
                  onClick: () => void openCreate(),
                },
                {
                  key: 'refresh',
                  label: '刷新',
                  onClick: () => void actionRef.current?.reload(),
                },
              ])
            }
          />
        </div>
      </div>

      <ManagementDrawer
        title={drawer.editingId ? '编辑用户' : '新增用户'}
        open={drawer.open}
        onClose={drawer.close}
        footerActions={[
          { key: 'cancel', label: '取消', onClick: drawer.close },
          { key: 'save', label: '保存', type: 'primary', loading: saving, disabled: !canSaveUser, onClick: () => void saveUser() },
        ]}
      >
        <UserEditorForm
          formProps={editorFormProps}
          editingId={drawer.editingId}
          roleOptions={roleOptions}
          departmentOptions={departmentOptions}
          protectedAdminSelected={protectedAdminSelected}
          securitySettings={securitySettings}
        />
      </ManagementDrawer>

      <ManagementDrawer title={detail.currentRecord?.username ? `用户详情 · ${detail.currentRecord.username}` : '用户详情'} open={detail.open} onClose={detail.close}>
        {detail.loading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 'var(--saas-spacing-240)' }}>
            <Spin />
          </div>
        ) : selectedUserDetail ? (
          <ProDescriptions<UserDetail> {...detailProps} columns={userDetailColumns} />
        ) : null}
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default UserManagementPage;
