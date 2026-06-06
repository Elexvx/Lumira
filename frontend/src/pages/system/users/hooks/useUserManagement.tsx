import { Form, Tag, Typography } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettingsTypes';
import { normalizeSecuritySettings } from '@/auth/securitySettingsNormalize';
import type { ProColumns } from '@ant-design/pro-components';
import { useCrudPageState } from '@/features/crud/useCrudPageState';
import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { useStandardFormProps } from '@/features/form/config';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import type { PermissionAwareTableAction } from '@/features/permissions/useActionPermission';
import type { TableActionItem } from '@/features/table/TableActionBar';
import { buildTableRequest } from '@/features/table/proTableRequest';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { request } from '@/services/common/request';
import { API_OPTS } from '@/utils/errorMessage';
import { confirmAction } from '@/utils/confirm';
import type { DepartmentRecord, PagedResult, RoleRecord, UserDetail, UserRecord } from '@/types/api';
import { maskEmail, maskMobile } from '@/utils/sensitive';
import { TableActionBar } from '@/features/table/TableActionBar';

const PROTECTED_ADMIN_ID = 1001;
const PROTECTED_ADMIN_USERNAME = 'admin';

const isProtectedAdminUserAccount = (user?: Pick<UserRecord, 'id' | 'username'> | null) =>
  Boolean(user && (user.id === PROTECTED_ADMIN_ID || user.username?.toLowerCase() === PROTECTED_ADMIN_USERNAME));

const flattenDepartments = (departments: DepartmentRecord[], depth = 0): { label: string; value: number }[] =>
  departments.flatMap((department) => [
    { label: `${'　'.repeat(depth)}${department.deptName}`, value: department.id },
    ...flattenDepartments(department.children || [], depth + 1),
  ]);

type UserOption = { label: string; value: number };

const userListIdentityColumns: ProColumns<UserRecord>[] = [
  {
    title: '用户ID',
    dataIndex: 'id',
    search: {
      transform: (value) => ({ userId: value ? Number(value) : undefined }),
    },
    width: 'var(--saas-spacing-96)',
  },
  {
    title: '用户编号',
    dataIndex: 'userNo',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
  },
  {
    title: '用户名',
    dataIndex: 'username',
    search: true,
  },
];

const userListContactColumns: ProColumns<UserRecord>[] = [
  {
    title: '手机号',
    dataIndex: 'mobile',
    search: false,
    ellipsis: true,
    render: (_, record) => {
      const content = maskMobile(record.mobile) || '';
      return content ? (
        <Typography.Text copyable={{ text: content }} ellipsis={{ tooltip: content }}>
          {content}
        </Typography.Text>
      ) : (
        '-'
      );
    },
  },
  {
    title: '邮箱',
    dataIndex: 'email',
    search: false,
    ellipsis: true,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    render: (_, record) => {
      const content = maskEmail(record.email) || '';
      return content ? <Typography.Text ellipsis={{ tooltip: content }}>{content}</Typography.Text> : '-';
    },
  },
];

const userListStatusColumns: ProColumns<UserRecord>[] = [
  {
    title: '状态',
    dataIndex: 'status',
    valueEnum: {
      ENABLED: { text: '启用', status: 'Success' },
      DISABLED: { text: '禁用', status: 'Default' },
    },
    search: false,
    render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{record.status}</Tag>,
  },
  {
    title: '来源',
    dataIndex: 'source',
    valueEnum: {
      LEGACY_SYS_USER: { text: '旧系统迁移' },
      PASSWORD: { text: '账号密码' },
      SMS: { text: '短信注册' },
      EMAIL: { text: '邮箱注册' },
      WECHAT: { text: '微信' },
      ADMIN_CREATE: { text: '后台创建' },
      SYSTEM: { text: '系统' },
    },
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
  },
  {
    title: '注册时间',
    dataIndex: 'registeredAt',
    valueType: 'dateRange',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    renderText: (value) => value || '-',
  },
  {
    title: '最近登录',
    dataIndex: 'lastLoginAt',
    valueType: 'dateRange',
    search: false,
    responsive: ['xl', 'xxl'],
    renderText: (value) => value || '-',
  },
];

interface BuildUserColumnsOptions {
  isMobile: boolean;
  buildRowActions: (items: PermissionAwareTableAction[]) => TableActionItem[];
  onOpenDetail: (record: UserRecord) => void;
  onOpenEdit: (record: UserRecord) => void;
  onToggleStatus: (record: UserRecord) => void;
  onDelete: (record: UserRecord) => void;
  isProtectedAdminAccount: (record?: Pick<UserRecord, 'id' | 'username'> | null) => boolean;
}

const buildUserColumns = ({
  isMobile,
  buildRowActions,
  onOpenDetail,
  onOpenEdit,
  onToggleStatus,
  onDelete,
  isProtectedAdminAccount,
}: BuildUserColumnsOptions): ProColumns<UserRecord>[] => [
  ...userListIdentityColumns,
  ...userListContactColumns,
  ...userListStatusColumns,
  {
    title: '昵称',
    dataIndex: 'nickname',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
  },
  {
    title: '姓名',
    dataIndex: 'realName',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
  },
  {
    title: '角色',
    dataIndex: 'roleNames',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_, record) => {
      const content = record.roleNames?.length ? record.roleNames.join(', ') : '';
      return content ? <Typography.Text ellipsis={{ tooltip: content }}>{content}</Typography.Text> : '-';
    },
  },
  {
    title: '部门',
    dataIndex: 'deptNames',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_, record) => {
      const content = record.deptNames?.length ? record.deptNames.join(', ') : '';
      return content ? <Typography.Text ellipsis={{ tooltip: content }}>{content}</Typography.Text> : '-';
    },
  },
  {
    title: '操作',
    valueType: 'option',
    fixed: 'right',
    width: 'var(--saas-spacing-220)',
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={buildRowActions([
          {
            key: 'view',
            label: '详情',
            permission: 'system:user:view',
            onClick: () => onOpenDetail(record),
          },
          {
            key: 'edit',
            label: '编辑',
            permission: 'system:user:update',
            onClick: () => onOpenEdit(record),
          },
          {
            key: 'toggle',
            label: record.status === 'ENABLED' ? '禁用' : '启用',
            permission: 'system:user:status',
            hidden: isProtectedAdminAccount(record),
            danger: record.status === 'ENABLED',
            onClick: () => onToggleStatus(record),
          },
          {
            key: 'delete',
            label: '删除',
            permission: 'system:user:delete',
            hidden: isProtectedAdminAccount(record),
            danger: true,
            onClick: () => onDelete(record),
          },
        ])}
      />
    ),
  },
];

export const useUserManagement = () => {
  const { initialState } = useInitialStateModel();
  const { actionPermission, responsive, searchConfig, buildToolbarButtons } = usePagePermissionActions();
  const { actionRef, drawer, detail, reloadTable } = useCrudPageState<UserRecord>();
  const securitySettings = useMemo(
    () => normalizeSecuritySettings(initialState?.securitySettings || DEFAULT_SECURITY_SETTINGS),
    [initialState?.securitySettings],
  );
  const [roleOptions, setRoleOptions] = useState<UserOption[]>([]);
  const [roleOptionsLoaded, setRoleOptionsLoaded] = useState(false);
  const [departmentOptions, setDepartmentOptions] = useState<UserOption[]>([]);
  const [departmentOptionsLoaded, setDepartmentOptionsLoaded] = useState(false);
  const [departments, setDepartments] = useState<DepartmentRecord[]>([]);
  const [departmentLoading, setDepartmentLoading] = useState(false);
  const [selectedDepartmentId, setSelectedDepartmentId] = useState<number | null>(null);
  const [editorForm] = Form.useForm();
  const [selectedUserDetail, setSelectedUserDetail] = useState<UserDetail | null>(null);
  const [saving, setSaving] = useState(false);

  const loadDepartments = useCallback(async () => {
    setDepartmentLoading(true);
    try {
      const result = await request<DepartmentRecord[]>('/v1/system/departments', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      });
      setDepartments(result);
      setDepartmentOptions(flattenDepartments(result));
      setDepartmentOptionsLoaded(true);
    } finally {
      setDepartmentLoading(false);
    }
  }, []);

  const ensureRoleOptionsLoaded = useCallback(async () => {
    if (roleOptionsLoaded) {
      return;
    }
    const result = await request<PagedResult<RoleRecord>>('/v1/system/roles', {
      method: 'GET',
      params: { pageNo: 1, pageSize: 200 },
      ...API_OPTS.NO_REDIRECT,
    });
    setRoleOptions(
      (result.records || []).map((role) => ({
        label: role.roleName,
        value: role.id,
      })),
    );
    setRoleOptionsLoaded(true);
  }, [roleOptionsLoaded]);

  const ensureDepartmentOptionsLoaded = useCallback(async () => {
    if (departmentOptionsLoaded) {
      return;
    }
    await loadDepartments();
  }, [departmentOptionsLoaded, loadDepartments]);

  useEffect(() => {
    void loadDepartments();
  }, [loadDepartments]);

  const protectedAdminSelected = useMemo(
    () => isProtectedAdminUserAccount(drawer.currentRecord ? { id: drawer.currentRecord.id, username: drawer.currentRecord.username } : null),
    [drawer.currentRecord],
  );
  const canSaveUser = actionPermission.can(drawer.editingId ? 'system:user:update' : 'system:user:create');
  const editorFormProps = useStandardFormProps({
    form: editorForm,
    initialValues: { status: 'ENABLED', roleIds: [], deptIds: [] },
  });
  const detailProps = useDetailProDescriptionsProps<UserDetail>({
    column: responsive.isMobile ? 1 : 2,
    dataSource: selectedUserDetail || undefined,
  });

  const openCreate = useCallback(async () => {
    drawer.openCreate();
    editorForm.resetFields();
    editorForm.setFieldsValue({
      status: 'ENABLED',
      roleIds: [],
      deptIds: selectedDepartmentId ? [selectedDepartmentId] : [],
      primaryDeptId: selectedDepartmentId || null,
    });
    try {
      await Promise.all([ensureRoleOptionsLoaded(), ensureDepartmentOptionsLoaded()]);
    } catch {
      drawer.reset();
    }
  }, [drawer, editorForm, ensureDepartmentOptionsLoaded, ensureRoleOptionsLoaded, selectedDepartmentId]);

  const openEdit = useCallback(
    async (record: UserRecord) => {
      drawer.openEdit(record, record.id);
      try {
        const [detailResult] = await Promise.all([
          request<UserDetail>(`/v1/system/users/${record.id}`, {
            method: 'GET',
            ...API_OPTS.NO_REDIRECT,
          }),
          ensureRoleOptionsLoaded(),
          ensureDepartmentOptionsLoaded(),
        ]);
        editorForm.setFieldsValue({
          ...detailResult,
          birthMonth: detailResult.birthMonth ? dayjs(detailResult.birthMonth, 'YYYY-MM') : null,
          roleIds: detailResult.roleIds || [],
          deptIds: detailResult.deptIds || [],
          primaryDeptId: detailResult.primaryDeptId || null,
        });
      } catch {
        drawer.reset();
      }
    },
    [drawer, editorForm, ensureDepartmentOptionsLoaded, ensureRoleOptionsLoaded],
  );

  const openDetail = useCallback(
    async (record: UserRecord) => {
      detail.openDetail(record);
      detail.setLoading(true);
      try {
        const detailResult = await request<UserDetail>(`/v1/system/users/${record.id}`, {
          method: 'GET',
          ...API_OPTS.NO_REDIRECT,
        });
        setSelectedUserDetail(detailResult);
      } catch {
        detail.setOpen(false);
        setSelectedUserDetail(null);
      } finally {
        detail.setLoading(false);
      }
    },
    [detail],
  );

  const saveUser = useCallback(async () => {
    setSaving(true);
    try {
      const values = await editorForm.validateFields();
      const isCreating = !drawer.editingId;
      const payload = {
        ...values,
        birthMonth: values.birthMonth ? dayjs(values.birthMonth).format('YYYY-MM') : '',
        roleIds: values.roleIds || [],
        deptIds: values.deptIds || [],
        primaryDeptId: values.primaryDeptId || values.deptIds?.[0] || null,
      };

      if (drawer.editingId) {
        await request<UserDetail>(`/v1/system/users/${drawer.editingId}`, {
          method: 'PUT',
          data: payload,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success('用户已更新');
      } else {
        await request<UserDetail>('/v1/system/users', {
          method: 'POST',
          data: payload,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success('用户已创建');
      }

      drawer.close();
      if (isCreating && selectedDepartmentId !== null) {
        setSelectedDepartmentId(null);
      } else {
        reloadTable();
      }
      await loadDepartments();
    } finally {
      setSaving(false);
    }
  }, [drawer.editingId, editorForm, loadDepartments, reloadTable, selectedDepartmentId, setSelectedDepartmentId]);

  const handleStatusToggle = useCallback(
    async (record: UserRecord) => {
      if (record.status !== 'ENABLED') {
        await request<boolean>(`/v1/system/users/${record.id}/status`, {
          method: 'PATCH',
          data: { status: 'ENABLED' },
          ...API_OPTS.NO_REDIRECT,
        });
        message.success('状态已更新');
        reloadTable();
        return;
      }

      confirmAction({
        title: '禁用用户',
        content: `确认禁用用户「${record.username}」吗？禁用后该账号将无法继续登录。`,
        okText: '确认禁用',
        okButtonProps: { danger: true },
        onOk: async () => {
          await request<boolean>(`/v1/system/users/${record.id}/status`, {
            method: 'PATCH',
            data: { status: 'DISABLED' },
            ...API_OPTS.NO_REDIRECT,
          });
          message.success('状态已更新');
          reloadTable();
        },
      });
    },
    [reloadTable],
  );

  const handleDelete = useCallback(
    (record: UserRecord) => {
      confirmAction({
        title: '删除用户',
        content: `确认删除用户「${record.username}」吗？删除后该账号将无法恢复。`,
        okText: '确认删除',
        okButtonProps: { danger: true },
        onOk: async () => {
          await request<boolean>(`/v1/system/users/${record.id}`, {
            method: 'DELETE',
            ...API_OPTS.NO_REDIRECT,
          });
          reloadTable();
          await loadDepartments();
        },
      });
    },
    [loadDepartments, reloadTable],
  );

  const columns = useMemo(
    () =>
      buildUserColumns({
        isMobile: responsive.isMobile,
        buildRowActions: actionPermission.buildTableActions,
        onOpenDetail: (record: UserRecord) => void openDetail(record),
        onOpenEdit: (record: UserRecord) => void openEdit(record),
        onToggleStatus: (record: UserRecord) => void handleStatusToggle(record),
        onDelete: (record: UserRecord) => void handleDelete(record),
        isProtectedAdminAccount: (record) =>
          isProtectedAdminUserAccount(record ? { id: record.id, username: record.username } : null),
      }),
    [actionPermission.buildTableActions, handleDelete, handleStatusToggle, openDetail, openEdit, responsive.isMobile],
  );

  const tableRequest = useMemo(
    () =>
      buildTableRequest((params) =>
        request<PagedResult<UserRecord>>('/v1/system/users', {
          method: 'GET',
          params: {
            ...params,
            deptId: selectedDepartmentId || undefined,
          },
          ...API_OPTS.NO_REDIRECT,
        }),
      ),
    [selectedDepartmentId],
  );

  return {
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
    openEdit,
    saveUser,
    handleStatusToggle,
    handleDelete,
    loadDepartments,
    setSelectedUserDetail,
  };
};
