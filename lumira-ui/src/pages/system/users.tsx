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
import { useDictOptions, type DictOption } from '@/hooks/useDictOptions';
import type { DepartmentRecord, UserDetail } from '@/types/api';
import { maskEmail, maskIdCardNumber, maskMobile } from '@/utils/sensitive';
import type { FormProps } from 'antd';
import type { Rule } from 'antd/es/form';
import type { SecuritySettings } from '@/types/api';
import { trimString, validateOptionalChinaIdCard, validateOptionalChinaMobile } from '@/utils/validators';

import { protectedUserStatusOptions } from './users/options';
import { databaseMessage } from '@/i18n/databaseMessage';
import { resolveRuntimeLocale } from '@/i18n/locale';
import { UserAvatar } from '@/components/UserAvatar';

const t = databaseMessage;

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
          <span className="saas-user-department-tree__name">{t('ui.system.users.allDepartments')}</span>
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
          {t('ui.system.users.orgDepartments')}
      </span>
      }
      extra={<Button type="text" aria-label={t('ui.system.users.refreshDepartments')} icon={<ReloadOutlined />} loading={loading} onClick={onRefresh} />}
    >
      <Input.Search
        allowClear
        className="saas-user-department-card__search"
        placeholder={t('ui.system.users.searchDepartments')}
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
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('ui.system.users.noMatchingDepartments')} />
      )}
    </Card>
  );
};

const userDetailColumns: ProDescriptionsItemProps<UserDetail>[] = [
  {
    title: t('ui.system.users.avatarUrl'),
    dataIndex: 'avatarUrl',
    render: (_, record) => (
      <UserAvatar
        size={48}
        avatarUrl={record.avatarUrl}
        userId={record.id}
        userUuid={record.userUuid || record.uid}
        username={record.username}
      />
    ),
  },
  { title: t('ui.system.users.userNumber'), dataIndex: 'userNo', renderText: (value) => value || '-' },
  { title: t('ui.system.users.username'), dataIndex: 'username' },
  { title: t('ui.system.users.nickname'), dataIndex: 'nickname', renderText: (value) => value || '-' },
  { title: t('ui.system.users.fullName'), dataIndex: 'realName', renderText: (value) => value || '-' },
  { title: t('ui.system.users.mobileNumber'), dataIndex: 'mobile', renderText: (value) => maskMobile(value) || '-' },
  {
    title: t('ui.system.users.idCardNumber'),
    dataIndex: 'idCardNumber',
    renderText: (value) => maskIdCardNumber(value) || '-',
  },
  { title: t('ui.system.users.email'), dataIndex: 'email', renderText: (value) => maskEmail(value) || '-' },
  { title: t('ui.system.users.birthMonth'), dataIndex: 'birthMonth', renderText: (value) => value || '-' },
  { title: t('ui.system.users.gender'), dataIndex: 'gender', renderText: (value) => value || '-' },
  { title: t('ui.system.users.region'), dataIndex: 'region', renderText: (value) => value || '-' },
  { title: t('ui.system.users.availableTime'), dataIndex: 'availableTime', renderText: (value) => value || '-' },
  { title: t('ui.system.users.status'), dataIndex: 'status' },
  { title: t('ui.system.users.source'), dataIndex: 'source', renderText: (value) => value || '-' },
  { title: t('ui.system.users.registeredAt'), dataIndex: 'registeredAt', renderText: (value) => value || '-' },
  { title: t('ui.system.users.lastLogin'), dataIndex: 'lastLoginAt', renderText: (value) => value || '-' },
  {
    title: t('ui.system.users.roles'),
    dataIndex: 'roleNames',
    renderText: (value) => (Array.isArray(value) && value.length ? value.join(', ') : '-'),
  },
  {
    title: t('ui.system.users.departments'),
    dataIndex: 'deptNames',
    renderText: (value) => (Array.isArray(value) && value.length ? value.join(', ') : '-'),
  },
  { title: t('ui.system.users.createdAt'), dataIndex: 'createdAt', renderText: (value) => value || '-' },
  { title: t('ui.system.users.updatedAt'), dataIndex: 'updatedAt', renderText: (value) => value || '-' },
];

const GENDER_OPTIONS = [
  { label: t('ui.system.users.male'), value: 'MALE' },
  { label: t('ui.system.users.female'), value: 'FEMALE' },
  { label: t('ui.system.users.other'), value: 'OTHER' },
];

const USER_STATUS_OPTIONS = [
  { label: t('ui.system.users.enabled'), value: 'ENABLED' },
  { label: t('ui.system.users.disabled'), value: 'DISABLED' },
];

const USER_GENDER_DICT_FALLBACK_OPTIONS = [...GENDER_OPTIONS, { label: 'Unknown', value: 'UNKNOWN' }];
const USER_STATUS_DICT_FALLBACK_OPTIONS = [...USER_STATUS_OPTIONS, { label: 'Locked', value: 'LOCKED' }];

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
  ...(!editingId ? [{ required: true, message: t('ui.system.users.pleaseEnterThePassword') }] : []),
  {
    validator: async (_: unknown, value?: string) => {
      if (!value) {
        return Promise.resolve();
      }
      const minLength = Math.max(1, Number(securitySettings.passwordMinLength || 0));
      if (value.length < minLength) {
        return Promise.reject(new Error(t('ui.system.users.passwordMustBeAtLeastCharacters').replace('{count}', String(minLength))));
      }
      if (securitySettings.passwordRequireUppercase && !/[A-Z]/.test(value)) {
        return Promise.reject(new Error(t('ui.system.users.passwordMustContainAnUppercaseLetter')));
      }
      if (securitySettings.passwordRequireLowercase && !/[a-z]/.test(value)) {
        return Promise.reject(new Error(t('ui.system.users.passwordMustContainALowercaseLetter')));
      }
      if (securitySettings.passwordRequireSpecialCharacter && !/[^A-Za-z0-9]/.test(value)) {
        return Promise.reject(new Error(t('ui.system.users.passwordMustContainASpecialCharacter')));
      }
      if (!securitySettings.passwordAllowConsecutiveCharacters && containsConsecutiveCharacters(value)) {
        return Promise.reject(new Error(t('ui.system.users.passwordCannotContainConsecutiveCharacters')));
      }
      return Promise.resolve();
    },
  },
];

const buildPasswordPolicyHint = (securitySettings: SecuritySettings) => {
  const parts = [t('ui.system.users.atLeastCharacters').replace('{count}', String(Math.max(1, Number(securitySettings.passwordMinLength || 0))))];
  if (securitySettings.passwordRequireUppercase) {
    parts.push(t('ui.system.users.containsUppercaseLetters'));
  }
  if (securitySettings.passwordRequireLowercase) {
    parts.push(t('ui.system.users.containsLowercaseLetters'));
  }
  if (securitySettings.passwordRequireSpecialCharacter) {
    parts.push(t('ui.system.users.containsSpecialCharacters'));
  }
  if (!securitySettings.passwordAllowConsecutiveCharacters) {
    parts.push(t('ui.system.users.noConsecutiveCharacters'));
  }
  return parts.join('，');
};

const UserEditorForm = ({ formProps, editingId, roleOptions, departmentOptions, protectedAdminSelected, securitySettings, genderOptions, userStatusOptions }: {
  formProps: FormProps;
  editingId: number | null;
  roleOptions: { label: string; value: number }[];
  departmentOptions: { label: string; value: number }[];
  protectedAdminSelected: boolean;
  securitySettings: SecuritySettings;
  genderOptions: DictOption[];
  userStatusOptions: DictOption[];
}) => (
  <Form {...formProps}>
    <Form.Item
      name="username"
      label={t('ui.system.users.username')}
      rules={[
        { required: true, message: t('ui.system.users.pleaseEnterTheUsername') },
        {
          pattern: USERNAME_PATTERN,
          message: t('ui.system.users.usernameCanOnlyContainLettersNumbersUnderscoresAnd'),
        },
      ]}
      normalize={trimString}
    >
      <Input autoComplete="username" placeholder={t('ui.system.users.eGZhangsan')} />
    </Form.Item>
    <Form.Item name="roleIds" label={t('ui.system.users.roles')} rules={[{ required: true, message: t('ui.system.users.pleaseSelectRoles') }]} extra={t('ui.system.users.youCanAssignOneOrMoreRolesTo')}>
      <Select mode="multiple" allowClear options={roleOptions} placeholder={t('ui.system.users.selectRoles')} />
    </Form.Item>
    <Form.Item name="deptIds" label={t('ui.system.users.departments.b9e09338')} extra={t('ui.system.users.departmentsAreUsedForDataScopeLikeCurrent')}>
      <Select mode="multiple" allowClear options={departmentOptions} placeholder={t('ui.system.users.selectDepartments')} />
    </Form.Item>
    <Form.Item name="primaryDeptId" label={t('ui.system.users.primaryDepartment')}>
      <Select allowClear options={departmentOptions} placeholder={t('ui.system.users.selectAPrimaryDepartment')} />
    </Form.Item>
    <Form.Item
      name="password"
      label={editingId ? t('ui.system.users.resetPasswordOptional') : t('ui.system.users.initialPassword')}
      extra={buildPasswordPolicyHint(securitySettings)}
      rules={buildPasswordPolicyRules(editingId, securitySettings, containsConsecutiveCharacters)}
    >
      <Input.Password placeholder={t('ui.system.users.enterPassword')} />
    </Form.Item>
    <Form.Item name="status" label={t('ui.system.users.status')} rules={[{ required: true, message: t('ui.system.users.pleaseSelectAStatus') }]}>
      <Select disabled={protectedAdminSelected} options={protectedUserStatusOptions(userStatusOptions, protectedAdminSelected)} />
    </Form.Item>
    <Form.Item name="mobile" label={t('ui.system.users.mobileNumber')} rules={[{ validator: validateOptionalChinaMobile }]} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="idCardNumber" label={t('ui.system.users.idCardNumber')} rules={[{ validator: validateOptionalChinaIdCard }]} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="nickname" label={t('ui.system.users.nickname')} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="realName" label={t('ui.system.users.fullName')} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="email" label={t('ui.system.users.email')} rules={[{ type: 'email', message: t('ui.system.users.pleaseEnterAValidEmailAddress') }]} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="avatarUrl" label={t('ui.system.users.avatarUrl')} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="birthMonth" label={t('ui.system.users.birthMonth')}>
      <DatePicker picker="month" placeholder={t('ui.system.users.selectBirthMonth')} format={resolveRuntimeLocale() === 'en-US' ? 'YYYY-MM' : 'YYYY年MM月'} style={{ width: '100%' }} />
    </Form.Item>
    <Form.Item name="gender" label={t('ui.system.users.gender')}>
      <Select allowClear options={genderOptions} placeholder={t('ui.system.users.selectGender')} />
    </Form.Item>
    <Form.Item name="region" label={t('ui.system.users.region')} normalize={trimString}>
      <Input />
    </Form.Item>
    <Form.Item name="availableTime" label={t('ui.system.users.availableTime')} normalize={trimString}>
      <Input.TextArea rows={2} placeholder={t('ui.system.users.enterAvailableTimeEGMonFri09')} />
    </Form.Item>
  </Form>
);

const UserManagementPage = () => {
  const userManagement = useUserManagement();
  const { options: genderOptions } = useDictOptions('sys_user_gender', USER_GENDER_DICT_FALLBACK_OPTIONS);
  const { options: userStatusOptions } = useDictOptions('sys_user_status', USER_STATUS_DICT_FALLBACK_OPTIONS);

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
    <ManagementPage title={t('ui.system.users.userManagement')} className="saas-user-management-page">
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
              adaptiveSpacing
              containerResponsive
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
                    label: t('ui.system.users.addUser'),
                    onClick: () => void openCreate(),
                  },
                  {
                    key: 'export',
                    permission: 'system:user:export',
                    icon: <DownloadOutlined />,
                    label: t('ui.system.users.exportUsers'),
                    onClick: () => void openExport(),
                  },
                  {
                    key: 'refresh',
                    label: t('ui.system.users.refresh'),
                    onClick: () => void actionRef.current?.reload(),
                  },
                ])
              }
            />
          </div>
        </div>
      </ManagementPageBody>

      <ManagementDrawer
        title={drawer.editingId ? t('ui.system.users.editUser') : t('ui.system.users.addUser')}
        open={drawer.open}
        onClose={drawer.close}
        footerActions={[
          { key: 'cancel', label: t('ui.system.users.cancel'), onClick: drawer.close },
          { key: 'save', label: t('ui.system.users.save'), type: 'primary', loading: saving, disabled: !canSaveUser, onClick: () => void saveUser() },
        ]}
      >
        <UserEditorForm
          formProps={editorFormProps}
          editingId={drawer.editingId}
          roleOptions={roleOptions}
          departmentOptions={departmentOptions}
          protectedAdminSelected={protectedAdminSelected}
          securitySettings={securitySettings}
          genderOptions={genderOptions}
          userStatusOptions={userStatusOptions}
        />
      </ManagementDrawer>

      <ManagementDrawer title={detail.currentRecord?.username ? `${t('ui.system.users.userDetails')} · ${detail.currentRecord.username}` : t('ui.system.users.userDetails')} open={detail.open} onClose={detail.close}>
        {detail.loading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 'var(--saas-spacing-240)' }}>
            <Spin />
          </div>
        ) : selectedUserDetail ? (
          <ProDescriptions<UserDetail> {...detailProps} columns={userDetailColumns} />
        ) : null}
      </ManagementDrawer>

      <Modal
        title={t('ui.system.users.exportUsers')}
        open={exportModalOpen}
        onCancel={() => setExportModalOpen(false)}
        onOk={() => void confirmExport()}
        okText={t('ui.system.users.startExport')}
        cancelText={t('ui.system.users.cancel')}
        confirmLoading={exportLoading}
        width={720}
        destroyOnHidden
      >
        <Transfer
          dataSource={exportFields.map((field) => ({ key: field.key, title: field.label }))}
          titles={[t('ui.system.users.availableFields'), t('ui.system.users.exportFields')]}
          targetKeys={selectedExportFields}
          onChange={(nextTargetKeys) => setSelectedExportFields(nextTargetKeys.map(String))}
          render={(item) => item.title}
          listStyle={{ width: 300, height: 360 }}
          showSearch
        />
      </Modal>

      <Modal
        title={t('ui.system.users.exportTask')}
        open={exportTaskOpen}
        onCancel={() => setExportTaskOpen(false)}
        footer={[
          <Button key="close" onClick={() => setExportTaskOpen(false)}>
            {t('ui.system.users.close')}
          </Button>,
          <Button key="center" onClick={openDownloadCenter}>
            {t('ui.system.users.downloadCenter')}
          </Button>,
          <Button key="download" type="primary" disabled={exportTask?.status !== 'SUCCESS' || !exportTask.downloadUrl} onClick={downloadExportTaskFile}>
            {t('ui.system.users.downloadFile')}
          </Button>,
        ]}
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Alert
            type={exportTask?.status === 'FAILED' ? 'error' : exportTask?.status === 'SUCCESS' ? 'success' : 'info'}
            showIcon
            message={
              exportTask?.status === 'SUCCESS'
                ? t('ui.system.users.exportComplete')
                : exportTask?.status === 'FAILED'
                  ? t('ui.system.users.exportFailed')
                  : t('ui.system.users.exportInProgress')
            }
            description={exportTask?.errorMessage || t('ui.system.users.largeExportsAreGeneratedInTheBackgroundAnd')}
          />
          <Typography.Text type="secondary">
            {t('ui.system.users.records')}: {exportTask?.totalCount ?? '-'}
          </Typography.Text>
          <Typography.Text type="secondary">
            {t('ui.system.users.fileName')}: {exportTask?.fileName || '-'}
          </Typography.Text>
        </Space>
      </Modal>
    </ManagementPage>
  );
};

export default UserManagementPage;
