import { Form, Space, Tag, Typography } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettingsTypes';
import { normalizeSecuritySettings } from '@/auth/securitySettingsNormalize';
import { notifyCurrentUserSync } from '@/auth/currentUserSync';
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
import type { DepartmentRecord, PagedResult, ProfileFieldSetting, RoleRecord, UserDetail, UserRecord } from '@/types/api';
import { maskEmail, maskMobile } from '@/utils/sensitive';
import { TableActionBar } from '@/features/table/TableActionBar';
import { UserAvatar } from '@/components/UserAvatar';
import { buildUserEditorPayload, normalizeExtraProfileValuesForEditor } from '@/pages/system/users/userEditorPayload';

import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

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

const formatDateRangeBoundary = (value: unknown, index: 0 | 1) => {
  if (!Array.isArray(value)) {
    return undefined;
  }
  const boundary = value[index];
  if (boundary == null || boundary === '') {
    return undefined;
  }
  const parsed = dayjs(boundary);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD') : undefined;
};

const normalizeUserQueryParams = (params: Record<string, unknown>, deptId: number | null) => {
  const { registeredAt, lastLoginAt, id, uid, userUuid, ...rest } = params;
  const uidValue = uid || userUuid || id || rest.userId;

  return {
    ...rest,
    uid: uidValue || undefined,
    deptId: deptId || undefined,
    registeredStart: formatDateRangeBoundary(registeredAt, 0),
    registeredEnd: formatDateRangeBoundary(registeredAt, 1),
    lastLoginStart: formatDateRangeBoundary(lastLoginAt, 0),
    lastLoginEnd: formatDateRangeBoundary(lastLoginAt, 1),
  };
};

const exportableQueryParams = (params: Record<string, unknown>) => {
  const {
    pageNo: _pageNo,
    pageSize: _pageSize,
    current: _current,
    cursorId: _cursorId,
    cursorCreatedAt: _cursorCreatedAt,
    ...rest
  } = params;
  return {
    ...rest,
  };
};

const userListIdentityColumns: ProColumns<UserRecord>[] = [
  {
    title: t('ui.system.users.useuser.no'),
    search: false,
    width: 'var(--saas-spacing-80)',
    align: 'center',
    render: (_: unknown, __: UserRecord, index: number) => index + 1,
  },
  {
    title: 'UID',
    dataIndex: 'uid',
    search: true,
    width: 320,
    render: (_, record) => {
      const uid = record.uid || record.userUuid || String(record.id);
      return <Typography.Text copyable={{ text: uid }} ellipsis={{ tooltip: uid }}>{uid}</Typography.Text>;
    },
  },
  {
    title: t('ui.system.users.useuser.userNumber'),
    dataIndex: 'userNo',
    hideInTable: true,
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
  },
  {
    title: t('ui.system.users.useuser.username'),
    dataIndex: 'username',
    search: true,
    width: 200,
    render: (_, record) => (
      <Space size="small" wrap={false}>
        <UserAvatar
          size="small"
          avatarUrl={record.avatarUrl}
          userId={record.id}
          userUuid={record.userUuid || record.uid}
          username={record.username}
        />
        <Typography.Text ellipsis={{ tooltip: record.username }}>{record.username}</Typography.Text>
      </Space>
    ),
  },
];

const userListContactColumns: ProColumns<UserRecord>[] = [
  {
    title: t('ui.system.users.useuser.mobileNumber'),
    dataIndex: 'mobile',
    search: true,
    responsive: ['lg', 'xl', 'xxl'],
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
    title: t('ui.system.users.useuser.email'),
    dataIndex: 'email',
    search: true,
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
    title: t('ui.system.users.useuser.status'),
    dataIndex: 'status',
    responsive: ['lg', 'xl', 'xxl'],
    valueEnum: {
      ENABLED: { text: t('ui.system.users.useuser.enabled'), status: 'Success' },
      DISABLED: { text: t('ui.system.users.useuser.disabled'), status: 'Default' },
    },
    search: true,
    render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{record.status === 'ENABLED' ? t('ui.system.users.useuser.enabled') : t('ui.system.users.useuser.disabled')}</Tag>,
  },
  {
    title: t('ui.system.users.useuser.source'),
    dataIndex: 'source',
    valueEnum: {
      LEGACY_SYS_USER: { text: t('ui.system.users.useuser.legacyMigration') },
      PASSWORD: { text: t('ui.system.users.useuser.usernamePassword') },
      SMS: { text: t('ui.system.users.useuser.smsSignUp') },
      EMAIL: { text: t('ui.system.users.useuser.emailSignUp') },
      WECHAT: { text: t('ui.system.users.useuser.wechat') },
      ADMIN_CREATE: { text: t('ui.system.users.useuser.adminCreated') },
      SYSTEM: { text: t('ui.system.users.useuser.system') },
    },
    search: true,
    responsive: ['lg', 'xl', 'xxl'],
  },
  {
    title: t('ui.system.users.useuser.registeredAt'),
    dataIndex: 'registeredAt',
    valueType: 'dateRange',
    search: true,
    responsive: ['lg', 'xl', 'xxl'],
    renderText: (value) => value || '-',
  },
  {
    title: t('ui.system.users.useuser.lastLogin'),
    dataIndex: 'lastLoginAt',
    valueType: 'dateRange',
    search: true,
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
    title: t('ui.system.users.useuser.nickname'),
    dataIndex: 'nickname',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
  },
  {
    title: t('ui.system.users.useuser.fullName'),
    dataIndex: 'realName',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
  },
  {
    title: t('ui.system.users.useuser.roles'),
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
    title: t('ui.system.users.useuser.departments'),
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
    title: t('ui.system.users.useuser.actions'),
    valueType: 'option',
    fixed: 'right',
    width: 'var(--saas-spacing-220)',
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={buildRowActions([
          {
            key: 'view',
            label: t('ui.system.users.useuser.details'),
            permission: 'system:user:view',
            onClick: () => onOpenDetail(record),
          },
          {
            key: 'edit',
            label: t('ui.system.users.useuser.edit'),
            permission: 'system:user:update',
            onClick: () => onOpenEdit(record),
          },
          {
            key: 'toggle',
            label: record.status === 'ENABLED' ? t('ui.system.users.useuser.disable') : t('ui.system.users.useuser.enable'),
            permission: 'system:user:status',
            hidden: isProtectedAdminAccount(record),
            danger: record.status === 'ENABLED',
            onClick: () => onToggleStatus(record),
          },
          {
            key: 'delete',
            label: t('ui.system.users.useuser.delete'),
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
  const userSearchConfig = useMemo(
    () => ({
      ...searchConfig,
      defaultCollapsed: true,
    }),
    [searchConfig],
  );
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
  const [profileFields, setProfileFields] = useState<ProfileFieldSetting[]>([]);
  const [profileFieldsLoaded, setProfileFieldsLoaded] = useState(false);
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

  const ensureProfileFieldsLoaded = useCallback(async (): Promise<ProfileFieldSetting[]> => {
    if (profileFieldsLoaded) {
      return profileFields;
    }
    try {
      const result = await request<ProfileFieldSetting[]>('/v1/system/profile-field-settings?pageKey=PROFILE', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      });
      const nextFields = (result || [])
        .filter((item) => item.visible !== false)
        .sort((left, right) => (left.sortNo ?? 1000) - (right.sortNo ?? 1000));
      setProfileFields(nextFields);
      setProfileFieldsLoaded(true);
      return nextFields;
    } catch {
      // Users who can manage accounts may not have configuration-view permission.
      // Keep the account fields usable and show an empty profile tab in that case.
      setProfileFields([]);
      setProfileFieldsLoaded(true);
      return [];
    }
  }, [profileFields, profileFieldsLoaded]);

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
      extraProfileValues: {},
    });
    try {
      await Promise.all([ensureRoleOptionsLoaded(), ensureDepartmentOptionsLoaded(), ensureProfileFieldsLoaded()]);
    } catch {
      drawer.reset();
    }
  }, [drawer, editorForm, ensureDepartmentOptionsLoaded, ensureProfileFieldsLoaded, ensureRoleOptionsLoaded, selectedDepartmentId]);

  const openEdit = useCallback(
    async (record: UserRecord) => {
      editorForm.resetFields();
      drawer.openEdit(record, record.id);
      try {
        const [detailResult, , , loadedProfileFields] = await Promise.all([
          request<UserDetail>(`/v1/system/users/${record.id}`, {
            method: 'GET',
            ...API_OPTS.NO_REDIRECT,
          }),
          ensureRoleOptionsLoaded(),
          ensureDepartmentOptionsLoaded(),
          ensureProfileFieldsLoaded(),
        ]);
        editorForm.setFieldsValue({
          ...detailResult,
          password: undefined,
          resetPassword: false,
          birthMonth: detailResult.birthMonth ? dayjs(detailResult.birthMonth, 'YYYY-MM') : null,
          roleIds: detailResult.roleIds || [],
          deptIds: detailResult.deptIds || [],
          primaryDeptId: detailResult.primaryDeptId || null,
          extraProfileValues: normalizeExtraProfileValuesForEditor(loadedProfileFields, detailResult.extraProfileValues || {}),
        });
      } catch {
        drawer.reset();
      }
    },
    [drawer, editorForm, ensureDepartmentOptionsLoaded, ensureProfileFieldsLoaded, ensureRoleOptionsLoaded],
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
      const payload = buildUserEditorPayload(values, {
        editing: !isCreating,
        profileFields,
      });

      if (drawer.editingId) {
        await request<UserDetail>(`/v1/system/users/${drawer.editingId}`, {
          method: 'PUT',
          data: payload,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('ui.system.users.useuser.userUpdated'));
      } else {
        await request<UserDetail>('/v1/system/users', {
          method: 'POST',
          data: payload,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('ui.system.users.useuser.userCreated'));
      }

      notifyCurrentUserSync();
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
  }, [drawer, editorForm, loadDepartments, profileFields, reloadTable, selectedDepartmentId, setSelectedDepartmentId]);

  const handleStatusToggle = useCallback(
    async (record: UserRecord) => {
      if (record.status !== 'ENABLED') {
        await request<boolean>(`/v1/system/users/${record.id}/status`, {
          method: 'PATCH',
          data: { status: 'ENABLED' },
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('ui.system.users.useuser.statusUpdated'));
        reloadTable();
        return;
      }

      confirmAction({
        title: t('ui.system.users.useuser.disableUser'),
        content: t('ui.system.users.useuser.disableUserThisAccountWillNoLongerBe', { username: record.username }),
        okText: t('ui.system.users.useuser.disable.c857adf6'),
        okButtonProps: { danger: true },
        onOk: async () => {
          await request<boolean>(`/v1/system/users/${record.id}/status`, {
            method: 'PATCH',
            data: { status: 'DISABLED' },
            ...API_OPTS.NO_REDIRECT,
          });
          message.success(t('ui.system.users.useuser.statusUpdated'));
          reloadTable();
        },
      });
    },
    [reloadTable],
  );

  const handleDelete = useCallback(
    (record: UserRecord) => {
      confirmAction({
        title: t('ui.system.users.useuser.deleteUser'),
        content: t('ui.system.users.useuser.deleteUserThisActionCannotBeUndone', { username: record.username }),
        okText: t('ui.system.users.useuser.delete.0ad952f3'),
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
      setSelectedExportFields([]);
      setExportModalOpen(true);
    } finally {
      setExportLoading(false);
    }
  }, [exportFields]);

  const confirmExport = useCallback(async () => {
    if (!selectedExportFields.length) {
      message.warning(t('ui.system.users.useuser.pleaseSelectAtLeastOneExportField'));
      return;
    }
    setExportLoading(true);
    try {
      const result = await request<UserExportStart>('/v1/system/users/export', {
        method: 'POST',
        data: {
          ...exportableQueryParams(lastUserQueryParamsRef.current),
          fields: selectedExportFields,
        },
        timeoutMs: 120000,
        ...API_OPTS.NO_REDIRECT,
      });
      if (result.mode === 'SYNC' && result.contentBase64) {
        downloadBase64File(
          result.contentBase64,
          result.contentType || 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          result.fileName || t('ui.system.users.useuser.userManagementExportXlsx'),
        );
        message.success(t('ui.system.users.useuser.exportFileGenerated'));
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
        message.success(t('ui.system.users.useuser.largeExportTaskCreated'));
      }
    } finally {
      setExportLoading(false);
    }
  }, [selectedExportFields]);

  const downloadExportTaskFile = useCallback(() => {
    if (exportTask?.downloadUrl) {
      window.open(exportTask.downloadUrl, '_blank', 'noopener,noreferrer');
    }
  }, [exportTask?.downloadUrl]);

  const openDownloadCenter = useCallback(() => {
    window.location.assign('/data-management/download-center');
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
        const queryParams = normalizeUserQueryParams(params, selectedDepartmentId);
        lastUserQueryParamsRef.current = queryParams;
        return request<PagedResult<UserRecord>>('/v1/system/users', {
          method: 'GET',
          params: { ...queryParams, _t: Date.now() },
          ...API_OPTS.NO_REDIRECT,
        });
      }),
    [selectedDepartmentId],
  );

  return {
    actionRef,
    responsive,
    searchConfig: userSearchConfig,
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
    profileFields,
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
