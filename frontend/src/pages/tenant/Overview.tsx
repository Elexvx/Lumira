import { useMemo, useRef, useState } from 'react';
import { useRequest } from '@umijs/max';
import {
  PageContainer,
  ProTable,
  type ActionType,
  type ProColumns,
} from '@ant-design/pro-components';
import { Button, Card, Col, Descriptions, Drawer, Empty, Form, Input, Modal, Row, Select, Space, Spin, Tag, Timeline, Typography, message } from 'antd';
import { DetailForm } from '@/components/DetailForm';
import { PageDetailDescriptions } from '@/components/PageDetailDescriptions';
import { auditService } from '@/services/audit';
import { pluginService } from '@/services/plugin';
import { tenantService, type TenantMutationPayload } from '@/services/tenant';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { usePermission } from '@/hooks/usePermission';
import type { AuditLogRecord, CurrentTenantResponse, MyTenant, PagedResult, TenantPlugin, TenantSummary } from '@/types/api';
import { buildResponsivePagination, buildResponsiveScroll, normalizeResponsiveColumns, ResponsiveActions, ResponsiveText, useResponsiveTable } from '@/components/ResponsiveTable';

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
  const responsive = useResponsiveTable();
  const { isMobile } = responsive;
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
        pageSize: 5,
      },
      { autoRedirectOnUnauthorized: false },
    ),
  }) as { data: PagedResult<AuditLogRecord> });

  const currentTenant = currentTenantQuery.data?.currentTenant || initialState?.currentTenant || null;
  const myTenants = (myTenantsQuery.data || initialState?.myTenants || []) as MyTenant[];
  const tenantPlugins = (pluginQuery.data || initialState?.availablePlugins || []) as TenantPlugin[];
  const tenantById = useMemo(
    () => new Map(myTenants.map((tenant) => [tenant.tenantId, tenant] as const)),
    [myTenants],
  );
  const switchHistoryItems = useMemo(() => {
    return (switchHistoryQuery.data?.records || []).slice(0, 5).map((record) => {
      const result = record.logResult || record.loginResult || 'UNKNOWN';
      const isSuccess = result === 'SUCCESS';
      const tenant = record.tenantId ? tenantById.get(record.tenantId) : undefined;
      const tenantLabel = tenant
        ? `${tenant.tenantName}${tenant.tenantCode ? `（${tenant.tenantCode}）` : ''}`
        : record.tenantId
          ? `租户 #${record.tenantId}`
          : '未知租户';

      return {
        key: record.id,
        color: isSuccess ? 'green' : 'red',
        children: (
          <Space direction="vertical" size={2} style={{ width: '100%' }}>
            <Space size={8} wrap>
              <Typography.Text strong>{record.username || '未知用户'}</Typography.Text>
              <Tag color={isSuccess ? 'green' : 'red'}>{isSuccess ? '成功' : '失败'}</Tag>
            </Space>
            <Typography.Text>{`切换至 ${tenantLabel}`}</Typography.Text>
            <Typography.Text type="secondary">
              {record.failReason || record.detailMessage || '租户切换操作'} · {formatDateTime(record.createdAt)}
            </Typography.Text>
          </Space>
        ),
      };
    });
  }, [myTenants, switchHistoryQuery.data?.records, tenantById]);

  const tenantColumns = useMemo<ProColumns<TenantSummary>[]>(
    () => [
      {
        title: '租户编码',
        dataIndex: 'tenantCode',
        search: true,
        importance: 1,
      },
      {
        title: '租户名称',
        dataIndex: 'tenantName',
        search: true,
        importance: 1,
      },
      {
        title: '简称',
        dataIndex: 'tenantShortName',
        search: false,
        importance: 2,
        responsiveLevel: ['tablet', 'desktop'],
      },
      {
        title: '状态',
        dataIndex: 'status',
        importance: 1,
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
        importance: 2,
        responsiveLevel: ['tablet', 'desktop'],
        render: (_, record) => formatDateTime(record.createdAt),
      },
      {
        title: '更新时间',
        dataIndex: 'updatedAt',
        hideInSearch: true,
        importance: 2,
        responsiveLevel: ['tablet', 'desktop'],
        render: (_, record) => formatDateTime(record.updatedAt),
      },
      {
        title: '操作',
        valueType: 'option',
        importance: 0,
        desktopFixed: 'right',
        width: 200,
        render: (_, record) => (
          <ResponsiveActions
            level={responsive.level}
            items={[
              {
                key: 'detail',
                label: '详情',
                hidden: !canAccess('tenant:view'),
                onClick: () => void openTenantDetail(record),
              },
              {
                key: 'edit',
                label: '编辑',
                hidden: !canAccess('tenant:update'),
                onClick: () => void openTenantEditor(record),
              },
              {
                key: 'delete',
                label: '删除',
                hidden: !canAccess('tenant:delete'),
                danger: true,
                onClick: () => void confirmDeleteTenant(record),
              },
            ]}
          />
        ),
      },
    ],
    [canAccess, responsive.level],
  );

  const pluginColumns = useMemo<ProColumns<TenantPlugin>[]>(
    () => [
      { title: '插件编码', dataIndex: 'pluginCode', importance: 1 },
      { title: '插件名称', dataIndex: 'pluginName', importance: 1 },
      { title: '版本', dataIndex: 'version', importance: 1 },
      {
        title: '共享依赖',
        dataIndex: 'sharedDeps',
        importance: 3,
        responsiveLevel: 'desktop',
        ellipsisText: true,
        render: (_, record) => <ResponsiveText value={record.sharedDeps?.length ? record.sharedDeps.join(', ') : '-'} copyable={Boolean(record.sharedDeps?.length)} />,
      },
      {
        title: '菜单数',
        dataIndex: 'menus',
        importance: 2,
        responsiveLevel: ['tablet', 'desktop'],
        render: (_, record) => record.menus?.length ?? 0,
      },
    ],
    [],
  );

  const myTenantColumns = useMemo<ProColumns<MyTenant>[]>(
    () => [
      { title: '租户编码', dataIndex: 'tenantCode', importance: 1 },
      { title: '租户名称', dataIndex: 'tenantName', importance: 1 },
      { title: '简称', dataIndex: 'tenantShortName', importance: 2, responsiveLevel: ['tablet', 'desktop'] },
      {
        title: '默认',
        dataIndex: 'isDefault',
        importance: 1,
        render: (_, record) => <Tag color={record.isDefault ? 'green' : 'default'}>{record.isDefault ? '是' : '否'}</Tag>,
      },
      {
        title: '状态',
        dataIndex: 'status',
        importance: 1,
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
                <PageDetailDescriptions column={isMobile ? 1 : 2}>
                  <Descriptions.Item label="租户编码">{currentTenant.tenantCode}</Descriptions.Item>
                  <Descriptions.Item label="租户名称">{currentTenant.tenantName}</Descriptions.Item>
                  <Descriptions.Item label="租户简称">{currentTenant.tenantShortName || '-'}</Descriptions.Item>
                  <Descriptions.Item label="状态">
                    <Tag color={currentTenant.status === 'ENABLED' ? 'green' : 'default'}>{currentTenant.status}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="创建时间">{formatDateTime(currentTenant.createdAt)}</Descriptions.Item>
                  <Descriptions.Item label="更新时间">{formatDateTime(currentTenant.updatedAt)}</Descriptions.Item>
                </PageDetailDescriptions>
              ) : (
                <Empty description="当前尚未选择租户" />
              )}
            </Card>
          </Col>
        </Row>

        <Card title="租户管理">
          <div className="saas-table-wrap">
            <ProTable<TenantSummary>
              actionRef={actionRef}
              rowKey="tenantId"
              columns={normalizeResponsiveColumns(tenantColumns, responsive.level)}
              search={{ labelWidth: 'auto', span: responsive.isMobile ? 24 : 8 }}
              options={false}
              pagination={buildResponsivePagination({ showSizeChanger: true }, responsive)}
              scroll={buildResponsiveScroll(normalizeResponsiveColumns(tenantColumns, responsive.level), responsive)}
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
                  <Button key="create" type="primary" size={responsive.isMobile ? 'small' : 'middle'} onClick={openCreateTenant}>
                    新增租户
                  </Button>
                ) : null,
                <Button key="refresh" size={responsive.isMobile ? 'small' : 'middle'} onClick={() => actionRef.current?.reload()}>
                  刷新
                </Button>,
              ]}
            />
          </div>
        </Card>

        <Card title="我可访问的租户" bodyStyle={{ height: 320, minHeight: 0 }}>
          <div className="saas-table-wrap">
            <ProTable<MyTenant>
              rowKey="tenantId"
              columns={normalizeResponsiveColumns(myTenantColumns, responsive.level)}
              dataSource={myTenants}
              loading={myTenantsQuery.loading}
              search={false}
              options={false}
              toolBarRender={false}
              pagination={false}
              scroll={buildResponsiveScroll(normalizeResponsiveColumns(myTenantColumns, responsive.level), responsive)}
            />
          </div>
        </Card>

        <Card title="当前租户启用插件" bodyStyle={{ height: 360, minHeight: 0 }}>
          <div className="saas-table-wrap">
            <ProTable<TenantPlugin>
              rowKey="pluginCode"
              columns={normalizeResponsiveColumns(pluginColumns, responsive.level)}
              dataSource={tenantPlugins}
              loading={pluginQuery.loading}
              search={false}
              options={false}
              toolBarRender={false}
              pagination={false}
              scroll={buildResponsiveScroll(normalizeResponsiveColumns(pluginColumns, responsive.level), responsive)}
            />
          </div>
        </Card>

        <Card title="最近 5 次切换" loading={switchHistoryQuery.loading}>
          {switchHistoryItems.length ? (
            <Timeline items={switchHistoryItems} />
          ) : (
            <Empty description="暂无最近的租户切换记录" />
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
        <DetailForm form={editorForm} initialValues={{ status: 'ENABLED' }}>
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
        </DetailForm>
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
          <PageDetailDescriptions column={isMobile ? 1 : 2}>
            <Descriptions.Item label="租户编码">{selectedTenant.tenantCode}</Descriptions.Item>
            <Descriptions.Item label="租户名称">{selectedTenant.tenantName}</Descriptions.Item>
            <Descriptions.Item label="租户简称">{selectedTenant.tenantShortName || '-'}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={selectedTenant.status === 'ENABLED' ? 'green' : 'default'}>{selectedTenant.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="创建时间">{formatDateTime(selectedTenant.createdAt)}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{formatDateTime(selectedTenant.updatedAt)}</Descriptions.Item>
          </PageDetailDescriptions>
        ) : (
          <Empty description="暂无租户详情" />
        )}
      </Drawer>
    </PageContainer>
  );
};
