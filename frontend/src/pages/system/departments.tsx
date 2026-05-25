import { ProDescriptions, type ProColumns } from '@ant-design/pro-components';
import { Button, Form, Input, InputNumber, Popconfirm, Select, Space, Tag, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useCrudDrawerState } from '@/features/crud/useCrudDrawerState';
import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { useStandardFormProps } from '@/features/form/config';
import { ManagementDrawer, ManagementPage, ManagementTable } from '@/features/management';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { iamService } from '@/services/iam';
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

const DepartmentManagementPage = () => {
  const drawer = useCrudDrawerState<DepartmentRecord>();
  const detail = useCrudDrawerState<DepartmentRecord>();
  const [form] = Form.useForm();
  const { actionPermission, responsive, buildToolbarButtons } = usePagePermissionActions();
  const [departments, setDepartments] = useState<DepartmentRecord[]>([]);
  const [selectedDepartment, setSelectedDepartment] = useState<DepartmentRecord | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const canSaveDepartment = actionPermission.can(drawer.editingId ? 'system:department:update' : 'system:department:create');
  const formProps = useStandardFormProps({
    form,
    initialValues: { sortNo: 0, status: 'ENABLED' },
  });
  const detailProps = useDetailProDescriptionsProps<DepartmentRecord>({
    column: responsive.isMobile ? 1 : 2,
    dataSource: selectedDepartment || undefined,
  });

  const loadDepartments = async () => {
    setLoading(true);
    try {
      setDepartments(await iamService.departments({ autoRedirectOnUnauthorized: false }));
    } finally {
      setLoading(false);
    }
  };

  const departmentOptions = useMemo(() => flattenDepartments(departments), [departments]);

  useEffect(() => {
    void loadDepartments();
  }, []);

  const openCreate = () => {
    drawer.openCreate();
    form.resetFields();
    form.setFieldsValue({ sortNo: 0, status: 'ENABLED' });
  };

  const openCreateChild = (record: DepartmentRecord) => {
    drawer.openCreate();
    form.resetFields();
    form.setFieldsValue({ parentId: record.id, sortNo: 0, status: 'ENABLED' });
  };

  const openEdit = async (record: DepartmentRecord) => {
    drawer.openEdit(record, record.id);
    const detailResult = await iamService.departmentDetail(record.id, { autoRedirectOnUnauthorized: false });
    form.setFieldsValue(detailResult);
  };

  const openDetail = async (record: DepartmentRecord) => {
    detail.openEdit(record, record.id);
    const detailResult = await iamService.departmentDetail(record.id, { autoRedirectOnUnauthorized: false });
    setSelectedDepartment(detailResult);
  };

  const saveDepartment = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      const payload = {
        ...values,
        parentId: values.parentId || null,
        sortNo: values.sortNo ?? 0,
      };
      if (drawer.editingId) {
        await iamService.updateDepartment(drawer.editingId, payload, { autoRedirectOnUnauthorized: false });
        message.success('部门已更新');
      } else {
        await iamService.createDepartment(payload, { autoRedirectOnUnauthorized: false });
        message.success('部门已创建');
      }
      drawer.close();
      await loadDepartments();
    } finally {
      setSaving(false);
    }
  };

  const deleteDepartment = async (record: DepartmentRecord) => {
    await iamService.deleteDepartment(record.id, { autoRedirectOnUnauthorized: false });
    message.success('部门已删除');
    await loadDepartments();
  };

  const columns = useMemo<ProColumns<DepartmentRecord>[]>(
    () => [
      {
        title: '部门名称',
        dataIndex: 'deptName',
        width: 260,
      },
      {
        title: '部门编码',
        dataIndex: 'deptCode',
        width: 180,
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (status) => <Tag color={status === 'ENABLED' ? 'green' : 'default'}>{status === 'ENABLED' ? '启用' : '停用'}</Tag>,
      },
      {
        title: '用户数',
        dataIndex: 'userCount',
        width: 100,
      },
      {
        title: '排序',
        dataIndex: 'sortNo',
        width: 90,
      },
      {
        title: '操作',
        width: 260,
        render: (_, record) => (
          <Space size={8} wrap>
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
              <Popconfirm title="删除部门" description={`确认删除「${record.deptName}」吗？`} onConfirm={() => void deleteDepartment(record)}>
                <Button type="link" size="small" danger>
                  删除
                </Button>
              </Popconfirm>
            ) : null}
          </Space>
        ),
      },
    ],
    [actionPermission],
  );

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
        scroll={{ x: 900 }}
        onRefresh={() => loadDepartments()}
        toolBarRender={() =>
          buildToolbarButtons([
            {
              key: 'create',
              permission: 'system:department:create',
              type: 'primary',
              label: '新增部门',
              onClick: openCreate,
            },
          ])
        }
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

      <ManagementDrawer
        title={selectedDepartment ? `部门详情 · ${selectedDepartment.deptName}` : '部门详情'}
        open={detail.open}
        onClose={() => {
          detail.close();
          setSelectedDepartment(null);
        }}
      >
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
