import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import type { ProDescriptionsItemProps } from '@ant-design/pro-components';
import { ProDescriptions } from '@ant-design/pro-components';
import { ApartmentOutlined, DownloadOutlined, ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Card, DatePicker, Empty, Form, Input, Modal, Select, Space, Spin, Transfer, Tree, Typography } from 'antd';
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
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

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
          <span className="saas-user-department-tree__name">{t('全部部门', 'All departments')}</span>
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
          {t('组织部门', 'Org departments')}
      </span>
      }
      extra={<Button type="text" aria-label={t('刷新部门', 'Refresh departments')} icon={<ReloadOutlined />} loading={loading} onClick={onRefresh} />}
    >
      <Input.Search
        allowClear
        className="saas-user-department-card__search"
        placeholder={t('搜索部门', 'Search departments')}
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
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('暂无匹配部门', 'No matching departments')} />
      )}
    </Card>
  );
};

const userDetailColumns: ProDescriptionsItemProps<UserDetail>[] = [
  { title: t('用户ID', 'User ID'), dataIndex: 'id' },
  { title: t('用户编号', 'User number'), dataIndex: 'userNo', renderText: (value) => value || '-' },
  { title: t('用户名', 'Username'), dataIndex: 'username' },
  { title: t('昵称', 'Nickname'), dataIndex: 'nickname', renderText: (value) => value || '-' },
  { title: t('姓名', 'Full name'), dataIndex: 'realName', renderText: (value) => value || '-' },
  { title: t('手机号', 'Mobile number'), dataIndex: 'mobile', renderText: (value) => maskMobile(value) || '-' },
  {
    title: t('身份证号码', 'ID card number'),
    dataIndex: 'idCardNumber',
    renderText: (value) => maskIdCardNumber(value) || '-',
  },
  { title: t('邮箱', 'Email'), dataIndex: 'email', renderText: (value) => maskEmail(value) || '-' },
  { title: t('头像地址', 'Avatar URL'), dataIndex: 'avatarUrl', renderText: (value) => value || '-' },
  { title: t('出生年月', 'Birth month'), dataIndex: 'birthMonth', renderText: (value) => value || '-' },
  { title: t('性别', 'Gender'), dataIndex: 'gender', renderText: (value) => value || '-' },
  { title: t('所在地区', 'Region'), dataIndex: 'region', renderText: (value) => value || '-' },
  { title: t('可工作时间', 'Available time'), dataIndex: 'availableTime', renderText: (value) => value || '-' },
  { title: t('状态', 'Status'), dataIndex: 'status' },
  { title: t('来源', 'Source'), dataIndex: 'source', renderText: (value) => value || '-' },
  { title: t('注册时间', 'Registered at'), dataIndex: 'registeredAt', renderText: (value) => value || '-' },
  { title: t('最近登录', 'Last login'), dataIndex: 'lastLoginAt', renderText: (value) => value || '-' },
  {
    title: t('角色', 'Roles'),
    dataIndex: 'roleNames',
    renderText: (value) => (Array.isArray(value) && value.length ? value.join(', ') : '-'),
  },
  {
    title: t('部门', 'Departments'),
    dataIndex: 'deptNames',
    renderText: (value) => (Array.isArray(value) && value.length ? value.join(', ') : '-'),
  },
  { title: t('创建时间', 'Created at'), dataIndex: 'createdAt', renderText: (value) => value || '-' },
  { title: t('更新时间', 'Updated at'), dataIndex: 'updatedAt', renderText: (value) => value || '-' },
];

const GENDER_OPTIONS = [
  { label: t('男', 'Male'), value: 'MALE' },
  { label: t('女', 'Female'), value: 'FEMALE' },
  { label: t('其他', 'Other'), value: 'OTHER' },
];

const USER_STATUS_OPTIONS = [
  { label: t('启用', 'Enabled'), value: 'ENABLED' },
  { label: t('禁用', 'Disabled'), value: 'DISABLED' },
];

const USERNAME_PATTERN = /^[A-Za-z0-9_-]+$/;

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
  ...(!editingId ? [{ required: true, message: t('请输入密码', 'Please enter the password') }] : []),
  {
    validator: async (_: unknown, value?: string) => {
      if (!value) {
        return Promise.resolve();
      }
      const minLength = Math.max(1, Number(securitySettings.passwordMinLength || 0));
      if (value.length < minLength) {
        return Promise.reject(new Error(t('密码长度不能少于 {count} 位', 'Password must be at least {count} characters').replace('{count}', String(minLength))));
      }
      if (securitySettings.passwordRequireUppercase && !/[A-Z]/.test(value)) {
        return Promise.reject(new Error(t('密码必须包含大写字母', 'Password must contain an uppercase letter')));
      }
      if (securitySettings.passwordRequireLowercase && !/[a-z]/.test(value)) {
        return Promise.reject(new Error(t('密码必须包含小写字母', 'Password must contain a lowercase letter')));
      }
      if (securitySettings.passwordRequireSpecialCharacter && !/[^A-Za-z0-9]/.test(value)) {
        return Promise.reject(new Error(t('密码必须包含特殊字符', 'Password must contain a special character')));
      }
      if (!securitySettings.passwordAllowConsecutiveCharacters && containsConsecutiveCharacters(value)) {
        return Promise.reject(new Error(t('密码不能包含连续字符', 'Password cannot contain consecutive characters')));
      }
      return Promise.resolve();
    },
  },
];

const buildPasswordPolicyHint = (securitySettings: SecuritySettings) => {
  const parts = [t('至少 {count} 位', 'At least {count} characters').replace('{count}', String(Math.max(1, Number(securitySettings.passwordMinLength || 0))))];
  if (securitySettings.passwordRequireUppercase) {
    parts.push(t('包含大写字母', 'Contains uppercase letters'));
  }
  if (securitySettings.passwordRequireLowercase) {
    parts.push(t('包含小写字母', 'Contains lowercase letters'));
  }
  if (securitySettings.passwordRequireSpecialCharacter) {
    parts.push(t('包含特殊字符', 'Contains special characters'));
  }
  if (!securitySettings.passwordAllowConsecutiveCharacters) {
    parts.push(t('不能包含连续字符', 'No consecutive characters'));
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
    <Form.Item
      name="username"
      label={t('用户名', 'Username')}
      rules={[
        { required: true, message: t('请输入用户名', 'Please enter the username') },
        {
          pattern: USERNAME_PATTERN,
          message: t('用户名只能包含英文字母、数字、下划线和连字符', 'Username can only contain letters, numbers, underscores, and hyphens'),
        },
      ]}
      normalize={trimString}
    >
      <Input autoComplete="username" placeholder={t('例如：zhangsan', 'e.g. zhangsan')} />
    </Form.Item>
    <Form.Item name="roleIds" label={t('角色', 'Roles')} rules={[{ required: true, message: t('请选择角色', 'Please select roles') }]} extra={t('可为用户分配一个或多个角色', 'You can assign one or more roles to the user')}>
      <Select mode="multiple" allowClear options={roleOptions} placeholder={t('请选择角色', 'Select roles')} />
    </Form.Item>
    <Form.Item name="deptIds" label={t('所属部门', 'Departments')} extra={t('部门用于本部门、本部门及下级等数据权限范围', 'Departments are used for data scope like current department and descendants')}>
      <Select mode="multiple" allowClear options={departmentOptions} placeholder={t('请选择部门', 'Select departments')} />
    </Form.Item>
    <Form.Item name="primaryDeptId" label={t('主部门', 'Primary department')}>
      <Select allowClear options={departmentOptions} placeholder={t('请选择主部门', 'Select a primary department')} />
    </Form.Item>
    <Form.Item
      name="password"
      label={editingId ? t('重置密码（可选）', 'Reset password (optional)') : t('初始密码', 'Initial password')}
      extra={buildPasswordPolicyHint(securitySettings)}
      rules={buildPasswordPolicyRules(editingId, securitySettings, containsConsecutiveCharacters)}
    >
      <Input.Password placeholder={t('输入密码', 'Enter password')} />
    </Form.Item>
    <Form.Item name="status" label={t('状态', 'Status')} rules={[{ required: true, message: t('请选择状态', 'Please select a status') }]}>
      <Select disabled={protectedAdminSelected} options={protectedAdminSelected ? USER_STATUS_OPTIONS.slice(0, 1) : USER_STATUS_OPTIONS} />
    </Form.Item>
    <Form.Item name="mobile" label={t('手机号', 'Mobile number')} rules={[{ validator: validateOptionalChinaMobile }]} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="idCardNumber" label={t('身份证号码', 'ID card number')} rules={[{ validator: validateOptionalChinaIdCard }]} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="nickname" label={t('昵称', 'Nickname')} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="realName" label={t('姓名', 'Full name')} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="email" label={t('邮箱', 'Email')} rules={[{ type: 'email', message: t('请输入有效邮箱地址', 'Please enter a valid email address') }]} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="avatarUrl" label={t('头像地址', 'Avatar URL')} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="birthMonth" label={t('出生年月', 'Birth month')}>
      <DatePicker picker="month" placeholder={t('请选择出生年月', 'Select birth month')} format={isEnglishLocale() ? 'YYYY-MM' : 'YYYY年MM月'} style={{ width: '100%' }} />
    </Form.Item>
    <Form.Item name="gender" label={t('性别', 'Gender')}>
      <Select allowClear options={GENDER_OPTIONS} placeholder={t('请选择性别', 'Select gender')} />
    </Form.Item>
    <Form.Item name="region" label={t('所在地区', 'Region')} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="availableTime" label={t('可工作时间', 'Available time')} normalize={trimString}>
      <Input.TextArea rows={2} placeholder={t('请输入可工作时间，如：周一至周五 09:00-18:00', 'Enter available time, e.g. Mon-Fri 09:00-18:00')} />
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
    exportModalOpen,
    setExportModalOpen,
    exportFields,
    selectedExportFields,
    setSelectedExportFields,
    exportLoading,
    exportTaskOpen,
    setExportTaskOpen,
    exportTask,
    openCreate,
    openExport,
    confirmExport,
    downloadExportTaskFile,
    openDownloadCenter,
    saveUser,
    loadDepartments,
  } = userManagement;

  return (
    <ManagementPage title={t('用户管理', 'User management')} className="saas-user-management-page">
      <ManagementPageBody>
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
                    label: t('新增用户', 'Add user'),
                    onClick: () => void openCreate(),
                  },
                  {
                    key: 'export',
                    permission: 'system:user:export',
                    icon: <DownloadOutlined />,
                    label: t('导出用户', 'Export users'),
                    onClick: () => void openExport(),
                  },
                  {
                    key: 'refresh',
                    label: t('刷新', 'Refresh'),
                    onClick: () => void actionRef.current?.reload(),
                  },
                ])
              }
            />
          </div>
        </div>
      </ManagementPageBody>

      <ManagementDrawer
        title={drawer.editingId ? t('编辑用户', 'Edit user') : t('新增用户', 'Add user')}
        open={drawer.open}
        onClose={drawer.close}
        footerActions={[
          { key: 'cancel', label: t('取消', 'Cancel'), onClick: drawer.close },
          { key: 'save', label: t('保存', 'Save'), type: 'primary', loading: saving, disabled: !canSaveUser, onClick: () => void saveUser() },
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

      <ManagementDrawer title={detail.currentRecord?.username ? `${t('用户详情', 'User details')} · ${detail.currentRecord.username}` : t('用户详情', 'User details')} open={detail.open} onClose={detail.close}>
        {detail.loading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 'var(--saas-spacing-240)' }}>
            <Spin />
          </div>
        ) : selectedUserDetail ? (
          <ProDescriptions<UserDetail> {...detailProps} columns={userDetailColumns} />
        ) : null}
      </ManagementDrawer>

      <Modal
        title={t('导出用户', 'Export users')}
        open={exportModalOpen}
        onCancel={() => setExportModalOpen(false)}
        onOk={() => void confirmExport()}
        okText={t('开始导出', 'Start export')}
        cancelText={t('取消', 'Cancel')}
        confirmLoading={exportLoading}
        width={720}
        destroyOnHidden
      >
        <Transfer
          dataSource={exportFields.map((field) => ({ key: field.key, title: field.label }))}
          titles={[t('可选字段', 'Available fields'), t('导出字段', 'Export fields')]}
          targetKeys={selectedExportFields}
          onChange={(nextTargetKeys) => setSelectedExportFields(nextTargetKeys.map(String))}
          render={(item) => item.title}
          listStyle={{ width: 300, height: 360 }}
          showSearch
        />
      </Modal>

      <Modal
        title={t('导出任务', 'Export task')}
        open={exportTaskOpen}
        onCancel={() => setExportTaskOpen(false)}
        footer={[
          <Button key="close" onClick={() => setExportTaskOpen(false)}>
            {t('关闭', 'Close')}
          </Button>,
          <Button key="center" onClick={openDownloadCenter}>
            {t('前往下载中心', 'Download center')}
          </Button>,
          <Button key="download" type="primary" disabled={exportTask?.status !== 'SUCCESS' || !exportTask.downloadUrl} onClick={downloadExportTaskFile}>
            {t('下载文件', 'Download file')}
          </Button>,
        ]}
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Alert
            type={exportTask?.status === 'FAILED' ? 'error' : exportTask?.status === 'SUCCESS' ? 'success' : 'info'}
            showIcon
            message={
              exportTask?.status === 'SUCCESS'
                ? t('导出完成', 'Export complete')
                : exportTask?.status === 'FAILED'
                  ? t('导出失败', 'Export failed')
                  : t('导出进行中', 'Export in progress')
            }
            description={exportTask?.errorMessage || t('数据较多时会在后台生成文件，完成后可在下载中心获取。', 'Large exports are generated in the background and can be downloaded from the download center.')}
          />
          <Typography.Text type="secondary">
            {t('记录数', 'Records')}: {exportTask?.totalCount ?? '-'}
          </Typography.Text>
          <Typography.Text type="secondary">
            {t('文件名', 'File name')}: {exportTask?.fileName || '-'}
          </Typography.Text>
        </Space>
      </Modal>
    </ManagementPage>
  );
};

export default UserManagementPage;
