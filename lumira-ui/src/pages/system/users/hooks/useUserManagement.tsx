import { Form, Tag, Typography } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

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
type UserExportField = { key: string; label: string; defaultSelected: boolean; orderNo: number };
type UserExportStart = {
  mode: 'SYNC' | 'ASYNC';
  taskId?: number;
  fileName?: string;
  contentType?: string;
  contentBase64?: string;
  totalCount?: number;
};
type UserExportTask = {
  id: number;
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
  totalCount?: number;
  fileId?: number | null;
  fileName?: string | null;
  downloadUrl?: string | null;
  errorMessage?: string | null;
};

const downloadBase64File = (contentBase64: string, contentType: string, fileName: string) => {
  const binary = atob(contentBase64);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  const blob = new Blob([bytes], { type: contentType });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
};

const exportableQueryParams = (params: Record<string, unknown>, deptId: number | null) => {
  const { pageNo: _pageNo, pageSize: _pageSize, current: _current, cursorId: _cursorId, cursorCreatedAt: _cursorCreatedAt, ...rest } = params;
  return {
    ...rest,
    deptId: deptId || undefined,
  };
};

const userListIdentityColumns: ProColumns<UserRecord>[] = [
  {
    title: t('用户ID', 'User ID'),
    dataIndex: 'id',
    search: {
      transform: (value) => ({ userId: value ? Number(value) : undefined }),
    },
    width: 'var(--saas-spacing-96)',
  },
  {
    title: t('用户编号', 'User number'),
    dataIndex: 'userNo',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
  },
  {
    title: t('用户名', 'Username'),
    dataIndex: 'username',
    search: true,
  },
];

const userListContactColumns: ProColumns<UserRecord>[] = [
  {
    title: t('手机号', 'Mobile number'),
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
    title: t('邮箱', 'Email'),
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
    title: t('状态', 'Status'),
    dataIndex: 'status',
    valueEnum: {
      ENABLED: { text: t('启用', 'Enabled'), status: 'Success' },
      DISABLED: { text: t('禁用', 'Disabled'), status: 'Default' },
    },
    search: false,
    render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{record.status === 'ENABLED' ? t('启用', 'Enabled') : t('禁用', 'Disabled')}</Tag>,
  },
  {
    title: t('来源', 'Source'),
    dataIndex: 'source',
    valueEnum: {
      LEGACY_SYS_USER: { text: t('旧系统迁移', 'Legacy migration') },
      PASSWORD: { text: t('账号密码', 'Username/password') },
      SMS: { text: t('短信注册', 'SMS sign-up') },
      EMAIL: { text: t('邮箱注册', 'Email sign-up') },
      WECHAT: { text: t('微信', 'WeChat') },
      ADMIN_CREATE: { text: t('后台创建', 'Admin created') },
      SYSTEM: { text: t('系统', 'System') },
    },
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
  },
  {
    title: t('注册时间', 'Registered at'),
    dataIndex: 'registeredAt',
    valueType: 'dateRange',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    renderText: (value) => value || '-',
  },
  {
    title: t('最近登录', 'Last login'),
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
    title: t('昵称', 'Nickname'),
    dataIndex: 'nickname',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
  },
  {
    title: t('姓名', 'Full name'),
    dataIndex: 'realName',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
  },
  {
    title: t('角色', 'Roles'),
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
    title: t('部门', 'Departments'),
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
    title: t('操作', 'Actions'),
    valueType: 'option',
    fixed: 'right',
    width: 'var(--saas-spacing-220)',
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={buildRowActions([
          {
            key: 'view',
            label: t('详情', 'Details'),
            permission: 'system:user:view',
            onClick: () => onOpenDetail(record),
          },
          {
            key: 'edit',
            label: t('编辑', 'Edit'),
            permission: 'system:user:update',
            onClick: () => onOpenEdit(record),
          },
          {
            key: 'toggle',
            label: record.status === 'ENABLED' ? t('禁用', 'Disable') : t('启用', 'Enable'),
            permission: 'system:user:status',
            hidden: isProtectedAdminAccount(record),
            danger: record.status === 'ENABLED',
            onClick: () => onToggleStatus(record),
          },
          {
            key: 'delete',
            label: t('删除', 'Delete'),
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
  const selectedDepartmentInitializedRef = useRef(false);
  const [editorForm] = Form.useForm();
  const [selectedUserDetail, setSelectedUserDetail] = useState<UserDetail | null>(null);
  const [saving, setSaving] = useState(false);
  const lastUserQueryParamsRef = useRef<Record<string, unknown>>({});
  const [exportModalOpen, setExportModalOpen] = useState(false);
  const [exportFields, setExportFields] = useState<UserExportField[]>([]);
  const [selectedExportFields, setSelectedExportFields] = useState<string[]>([]);
  const [exportLoading, setExportLoading] = useState(false);
  const [exportTaskOpen, setExportTaskOpen] = useState(false);
  const [exportTask, setExportTask] = useState<UserExportTask | null>(null);

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

  useEffect(() => {
    if (!selectedDepartmentInitializedRef.current) {
      selectedDepartmentInitializedRef.current = true;
      return;
    }
    reloadTable();
  }, [reloadTable, selectedDepartmentId]);

  useEffect(() => {
    if (!exportTaskOpen || !exportTask?.id || ['SUCCESS', 'FAILED'].includes(exportTask.status)) {
      return undefined;
    }
    const timer = window.setInterval(async () => {
      try {
        const result = await request<UserExportTask>(`/v1/system/export-tasks/${exportTask.id}`, {
          method: 'GET',
          ...API_OPTS.NO_REDIRECT,
          silent: true,
        });
        setExportTask(result);
      } catch {
        window.clearInterval(timer);
      }
    }, 3000);
    return () => window.clearInterval(timer);
  }, [exportTask?.id, exportTask?.status, exportTaskOpen]);

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
    className: 'saas-user-detail-descriptions',
    column: 1,
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
        message.success(t('用户已更新', 'User updated'));
      } else {
        await request<UserDetail>('/v1/system/users', {
          method: 'POST',
          data: payload,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('用户已创建', 'User created'));
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
  }, [drawer, editorForm, loadDepartments, reloadTable, selectedDepartmentId, setSelectedDepartmentId]);

  const handleStatusToggle = useCallback(
    async (record: UserRecord) => {
      if (record.status !== 'ENABLED') {
        await request<boolean>(`/v1/system/users/${record.id}/status`, {
          method: 'PATCH',
          data: { status: 'ENABLED' },
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('状态已更新', 'Status updated'));
        reloadTable();
        return;
      }

      confirmAction({
        title: t('禁用用户', 'Disable user'),
        content: t(`确认禁用用户「${record.username}」吗？禁用后该账号将无法继续登录。`, `Disable user "${record.username}"? This account will no longer be able to sign in.`),
        okText: t('确认禁用', 'Disable'),
        okButtonProps: { danger: true },
        onOk: async () => {
          await request<boolean>(`/v1/system/users/${record.id}/status`, {
            method: 'PATCH',
            data: { status: 'DISABLED' },
            ...API_OPTS.NO_REDIRECT,
          });
          message.success(t('状态已更新', 'Status updated'));
          reloadTable();
        },
      });
    },
    [reloadTable],
  );

  const handleDelete = useCallback(
    (record: UserRecord) => {
      confirmAction({
        title: t('删除用户', 'Delete user'),
        content: t(`确认删除用户「${record.username}」吗？删除后该账号将无法恢复。`, `Delete user "${record.username}"? This action cannot be undone.`),
        okText: t('确认删除', 'Delete'),
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

  const openExport = useCallback(async () => {
    setExportLoading(true);
    try {
      const fields = exportFields.length
        ? exportFields
        : await request<UserExportField[]>('/v1/system/users/export-fields', {
            method: 'GET',
            ...API_OPTS.NO_REDIRECT,
          });
      const orderedFields = [...fields].sort((left, right) => left.orderNo - right.orderNo);
      setExportFields(orderedFields);
      setSelectedExportFields((current) =>
        current.length ? current : orderedFields.filter((field) => field.defaultSelected).map((field) => field.key),
      );
      setExportModalOpen(true);
    } finally {
      setExportLoading(false);
    }
  }, [exportFields]);

  const confirmExport = useCallback(async () => {
    if (!selectedExportFields.length) {
      message.warning(t('请至少选择一个导出字段', 'Please select at least one export field'));
      return;
    }
    setExportLoading(true);
    try {
      const result = await request<UserExportStart>('/v1/system/users/export', {
        method: 'POST',
        data: {
          ...exportableQueryParams(lastUserQueryParamsRef.current, selectedDepartmentId),
          fields: selectedExportFields,
        },
        timeoutMs: 120000,
        ...API_OPTS.NO_REDIRECT,
      });
      if (result.mode === 'SYNC' && result.contentBase64) {
        downloadBase64File(
          result.contentBase64,
          result.contentType || 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          result.fileName || t('用户管理导出.xlsx', 'User management export.xlsx'),
        );
        message.success(t('导出文件已生成', 'Export file generated'));
        setExportModalOpen(false);
        return;
      }
      if (result.mode === 'ASYNC' && result.taskId) {
        setExportTask({
          id: result.taskId,
          status: 'PENDING',
          totalCount: result.totalCount,
          fileName: result.fileName,
        });
        setExportTaskOpen(true);
        setExportModalOpen(false);
        message.success(t('数据较多，已创建异步导出任务', 'Large export task created'));
      }
    } finally {
      setExportLoading(false);
    }
  }, [selectedDepartmentId, selectedExportFields]);

  const downloadExportTaskFile = useCallback(() => {
    if (exportTask?.downloadUrl) {
      window.open(exportTask.downloadUrl, '_blank', 'noopener,noreferrer');
    }
  }, [exportTask?.downloadUrl]);

  const openDownloadCenter = useCallback(() => {
    window.location.assign('/download-center');
  }, []);

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
      buildTableRequest((params) => {
        lastUserQueryParamsRef.current = params;
        return request<PagedResult<UserRecord>>('/v1/system/users', {
          method: 'GET',
          params: {
            ...params,
            deptId: selectedDepartmentId || undefined,
          },
          ...API_OPTS.NO_REDIRECT,
        });
      }),
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
    openEdit,
    openExport,
    confirmExport,
    downloadExportTaskFile,
    openDownloadCenter,
    saveUser,
    handleStatusToggle,
    handleDelete,
    loadDepartments,
    setSelectedUserDetail,
  };
};
