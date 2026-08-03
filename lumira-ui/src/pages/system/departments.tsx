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

import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

const STATUS_OPTIONS = [
  { label: t('ui.system.departments.enabled'), value: 'ENABLED' },
  { label: t('ui.system.departments.disabled'), value: 'DISABLED' },
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
    title: t('ui.system.departments.departmentName'),
    dataIndex: 'deptName',
    width: 'var(--saas-spacing-260)',
  },
  {
    title: t('ui.system.departments.departmentCode'),
    dataIndex: 'deptCode',
    width: 'var(--saas-spacing-180)',
  },
  {
    title: t('ui.system.departments.status'),
    dataIndex: 'status',
    width: 'var(--saas-spacing-100)',
    render: (status) => <Tag color={status === 'ENABLED' ? 'green' : 'default'}>{status === 'ENABLED' ? t('ui.system.departments.enabled') : t('ui.system.departments.disabled')}</Tag>,
  },
  {
    title: t('ui.system.departments.userCount'),
    dataIndex: 'userCount',
    width: 'var(--saas-spacing-100)',
  },
  {
    title: t('ui.system.departments.sortOrder'),
    dataIndex: 'sortNo',
    width: 'var(--saas-spacing-90)',
  },
  {
    title: t('ui.system.departments.actions'),
    width: 'var(--saas-spacing-260)',
    fixed: 'right',
    render: (_, record) => {
      const userCount = record.userCount ?? 0;
      const hasChildren = !!record.children?.length;
      const cannotDelete = userCount > 0 || hasChildren;
      const deleteDisabledReason = hasChildren ? t('ui.system.departments.thisDepartmentHasChildDepartmentsAndCannotBe') : userCount > 0 ? t('ui.system.departments.thisDepartmentStillHasUsersAndCannotBe').replace('{count}', String(userCount)) : null;

      return (
        <Space size={resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile)} wrap>
          <Button type="link" size="small" onClick={() => void openDetail(record)}>
            {t('ui.system.departments.details')}
          </Button>
          {actionPermission.can('system:department:create') ? (
            <Button type="link" size="small" onClick={() => openCreateChild(record)}>
              {t('ui.system.departments.addChild')}
            </Button>
          ) : null}
          {actionPermission.can('system:department:update') ? (
            <Button type="link" size="small" onClick={() => void openEdit(record)}>
              {t('ui.system.departments.edit')}
            </Button>
          ) : null}
          {actionPermission.can('system:department:delete') ? (
            cannotDelete ? (
              <Tooltip title={deleteDisabledReason}>
                <Button type="link" size="small" danger disabled>
                  {t('ui.system.departments.delete')}
                </Button>
              </Tooltip>
            ) : (
              <Popconfirm title={t('ui.system.departments.deleteDepartment')} description={t('ui.system.departments.delete.d9ac9dcc', { deptName: record.deptName })} onConfirm={() => void deleteDepartment(record)}>
                <Button type="link" size="small" danger>
                  {t('ui.system.departments.delete')}
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
        message.success(t('ui.system.departments.departmentUpdated'));
      } else {
        await request<DepartmentRecord>('/v1/system/departments', {
          method: 'POST',
          data: payload,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('ui.system.departments.departmentCreated'));
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
      message.success(t('ui.system.departments.departmentDeleted'));
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
          label: t('ui.system.departments.addDepartment'),
          onClick: openCreate,
        },
        {
          key: 'refresh',
          label: t('ui.system.departments.refresh'),
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
    <ManagementPage title={t('ui.system.departments.organizationDepartments')}>
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
        title={drawer.editingId ? t('ui.system.departments.editDepartment') : t('ui.system.departments.addDepartment')}
        open={drawer.open}
        onClose={drawer.close}
        footerActions={[
          { key: 'cancel', label: t('ui.system.departments.cancel'), onClick: drawer.close },
          { key: 'save', label: t('ui.system.departments.save'), type: 'primary', loading: saving, disabled: !canSaveDepartment, onClick: () => void saveDepartment() },
        ]}
      >
        <Form {...formProps}>
          <Form.Item name="parentId" label={t('ui.system.departments.parentDepartment')}>
            <Select allowClear options={departmentOptions.filter((item) => item.value !== drawer.editingId)} placeholder={t('ui.system.departments.selectAParentDepartment')} />
          </Form.Item>
          <Form.Item name="deptCode" label={t('ui.system.departments.departmentCode')} rules={[{ required: true, message: t('ui.system.departments.pleaseEnterTheDepartmentCode') }]}>
            <Input placeholder={t('ui.system.departments.eGProduct')} />
          </Form.Item>
          <Form.Item name="deptName" label={t('ui.system.departments.departmentName')} rules={[{ required: true, message: t('ui.system.departments.pleaseEnterTheDepartmentName') }]}>
            <Input placeholder={t('ui.system.departments.eGProductDepartment')} />
          </Form.Item>
          <Form.Item name="sortNo" label={t('ui.system.departments.sortOrder')}>
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label={t('ui.system.departments.status')} rules={[{ required: true, message: t('ui.system.departments.pleaseSelectAStatus') }]}>
            <Select options={STATUS_OPTIONS} />
          </Form.Item>
        </Form>
      </ManagementDrawer>

      <ManagementDrawer title={selectedDepartment ? `${t('ui.system.departments.departmentDetails')} · ${selectedDepartment.deptName}` : t('ui.system.departments.departmentDetails')} open={detail.open} onClose={closeDetail}>
        {selectedDepartment ? (
          <ProDescriptions<DepartmentRecord>
            {...detailProps}
            columns={[
              { title: t('ui.system.departments.departmentName'), dataIndex: 'deptName' },
              { title: t('ui.system.departments.departmentCode'), dataIndex: 'deptCode' },
              { title: t('ui.system.departments.status'), dataIndex: 'status' },
              { title: t('ui.system.departments.userCount'), dataIndex: 'userCount' },
              { title: t('ui.system.departments.sortOrder'), dataIndex: 'sortNo' },
              { title: t('ui.system.departments.createdAt'), dataIndex: 'createdAt', valueType: 'dateTime' },
              { title: t('ui.system.departments.updatedAt'), dataIndex: 'updatedAt', valueType: 'dateTime' },
            ]}
          />
        ) : null}
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default DepartmentManagementPage;
