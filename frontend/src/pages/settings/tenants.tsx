import { PlusOutlined } from '@ant-design/icons';
import { Button, Form, Input, Popconfirm, Select, Space, Tag, message } from 'antd';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { useResponsive } from '@/hooks/useResponsive';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementTable } from '@/features/management/ManagementTable';
import { request } from '@/services/common/request';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import type { TenantRecord } from '@/types/api';
import { useCallback, useEffect, useRef, useState } from 'react';

type TenantMutationPayload = {
  tenantCode: string;
  tenantName: string;
  status: string;
  remark?: string | null;
};

const PAGE_SIZE = 10;

const DEFAULT_TENANT_VALUES: TenantMutationPayload = {
  tenantCode: '',
  tenantName: '',
  status: 'ENABLED',
  remark: '',
};

const statusOptions = [
  { value: 'ENABLED', label: '启用' },
  { value: 'DISABLED', label: '停用' },
];

const TenantManagementPage = () => {
  const [records, setRecords] = useState<TenantRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<Partial<TenantRecord> | null>(null);
  const [form] = Form.useForm<TenantMutationPayload>();
  const pageNoRef = useRef(1);
  const actionPermission = useActionPermission();
  const responsive = useResponsive();

  const canCreate = actionPermission.can('system:tenant:create');
  const canUpdate = actionPermission.can('system:tenant:update');
  const canDelete = actionPermission.can('system:tenant:delete');

  useEffect(() => {
    pageNoRef.current = pageNo;
  }, [pageNo]);

  const load = useCallback(async (nextPage = pageNoRef.current) => {
    setLoading(true);
    try {
      const result = await request<{ records: TenantRecord[]; total: number }>('/v1/system/tenants', {
        method: 'GET',
        params: { pageNo: nextPage, pageSize: PAGE_SIZE },
        ...API_OPTS.NO_REDIRECT,
      });
      setRecords(result.records || []);
      setTotal(result.total);
      setPageNo(nextPage);
    } catch (error) {
      showErrorMessage(error, '加载租户列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(1);
  }, [load]);

  const close = useCallback(() => {
    setEditing(null);
    form.resetFields();
  }, [form]);

  const open = useCallback((record?: TenantRecord) => {
    const next = record ? { ...record } : DEFAULT_TENANT_VALUES;
    setEditing(next);
    form.setFieldsValue({
      tenantCode: next.tenantCode || '',
      tenantName: next.tenantName || '',
      status: next.status || 'ENABLED',
      remark: next.remark || '',
    });
  }, [form]);
  const [saving, setSaving] = useState(false);

  const save = useCallback(async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing?.id) {
        await request<TenantRecord>(`/v1/system/tenants/${editing.id}`, {
          method: 'PUT',
          data: values as TenantMutationPayload,
          ...API_OPTS.NO_REDIRECT,
        });
      } else {
        await request<TenantRecord>('/v1/system/tenants', {
          method: 'POST',
          data: values as TenantMutationPayload,
          ...API_OPTS.NO_REDIRECT,
        });
      }
      message.success('租户已保存');
      close();
      await load();
    } catch (error) {
      showErrorMessage(error, '保存租户失败');
    } finally {
      setSaving(false);
    }
  }, [close, editing?.id, form, load]);

  const deleteTenant = useCallback(
    async (record: TenantRecord) => {
      if (record.id === 1001) {
        return;
      }

      try {
        await request<boolean>(`/v1/system/tenants/${record.id}`, {
          method: 'DELETE',
          ...API_OPTS.NO_REDIRECT,
        });
        message.success('租户已删除');
        await load();
      } catch (error) {
        showErrorMessage(error, '删除租户失败');
      }
    },
    [load],
  );

  return (
    <ManagementPage
      title="租户管理"
      extra={
        <Button type="primary" icon={<PlusOutlined />} disabled={!canCreate} onClick={() => open()}>
          新增租户
        </Button>
      }
    >
      <ManagementTable<TenantRecord>
        rowKey="id"
        loading={loading}
        dataSource={records}
        pagination={{ current: pageNo, total, pageSize: 10, onChange: (nextPage) => void load(nextPage) }}
        isMobile={responsive.isMobile}
        search={false}
        onRefresh={() => load()}
        columns={[
          { title: '租户编码', dataIndex: 'tenantCode', width: 'var(--saas-spacing-180)' },
          { title: '租户名称', dataIndex: 'tenantName', ellipsis: true },
          {
            title: '状态',
            dataIndex: 'status',
            width: 'var(--saas-spacing-120)',
            render: (value) => <Tag color={value === 'ENABLED' ? 'green' : 'default'}>{value === 'ENABLED' ? '启用' : '停用'}</Tag>,
          },
          { title: '备注', dataIndex: 'remark', ellipsis: true, render: (value) => value || '-' },
          {
            title: '操作',
            width: 'var(--saas-spacing-160)',
            render: (_, record) => (
            <Space>
                <Button type="link" disabled={!canUpdate} onClick={() => open(record)}>
                  编辑
                </Button>
                <Popconfirm
                  title="删除租户"
                  description={`确认删除「${record.tenantName}」吗？`}
                  onConfirm={() => void deleteTenant(record)}
                >
                <Button type="link" danger disabled={!canDelete || record.id === 1001}>
                  删除
                </Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />

      <ManagementDrawer
        title={editing?.id ? '编辑租户' : '新增租户'}
        open={Boolean(editing)}
        onClose={close}
        footerActions={[
          { key: 'cancel', label: '取消', onClick: close },
          {
            key: 'save',
            label: '保存',
            type: 'primary',
            loading: saving,
            disabled: saving || (editing?.id ? !canUpdate : !canCreate),
            onClick: () => void save(),
          },
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

export default TenantManagementPage;
