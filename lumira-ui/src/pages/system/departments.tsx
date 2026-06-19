import { Button, Form, Input, InputNumber, Popconfirm, Select, Space, Tag, Tooltip } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import { ProDescriptions, type ProColumns } from '@ant-design/pro-components';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { useStandardFormProps } from '@/features/form/config';
import { useCrudDrawerState } from '@/features/crud/useCrudDrawerState';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { request } from '@/services/common/request';
import { API_OPTS } from '@/utils/errorMessage';
import type { DepartmentRecord } from '@/types/api';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const STATUS_OPTIONS = [
  { label: t('启用', 'Enabled'), value: 'ENABLED' },
  { label: t('停用', 'Disabled'), value: 'DISABLED' },
];

const flattenDepartments = (departments: DepartmentRecord[], depth = 0): { label: string; value: number }[] =>
  departments.flatMap((department) => [
    { label: `${'　'.repeat(depth)}${department.deptName}`, value: department.id },
    ...flattenDepartments(department.children || [], depth + 1),
  ]);

const buildDepartmentColumns = ({
  actionPermission,
  openDetail,
  openCreateChild,
  openEdit,
  deleteDepartment,
  isMobile,
}: {
  actionPermission: { can: (permission: string) => boolean };
  openDetail: (record: DepartmentRecord) => void;
  openCreateChild: (record: DepartmentRecord) => void;
  openEdit: (record: DepartmentRecord) => void | Promise<void>;
  deleteDepartment: (record: DepartmentRecord) => Promise<void>;
  isMobile: boolean;
}): ProColumns<DepartmentRecord>[] => [
  {
    title: t('部门名称', 'Department name'),
    dataIndex: 'deptName',
    width: 'var(--saas-spacing-260)',
  },
  {
    title: t('部门编码', 'Department code'),
    dataIndex: 'deptCode',
    width: 'var(--saas-spacing-180)',
  },
  {
    title: t('状态', 'Status'),
    dataIndex: 'status',
    width: 'var(--saas-spacing-100)',
    render: (status) => <Tag color={status === 'ENABLED' ? 'green' : 'default'}>{status === 'ENABLED' ? t('启用', 'Enabled') : t('停用', 'Disabled')}</Tag>,
  },
  {
    title: t('用户数', 'User count'),
    dataIndex: 'userCount',
    width: 'var(--saas-spacing-100)',
  },
  {
    title: t('排序', 'Sort order'),
    dataIndex: 'sortNo',
    width: 'var(--saas-spacing-90)',
  },
  {
    title: t('操作', 'Actions'),
    width: 'var(--saas-spacing-260)',
    fixed: 'right',
    render: (_, record) => {
      const userCount = record.userCount ?? 0;
      const hasChildren = !!record.children?.length;
      const cannotDelete = userCount > 0 || hasChildren;
      const deleteDisabledReason = hasChildren ? t('该部门存在下级部门，不能删除', 'This department has child departments and cannot be deleted') : userCount > 0 ? t('该部门下仍有 {count} 名用户，不能删除', 'This department still has {count} users and cannot be deleted').replace('{count}', String(userCount)) : null;

      return (
        <Space size={resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile)} wrap>
          <Button type="link" size="small" onClick={() => void openDetail(record)}>
            {t('详情', 'Details')}
          </Button>
          {actionPermission.can('system:department:create') ? (
            <Button type="link" size="small" onClick={() => openCreateChild(record)}>
              {t('新增下级', 'Add child')}
            </Button>
          ) : null}
          {actionPermission.can('system:department:update') ? (
            <Button type="link" size="small" onClick={() => void openEdit(record)}>
              {t('编辑', 'Edit')}
            </Button>
          ) : null}
          {actionPermission.can('system:department:delete') ? (
            cannotDelete ? (
              <Tooltip title={deleteDisabledReason}>
                <Button type="link" size="small" danger disabled>
                  {t('删除', 'Delete')}
                </Button>
              </Tooltip>
            ) : (
              <Popconfirm title={t('删除部门', 'Delete department')} description={t(`确认删除「${record.deptName}」吗？`, `Delete "${record.deptName}"?`)} onConfirm={() => void deleteDepartment(record)}>
                <Button type="link" size="small" danger>
                  {t('删除', 'Delete')}
                </Button>
              </Popconfirm>
            )
          ) : null}
        </Space>
      );
    },
  },
];

const useDepartmentManagement = () => {
  const [departments, setDepartments] = useState<DepartmentRecord[]>([]);
  const [selectedDepartment, setSelectedDepartment] = useState<DepartmentRecord | null>(null);
  const [loading, setLoading] = useState(false);
  const { actionPermission, responsive, buildToolbarButtons } = usePagePermissionActions();
  const drawer = useCrudDrawerState<DepartmentRecord>();
  const detail = useCrudDrawerState<DepartmentRecord>();
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);

  const detailProps = useDetailProDescriptionsProps<DepartmentRecord>({
    column: 1,
    dataSource: selectedDepartment || undefined,
  });
  const departmentOptions = useMemo(() => flattenDepartments(departments), [departments]);

  const loadDepartments = useCallback(async () => {
    setLoading(true);
    try {
      setDepartments(
        await request<DepartmentRecord[]>('/v1/system/departments', {
          method: 'GET',
          ...API_OPTS.NO_REDIRECT,
        }),
      );
    } finally {
      setLoading(false);
    }
  }, []);

  const loadDepartmentDetail = useCallback(
    async (id: number) =>
      request<DepartmentRecord>(`/v1/system/departments/${id}`, {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      }),
    [],
  );

  useEffect(() => {
    void loadDepartments();
  }, [loadDepartments]);

  const openSelectedDepartment = useCallback(async (record: DepartmentRecord) => {
    const detailResult = await request<DepartmentRecord>(`/v1/system/departments/${record.id}`, {
      method: 'GET',
      ...API_OPTS.NO_REDIRECT,
    });
    setSelectedDepartment(detailResult);
  }, []);

  const closeSelectedDepartment = useCallback(() => {
    setSelectedDepartment(null);
  }, []);

  const openCreate = useCallback(() => {
    drawer.openCreate();
    form.resetFields();
    form.setFieldsValue({ sortNo: 0, status: 'ENABLED' });
  }, [drawer, form]);

  const openCreateChild = useCallback(
    (record: DepartmentRecord) => {
      drawer.openCreate();
      form.resetFields();
      form.setFieldsValue({ parentId: record.id, sortNo: 0, status: 'ENABLED' });
    },
    [drawer, form],
  );

  const openEdit = useCallback(
    async (record: DepartmentRecord) => {
      drawer.openEdit(record, record.id);
      const detailResult = await loadDepartmentDetail(record.id);
      form.setFieldsValue(detailResult);
    },
    [drawer, form, loadDepartmentDetail],
  );

  const openDetail = useCallback(
    async (record: DepartmentRecord) => {
      detail.openEdit(record, record.id);
      await openSelectedDepartment(record);
    },
    [detail, openSelectedDepartment],
  );

  const closeDetail = useCallback(() => {
    detail.close();
    closeSelectedDepartment();
  }, [closeSelectedDepartment, detail]);

  const canSaveDepartment = actionPermission.can(drawer.editingId ? 'system:department:update' : 'system:department:create');
  const formProps = useStandardFormProps({
    form,
    initialValues: { sortNo: 0, status: 'ENABLED' },
  });
  const saveDepartment = useCallback(async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      const payload = {
        ...values,
        parentId: values.parentId || null,
        sortNo: values.sortNo ?? 0,
      };
      if (drawer.editingId) {
        await request<DepartmentRecord>(`/v1/system/departments/${drawer.editingId}`, {
          method: 'PUT',
          data: payload,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('部门已更新', 'Department updated'));
      } else {
        await request<DepartmentRecord>('/v1/system/departments', {
          method: 'POST',
          data: payload,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('部门已创建', 'Department created'));
      }
      drawer.close();
      await loadDepartments();
    } finally {
      setSaving(false);
    }
  }, [drawer, form, loadDepartments]);
  const deleteDepartment = useCallback(
    async (record: DepartmentRecord) => {
      await request<boolean>(`/v1/system/departments/${record.id}`, {
        method: 'DELETE',
        ...API_OPTS.NO_REDIRECT,
      });
      message.success(t('部门已删除', 'Department deleted'));
      await loadDepartments();
    },
    [loadDepartments],
  );
  const columns = useMemo(
    () =>
      buildDepartmentColumns({
        actionPermission,
        openDetail,
        openCreateChild,
        openEdit,
        deleteDepartment,
        isMobile: responsive.isMobile,
      }),
    [actionPermission, deleteDepartment, openCreateChild, openEdit, openDetail, responsive.isMobile],
  );
  const toolbarActions = useMemo(
    () =>
      buildToolbarButtons([
        {
          key: 'create',
          permission: 'system:department:create',
          type: 'primary',
          label: t('新增部门', 'Add department'),
          onClick: openCreate,
        },
        {
          key: 'refresh',
          label: t('刷新', 'Refresh'),
          onClick: async () => {
            await loadDepartments();
          },
        },
      ]),
    [buildToolbarButtons, loadDepartments, openCreate],
  );

  return {
    actionPermission,
    responsive,
    buildToolbarButtons,
    toolbarActions,
    drawer,
    detail,
    form,
    formProps,
    detailProps,
    departments,
    loading,
    saving,
    selectedDepartment,
    canSaveDepartment,
    departmentOptions,
    columns,
    openCreate,
    openCreateChild,
    openEdit,
    openDetail,
    closeDetail,
    saveDepartment,
    deleteDepartment,
    loadDepartments,
  };
};

const DepartmentManagementPage = () => {
  const {
    responsive,
    toolbarActions,
    drawer,
    detail,
    detailProps,
    departments,
    loading,
    saving,
    selectedDepartment,
    canSaveDepartment,
    departmentOptions,
    columns,
    closeDetail,
    loadDepartments,
    saveDepartment,
    formProps,
  } = useDepartmentManagement();

  return (
    <ManagementPage title={t('组织部门', 'Organization departments')}>
      <ManagementPageBody>
        <ManagementTable<DepartmentRecord>
          rowKey="id"
          columns={columns}
          dataSource={departments}
          loading={loading}
          pagination={false}
          isMobile={responsive.isMobile}
          search={false}
          scroll={{ x: 990 }}
          onRefresh={() => void loadDepartments()}
          toolBarRender={() => toolbarActions}
        />
      </ManagementPageBody>

      <ManagementDrawer
        title={drawer.editingId ? t('编辑部门', 'Edit department') : t('新增部门', 'Add department')}
        open={drawer.open}
        onClose={drawer.close}
        footerActions={[
          { key: 'cancel', label: t('取消', 'Cancel'), onClick: drawer.close },
          { key: 'save', label: t('保存', 'Save'), type: 'primary', loading: saving, disabled: !canSaveDepartment, onClick: () => void saveDepartment() },
        ]}
      >
        <Form {...formProps}>
          <Form.Item name="parentId" label={t('上级部门', 'Parent department')}>
            <Select allowClear options={departmentOptions.filter((item) => item.value !== drawer.editingId)} placeholder={t('请选择上级部门', 'Select a parent department')} />
          </Form.Item>
          <Form.Item name="deptCode" label={t('部门编码', 'Department code')} rules={[{ required: true, message: t('请输入部门编码', 'Please enter the department code') }]}>
            <Input placeholder={t('例如 product', 'e.g. product')} />
          </Form.Item>
          <Form.Item name="deptName" label={t('部门名称', 'Department name')} rules={[{ required: true, message: t('请输入部门名称', 'Please enter the department name') }]}>
            <Input placeholder={t('例如 产品部', 'e.g. Product Department')} />
          </Form.Item>
          <Form.Item name="sortNo" label={t('排序', 'Sort order')}>
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label={t('状态', 'Status')} rules={[{ required: true, message: t('请选择状态', 'Please select a status') }]}>
            <Select options={STATUS_OPTIONS} />
          </Form.Item>
        </Form>
      </ManagementDrawer>

      <ManagementDrawer title={selectedDepartment ? `${t('部门详情', 'Department details')} · ${selectedDepartment.deptName}` : t('部门详情', 'Department details')} open={detail.open} onClose={closeDetail}>
        {selectedDepartment ? (
          <ProDescriptions<DepartmentRecord>
            {...detailProps}
            columns={[
              { title: t('部门名称', 'Department name'), dataIndex: 'deptName' },
              { title: t('部门编码', 'Department code'), dataIndex: 'deptCode' },
              { title: t('状态', 'Status'), dataIndex: 'status' },
              { title: t('用户数', 'User count'), dataIndex: 'userCount' },
              { title: t('排序', 'Sort order'), dataIndex: 'sortNo' },
              { title: t('创建时间', 'Created at'), dataIndex: 'createdAt', valueType: 'dateTime' },
              { title: t('更新时间', 'Updated at'), dataIndex: 'updatedAt', valueType: 'dateTime' },
            ]}
          />
        ) : null}
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default DepartmentManagementPage;
