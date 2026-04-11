import { useMemo, useRef, useState } from 'react';
import { useRequest } from '@umijs/max';
import {
  PageContainer,
  ProTable,
  type ActionType,
  type ProColumns,
} from '@ant-design/pro-components';
import { Button, Card, Col, Descriptions, Drawer, Empty, Form, Input, Modal, Row, Select, Space, Spin, Tag, Typography, message } from 'antd';
import { auditService } from '@/services/audit';
import { pluginService } from '@/services/plugin';
import { tenantService, type TenantMutationPayload } from '@/services/tenant';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { usePermission } from '@/hooks/usePermission';
import { useResponsive } from '@/hooks/useResponsive';
import type { AuditLogRecord, CurrentTenantResponse, MyTenant, PagedResult, TenantPlugin, TenantSummary } from '@/types/api';

const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('zh-CN', { hour12: false });
};

export default () => {
  const actionRef = useRef<ActionType>();
  const [editorForm] = Form.useForm<TenantMutationPayload>();
  const { initialState } = useInitialStateModel();
  const { isMobile } = useResponsive();
  const { canAccess } = usePermission();
  const [editorOpen, setEditorOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [selectedTenant, setSelectedTenant] = useState<TenantSummary | null>(null);

  const currentTenantQuery = useRequest(async () => ({ data: await tenantService.currentTenant({ autoRedirectOnUnauthorized: false }) }) as {
    data: CurrentTenantResponse;
  });
  const myTenantsQuery = useRequest(async () => ({ data: await tenantService.myTenants({ autoRedirectOnUnauthorized: false }) }) as {
    data: MyTenant[];
  });
  const pluginQuery = useRequest(
    async () => ({ data: await pluginService.currentAvailable({ autoRedirectOnUnauthorized: false }) }) as {
      data: TenantPlugin[];
    },
    {
      refreshDeps: [initialState?.currentTenant?.tenantId],
    },
  );
  const switchHistoryQuery = useRequest(async () => ({
    data: await auditService.loginLogs(
      {
        loginType: 'TENANT_SWITCH',
        pageNo: 1,
        pageSize: 20,
      },
      { autoRedirectOnUnauthorized: false },
    ),
  }) as { data: PagedResult<AuditLogRecord> });

  const currentTenant = currentTenantQuery.data?.currentTenant || initialState?.currentTenant || null;
  const myTenants = (myTenantsQuery.data || initialState?.myTenants || []) as MyTenant[];
  const tenantPlugins = (pluginQuery.data || initialState?.availablePlugins || []) as TenantPlugin[];

  const tenantColumns = useMemo<ProColumns<TenantSummary>[]>(
    () => [
      {
        title: '租户编码',
        dataIndex: 'tenantCode',
        search: true,
      },
      {
        title: '租户名称',
        dataIndex: 'tenantName',
        search: true,
      },
      {
        title: '简称',
        dataIndex: 'tenantShortName',
        search: false,
      },
      {
        title: '状态',
        dataIndex: 'status',
        valueEnum: {
          ENABLED: { text: '启用', status: 'Success' },
          DISABLED: { text: '停用', status: 'Default' },
        },
        search: {
          transform: (value) => ({ status: value }),
        },
        render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{record.status}</Tag>,
      },
      {
        title: '创建时间',
        dataIndex: 'createdAt',
        hideInSearch: true,
        render: (_, record) => formatDateTime(record.createdAt),
      },
      {
        title: '更新时间',
        dataIndex: 'updatedAt',
        hideInSearch: true,
        render: (_, record) => formatDateTime(record.updatedAt),
      },
      {
        title: '操作',
        valueType: 'option',
        fixed: 'right',
        width: 200,
        render: (_, record) => (
          <Space size={0}>
            {canAccess('tenant:view') ? (
              <Button type="link" size="small" onClick={() => void openTenantDetail(record)}>
                详情
              </Button>
            ) : null}
            {canAccess('tenant:update') ? (
              <Button type="link" size="small" onClick={() => void openTenantEditor(record)}>
                编辑
              </Button>
            ) : null}
            {canAccess('tenant:delete') ? (
              <Button type="link" size="small" danger onClick={() => void confirmDeleteTenant(record)}>
                删除
              </Button>
            ) : null}
          </Space>
        ),
      },
    ],
    [canAccess],
  );

  const pluginColumns = useMemo<ProColumns<TenantPlugin>[]>(
    () => [
      { title: '插件编码', dataIndex: 'pluginCode' },
      { title: '插件名称', dataIndex: 'pluginName' },
      { title: '版本', dataIndex: 'version' },
      {
        title: '共享依赖',
        dataIndex: 'sharedDeps',
        render: (_, record) => (record.sharedDeps?.length ? record.sharedDeps.join(', ') : '-'),
      },
      {
        title: '菜单数',
        dataIndex: 'menus',
        render: (_, record) => record.menus?.length ?? 0,
      },
    ],
    [],
  );

  const myTenantColumns = useMemo<ProColumns<MyTenant>[]>(
    () => [
      { title: '租户编码', dataIndex: 'tenantCode' },
      { title: '租户名称', dataIndex: 'tenantName' },
      { title: '简称', dataIndex: 'tenantShortName' },
      {
        title: '默认',
        dataIndex: 'isDefault',
        render: (_, record) => <Tag color={record.isDefault ? 'green' : 'default'}>{record.isDefault ? '是' : '否'}</Tag>,
      },
      {
        title: '状态',
        dataIndex: 'status',
        render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{record.status}</Tag>,
      },
    ],
    [],
  );

  function openCreateTenant() {
    setSelectedTenant(null);
    setEditingId(null);
    editorForm.resetFields();
    editorForm.setFieldsValue({
      tenantCode: '',
      tenantName: '',
      tenantShortName: '',
      status: 'ENABLED',
    });
    setEditorOpen(true);
  }

  async function openTenantEditor(record: TenantSummary) {
    setSelectedTenant(record);
    setEditingId(record.tenantId);
    setEditorOpen(true);
    try {
      const detail = await tenantService.detail(record.tenantId, { autoRedirectOnUnauthorized: false });
      editorForm.setFieldsValue({
        tenantCode: detail.tenantCode,
        tenantName: detail.tenantName,
        tenantShortName: detail.tenantShortName || '',
        status: detail.status,
      });
    } catch {
      setEditorOpen(false);
      setEditingId(null);
      setSelectedTenant(null);
    }
  }

  async function openTenantDetail(record: TenantSummary) {
    setSelectedTenant(record);
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const detail = await tenantService.detail(record.tenantId, { autoRedirectOnUnauthorized: false });
      setSelectedTenant(detail);
    } catch {
      setDetailOpen(false);
      setSelectedTenant(null);
    } finally {
      setDetailLoading(false);
    }
  }

  function closeTenantEditor() {
    if (!editorForm.isFieldsTouched(true)) {
      setEditorOpen(false);
      return;
    }

    Modal.confirm({
      title: '提示',
      content: '关闭后未保存的修改将丢失，是否继续？',
      okText: '继续编辑',
      cancelText: '确认关闭',
      centered: true,
      onOk: () => Promise.resolve(),
      onCancel: () => {
        setEditorOpen(false);
      },
    });
  }

  async function saveTenant() {
    setSaving(true);
    try {
      const values = await editorForm.validateFields();
      const payload: TenantMutationPayload = {
        tenantCode: values.tenantCode.trim(),
        tenantName: values.tenantName.trim(),
        tenantShortName: values.tenantShortName?.trim() || undefined,
        status: values.status,
      };

      if (editingId) {
        await tenantService.update(editingId, payload, { autoRedirectOnUnauthorized: false });
        message.success('租户已更新');
      } else {
        await tenantService.create(payload, { autoRedirectOnUnauthorized: false });
        message.success('租户已创建');
      }

      setEditorOpen(false);
      setSelectedTenant(null);
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  }

  function confirmDeleteTenant(record: TenantSummary) {
    Modal.confirm({
      title: '删除租户',
      content: `确认删除租户「${record.tenantName}」吗？删除后仅做软删除，不会同步清理该租户下的业务数据。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      centered: true,
      onOk: async () => {
        await tenantService.delete(record.tenantId, { autoRedirectOnUnauthorized: false });
        message.success('租户已删除');
        actionRef.current?.reload();
      },
    });
  }

  return (
    <PageContainer className="saas-management-page" ghost title="租户中心" style={{ height: '100%', minHeight: 0 }} content={null}>
      <div className="saas-management-page-body">
        <Row gutter={[16, 16]}>
          <Col xs={24}>
            <Card title="当前租户" loading={currentTenantQuery.loading}>
              {currentTenant ? (
                <Descriptions column={isMobile ? 1 : 2} size="small" bordered>
                  <Descriptions.Item label="租户编码">{currentTenant.tenantCode}</Descriptions.Item>
                  <Descriptions.Item label="租户名称">{currentTenant.tenantName}</Descriptions.Item>
                  <Descriptions.Item label="租户简称">{currentTenant.tenantShortName || '-'}</Descriptions.Item>
                  <Descriptions.Item label="状态">
                    <Tag color={currentTenant.status === 'ENABLED' ? 'green' : 'default'}>{currentTenant.status}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="创建时间">{formatDateTime(currentTenant.createdAt)}</Descriptions.Item>
                  <Descriptions.Item label="更新时间">{formatDateTime(currentTenant.updatedAt)}</Descriptions.Item>
                </Descriptions>
              ) : (
                <Empty description="当前尚未选择租户" />
              )}
            </Card>
          </Col>
        </Row>

        <Card title="租户管理">
          <ProTable<TenantSummary>
            actionRef={actionRef}
            rowKey="tenantId"
            columns={tenantColumns}
            search={{ labelWidth: 'auto' }}
            options={false}
            pagination={{ showSizeChanger: true }}
            request={async (params) => {
              const { current, pageSize, ...rest } = params;
              const result = await tenantService.list(
                {
                  pageNo: current,
                  pageSize,
                  ...rest,
                },
                { autoRedirectOnUnauthorized: false },
              );
              return {
                data: result.records,
                success: true,
                total: result.total,
              };
            }}
            toolBarRender={() => [
              canAccess('tenant:create') ? (
                <Button key="create" type="primary" onClick={openCreateTenant}>
                  新增租户
                </Button>
              ) : null,
              <Button key="refresh" onClick={() => actionRef.current?.reload()}>
                刷新
              </Button>,
            ]}
          />
        </Card>

        <Card title="我可访问的租户" bodyStyle={{ height: 320, minHeight: 0 }}>
          <ProTable<MyTenant>
            rowKey="tenantId"
            columns={myTenantColumns}
            dataSource={myTenants}
            loading={myTenantsQuery.loading}
            search={false}
            options={false}
            toolBarRender={false}
            pagination={false}
          />
        </Card>

        <Card title="当前租户启用插件" bodyStyle={{ height: 360, minHeight: 0 }}>
          <ProTable<TenantPlugin>
            rowKey="pluginCode"
            columns={pluginColumns}
            dataSource={tenantPlugins}
            loading={pluginQuery.loading}
            search={false}
            options={false}
            toolBarRender={false}
            pagination={false}
          />
        </Card>

        <Card title="最近切换记录" loading={switchHistoryQuery.loading}>
          {switchHistoryQuery.data?.records?.length ? (
            <Space direction="vertical" style={{ width: '100%' }}>
              {switchHistoryQuery.data.records.map((record) => (
                <Card key={record.id} size="small">
                  <Space direction="vertical" size={0}>
                    <Typography.Text strong>{record.username}</Typography.Text>
                    <Typography.Text type="secondary">
                      {record.failReason || record.detailMessage || '租户切换操作'}
                    </Typography.Text>
                  </Space>
                </Card>
              ))}
            </Space>
          ) : (
            <Empty description="暂无租户切换记录" />
          )}
        </Card>
      </div>

      <Drawer
        title={editingId ? '编辑租户' : '新增租户'}
        open={editorOpen}
        onClose={closeTenantEditor}
        width={720}
        destroyOnClose
        footer={
          <div className="saas-drawer-footer">
            <Space>
              <Button onClick={closeTenantEditor}>取消</Button>
              <Button type="primary" loading={saving} onClick={() => void saveTenant()}>
                保存
              </Button>
            </Space>
          </div>
        }
      >
        <Form form={editorForm} layout="vertical" initialValues={{ status: 'ENABLED' }}>
          <Form.Item name="tenantCode" label="租户编码" rules={[{ required: true, message: '请输入租户编码' }]}>
            <Input maxLength={64} placeholder="例如：acme" />
          </Form.Item>
          <Form.Item name="tenantName" label="租户名称" rules={[{ required: true, message: '请输入租户名称' }]}>
            <Input maxLength={128} placeholder="租户显示名称" />
          </Form.Item>
          <Form.Item name="tenantShortName" label="租户简称">
            <Input maxLength={64} placeholder="可选，用于顶部切换器展示" />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true, message: '请选择状态' }]}>
            <Select
              options={[
                { label: '启用', value: 'ENABLED' },
                { label: '停用', value: 'DISABLED' },
              ]}
            />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={selectedTenant ? `租户详情 · ${selectedTenant.tenantName}` : '租户详情'}
        open={detailOpen}
        onClose={() => {
          setDetailOpen(false);
          setSelectedTenant(null);
        }}
        width={720}
        destroyOnClose
      >
        {detailLoading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
            <Spin />
          </div>
        ) : selectedTenant ? (
          <Descriptions column={isMobile ? 1 : 2} bordered size="small">
            <Descriptions.Item label="租户编码">{selectedTenant.tenantCode}</Descriptions.Item>
            <Descriptions.Item label="租户名称">{selectedTenant.tenantName}</Descriptions.Item>
            <Descriptions.Item label="租户简称">{selectedTenant.tenantShortName || '-'}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={selectedTenant.status === 'ENABLED' ? 'green' : 'default'}>{selectedTenant.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="创建时间">{formatDateTime(selectedTenant.createdAt)}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{formatDateTime(selectedTenant.updatedAt)}</Descriptions.Item>
          </Descriptions>
        ) : (
          <Empty description="暂无租户详情" />
        )}
      </Drawer>
    </PageContainer>
  );
};
