import { Button, Form, Input, Popconfirm, Select, Space, Tag, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { ManagementDrawer, ManagementPage, ManagementTable } from '@/features/management';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { useResponsive } from '@/hooks/useResponsive';
import { systemService, type TenantMutationPayload } from '@/services/system';
import type { TenantRecord } from '@/types/api';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';


const statusOptions = [
  { value: 'ENABLED', label: '启用' },
  { value: 'DISABLED', label: '停用' },
];

const TenantManagement = () => {
  const [records, setRecords] = useState<TenantRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<Partial<TenantRecord> | null>(null);
  const [form] = Form.useForm<TenantMutationPayload>();
  const actionPermission = useActionPermission();
  const responsive = useResponsive();
  const canCreate = actionPermission.can('system:tenant:create');
  const canUpdate = actionPermission.can('system:tenant:update');
  const canDelete = actionPermission.can('system:tenant:delete');

  const load = async (nextPage = pageNo) => {
    setLoading(true);
    try {
      const result = await systemService.tenants({ pageNo: nextPage, pageSize: 10 }, API_OPTS.NO_REDIRECT);
      setRecords(result.records || []);
      setTotal(result.total);
      setPageNo(nextPage);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load(1);
  }, []);

  const open = (record?: TenantRecord) => {
    const next = record || { tenantCode: '', tenantName: '', status: 'ENABLED', remark: '' };
    setEditing(next);
    form.setFieldsValue({
      tenantCode: next.tenantCode || '',
      tenantName: next.tenantName || '',
      status: next.status || 'ENABLED',
      remark: next.remark || '',
    });
  };

  const save = async () => {
    const values = await form.validateFields();
    if (editing?.id) {
      await systemService.updateTenant(editing.id, values, API_OPTS.NO_REDIRECT);
    } else {
      await systemService.createTenant(values, API_OPTS.NO_REDIRECT);
    }
    message.success('租户已保存');
    setEditing(null);
    await load();
  };

  return (
    <ManagementPage
      title="租户管理"
      extra={<Button type="primary" icon={<PlusOutlined />} disabled={!canCreate} onClick={() => open()}>新增租户</Button>}
    >
      <ManagementTable<TenantRecord>
        rowKey="id"
        loading={loading}
        dataSource={records}
        pagination={{ current: pageNo, total, pageSize: 10, onChange: load }}
        isMobile={responsive.isMobile}
        search={false}
        onRefresh={() => load()}
        columns={[
          { title: '租户编码', dataIndex: 'tenantCode', width: 180 },
          { title: '租户名称', dataIndex: 'tenantName', ellipsis: true },
          {
            title: '状态',
            dataIndex: 'status',
            width: 120,
            render: (value) => <Tag color={value === 'ENABLED' ? 'green' : 'default'}>{value === 'ENABLED' ? '启用' : '停用'}</Tag>,
          },
          { title: '备注', dataIndex: 'remark', ellipsis: true, render: (value) => value || '-' },
          {
            title: '操作',
            width: 160,
            render: (_, record) => (
              <Space>
                <Button type="link" disabled={!canUpdate} onClick={() => open(record)}>编辑</Button>
                <Popconfirm
                  title="删除租户"
                  description={`确认删除「${record.tenantName}」吗？`}
                  onConfirm={async () => {
                    await systemService.deleteTenant(record.id, API_OPTS.NO_REDIRECT);
                    message.success('租户已删除');
                    await load();
                  }}
                >
                  <Button type="link" danger disabled={!canDelete || record.id === 1001}>删除</Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />
      <ManagementDrawer
        title={editing?.id ? '编辑租户' : '新增租户'}
        open={Boolean(editing)}
        onClose={() => setEditing(null)}
        footerActions={[
          { key: 'cancel', label: '取消', onClick: () => setEditing(null) },
          { key: 'save', label: '保存', type: 'primary', disabled: editing?.id ? !canUpdate : !canCreate, onClick: save },
        ]}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="tenantCode" label="租户编码" rules={[{ required: true, message: '请输入租户编码' }]}>
            <Input disabled={editing?.id === 1001} placeholder="tenant-code" />
          </Form.Item>
          <Form.Item name="tenantName" label="租户名称" rules={[{ required: true, message: '请输入租户名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true, message: '请选择状态' }]}>
            <Select options={statusOptions} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={4} />
          </Form.Item>
        </Form>
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default TenantManagement;
