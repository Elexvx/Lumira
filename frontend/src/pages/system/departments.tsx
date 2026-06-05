import { Button, Form, Input, InputNumber, Popconfirm, Select, Space, Tag, Tooltip, message } from 'antd';
import { ProDescriptions, type ProColumns } from '@ant-design/pro-components';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { useCrudDrawerState } from '@/features/crud/useCrudDrawerState';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { request } from '@/services/common/request';
import { API_OPTS } from '@/utils/errorMessage';
import type { DepartmentRecord } from '@/types/api';

const STATUS_OPTIONS = [
  { label: '启用', value: 'ENABLED' },
  { label: '停用', value: 'DISABLED' },
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
}: {
  actionPermission: { can: (permission: string) => boolean };
  openDetail: (record: DepartmentRecord) => void;
  openCreateChild: (record: DepartmentRecord) => void;
  openEdit: (record: DepartmentRecord) => void | Promise<void>;
  deleteDepartment: (record: DepartmentRecord) => Promise<void>;
}): ProColumns<DepartmentRecord>[] => [
  {
    title: '部门名称',
    dataIndex: 'deptName',
    width: 'var(--saas-spacing-260)',
  },
  {
    title: '部门编码',
    dataIndex: 'deptCode',
    width: 'var(--saas-spacing-180)',
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 'var(--saas-spacing-100)',
    render: (status) => <Tag color={status === 'ENABLED' ? 'green' : 'default'}>{status === 'ENABLED' ? '启用' : '停用'}</Tag>,
  },
  {
    title: '用户数',
    dataIndex: 'userCount',
    width: 'var(--saas-spacing-100)',
  },
  {
    title: '排序',
    dataIndex: 'sortNo',
    width: 'var(--saas-spacing-90)',
  },
  {
    title: '操作',
    width: 'var(--saas-spacing-260)',
    fixed: 'right',
    render: (_, record) => {
      const userCount = record.userCount ?? 0;
      const hasChildren = !!record.children?.length;
      const cannotDelete = userCount > 0 || hasChildren;
      const deleteDisabledReason = hasChildren ? '该部门存在下级部门，不能删除' : userCount > 0 ? `该部门下仍有 ${userCount} 名用户，不能删除` : null;

      return (
        <Space size={resolveResponsiveValue(APP_SPACING.tagWrapGap, responsive.isMobile)} wrap>
          <Button type="link" size="small" onClick={() => void openDetail(record)}>
            详情
          </Button>
          {actionPermission.can('system:department:create') ? (
            <Button type="link" size="small" onClick={() => openCreateChild(record)}>
              新增下级
            </Button>
          ) : null}
          {actionPermission.can('system:department:update') ? (
            <Button type="link" size="small" onClick={() => void openEdit(record)}>
              编辑
            </Button>
          ) : null}
          {actionPermission.can('system:department:delete') ? (
            cannotDelete ? (
              <Tooltip title={deleteDisabledReason}>
                <Button type="link" size="small" danger disabled>
                  删除
                </Button>
              </Tooltip>
            ) : (
              <Popconfirm title="删除部门" description={`确认删除「${record.deptName}」吗？`} onConfirm={() => void deleteDepartment(record)}>
                <Button type="link" size="small" danger>
                  删除
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
  const formProps = {
    form,
    initialValues: { sortNo: 0, status: 'ENABLED' },
  } as const;
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
        message.success('部门已更新');
      } else {
        await request<DepartmentRecord>('/v1/system/departments', {
          method: 'POST',
          data: payload,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success('部门已创建');
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
      message.success('部门已删除');
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
      }),
    [actionPermission, deleteDepartment, openCreateChild, openEdit, openDetail],
  );
  const toolbarActions = useMemo(
    () =>
      buildToolbarButtons([
        {
          key: 'create',
          permission: 'system:department:create',
          type: 'primary',
          label: '新增部门',
          onClick: openCreate,
        },
        {
          key: 'refresh',
          label: '刷新',
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
    <ManagementPage title="组织部门">
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

      <ManagementDrawer
        title={drawer.editingId ? '编辑部门' : '新增部门'}
        open={drawer.open}
        onClose={drawer.close}
        footerActions={[
          { key: 'cancel', label: '取消', onClick: drawer.close },
          { key: 'save', label: '保存', type: 'primary', loading: saving, disabled: !canSaveDepartment, onClick: () => void saveDepartment() },
        ]}
      >
        <Form {...formProps}>
          <Form.Item name="parentId" label="上级部门">
            <Select allowClear options={departmentOptions.filter((item) => item.value !== drawer.editingId)} placeholder="请选择上级部门" />
          </Form.Item>
          <Form.Item name="deptCode" label="部门编码" rules={[{ required: true, message: '请输入部门编码' }]}>
            <Input placeholder="例如 product" />
          </Form.Item>
          <Form.Item name="deptName" label="部门名称" rules={[{ required: true, message: '请输入部门名称' }]}>
            <Input placeholder="例如 产品部" />
          </Form.Item>
          <Form.Item name="sortNo" label="排序">
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true, message: '请选择状态' }]}>
            <Select options={STATUS_OPTIONS} />
          </Form.Item>
        </Form>
      </ManagementDrawer>

      <ManagementDrawer title={selectedDepartment ? `部门详情 · ${selectedDepartment.deptName}` : '部门详情'} open={detail.open} onClose={closeDetail}>
        {selectedDepartment ? (
          <ProDescriptions<DepartmentRecord>
            {...detailProps}
            columns={[
              { title: '部门名称', dataIndex: 'deptName' },
              { title: '部门编码', dataIndex: 'deptCode' },
              { title: '状态', dataIndex: 'status' },
              { title: '用户数', dataIndex: 'userCount' },
              { title: '排序', dataIndex: 'sortNo' },
              { title: '创建时间', dataIndex: 'createdAt', valueType: 'dateTime' },
              { title: '更新时间', dataIndex: 'updatedAt', valueType: 'dateTime' },
            ]}
          />
        ) : null}
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default DepartmentManagementPage;
