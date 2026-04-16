import { useRequest } from '@umijs/max';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Button, Card, Col, Descriptions, Drawer, Empty, Form, Modal, Row, Space, Spin, Tag, Timeline, message } from 'antd';
import { useMemo, useState } from 'react';
import { useCrudPageState } from '@/features/crud/useCrudPageState';
import { useDetailDescriptionsProps } from '@/features/detail/config';
import { useStandardFormProps } from '@/features/form/config';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { buildMobilePagination, buildTableRequest, buildTableScroll } from '@/features/table/proTable';
import { useResponsive } from '@/hooks/useResponsive';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { buildTenantColumns, myTenantColumns, pluginColumns } from '@/pages/tenant/overview/columns';
import { TenantEditorForm } from '@/pages/tenant/overview/components/TenantEditorForm';
import { buildSwitchHistoryItems, formatDateTime } from '@/pages/tenant/overview/utils';
import { auditService } from '@/services/audit';
import { pluginService } from '@/services/plugin';
import { tenantService, type TenantMutationPayload } from '@/services/tenant';
import type { AuditLogRecord, CurrentTenantResponse, MyTenant, PagedResult, TenantPlugin, TenantSummary } from '@/types/api';

const TenantOverviewPage = () => {
  const { initialState } = useInitialStateModel();
  const tenantCrud = useCrudPageState<TenantSummary>();
  const [editorForm] = Form.useForm<TenantMutationPayload>();
  const responsive = useResponsive();
  const { isMobile } = responsive;
  const actionPermission = useActionPermission();
  const [saving, setSaving] = useState(false);
  const detailDescriptionsProps = useDetailDescriptionsProps({ column: isMobile ? 1 : 2 });
  const editorFormProps = useStandardFormProps({
    form: editorForm,
    initialValues: { status: 'ENABLED' },
  });

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
  const switchHistoryItems = useMemo(
    () => buildSwitchHistoryItems(switchHistoryQuery.data?.records || [], tenantById),
    [switchHistoryQuery.data?.records, tenantById],
  );

  const openCreateTenant = () => {
    tenantCrud.drawer.openCreate();
    editorForm.resetFields();
    editorForm.setFieldsValue({
      tenantCode: '',
      tenantName: '',
      tenantShortName: '',
      status: 'ENABLED',
    });
  };

  const openTenantEditor = async (record: TenantSummary) => {
    tenantCrud.drawer.openEdit(record, record.tenantId);
    try {
      const detail = await tenantService.detail(record.tenantId, { autoRedirectOnUnauthorized: false });
      editorForm.setFieldsValue({
        tenantCode: detail.tenantCode,
        tenantName: detail.tenantName,
        tenantShortName: detail.tenantShortName || '',
        status: detail.status,
      });
    } catch {
      tenantCrud.drawer.reset();
    }
  };

  const openTenantDetail = async (record: TenantSummary) => {
    tenantCrud.detail.openDetail(record);
    tenantCrud.detail.setLoading(true);
    try {
      const detail = await tenantService.detail(record.tenantId, { autoRedirectOnUnauthorized: false });
      tenantCrud.detail.setCurrentRecord(detail);
    } catch {
      tenantCrud.detail.setOpen(false);
      tenantCrud.detail.setCurrentRecord(null);
    } finally {
      tenantCrud.detail.setLoading(false);
    }
  };

  const closeTenantEditor = () => {
    if (!editorForm.isFieldsTouched(true)) {
      tenantCrud.drawer.close();
      return;
    }

    Modal.confirm({
      title: '提示',
      content: '关闭后未保存的修改将丢失，是否继续？',
      okText: '继续编辑',
      cancelText: '确认关闭',
      centered: true,
      onOk: () => Promise.resolve(),
      onCancel: tenantCrud.drawer.close,
    });
  };

  const saveTenant = async () => {
    setSaving(true);
    try {
      const values = await editorForm.validateFields();
      const payload: TenantMutationPayload = {
        tenantCode: values.tenantCode.trim(),
        tenantName: values.tenantName.trim(),
        tenantShortName: values.tenantShortName?.trim() || undefined,
        status: values.status,
      };

      if (tenantCrud.drawer.editingId) {
        await tenantService.update(tenantCrud.drawer.editingId, payload, { autoRedirectOnUnauthorized: false });
        message.success('租户已更新');
      } else {
        await tenantService.create(payload, { autoRedirectOnUnauthorized: false });
        message.success('租户已创建');
      }

      tenantCrud.drawer.close();
      tenantCrud.reloadTable();
    } finally {
      setSaving(false);
    }
  };

  const confirmDeleteTenant = (record: TenantSummary) => {
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
        tenantCrud.reloadTable();
      },
    });
  };

  const tenantColumns = useMemo(
    () =>
      buildTenantColumns({
        isDesktop: responsive.isDesktop,
        isMobile: responsive.isMobile,
        buildRowActions: actionPermission.buildTableActions,
        onOpenDetail: (record) => void openTenantDetail(record),
        onOpenEdit: (record) => void openTenantEditor(record),
        onDelete: (record) => void confirmDeleteTenant(record),
      }),
    [actionPermission.buildTableActions, responsive.isDesktop, responsive.isMobile],
  );

  return (
    <PageContainer className="saas-management-page" ghost title="租户中心" style={{ height: '100%', minHeight: 0 }} content={null}>
      <div className="saas-management-page-body">
        <Row gutter={[16, 16]}>
          <Col xs={24}>
            <Card title="当前租户" loading={currentTenantQuery.loading}>
              {currentTenant ? (
                <Descriptions {...detailDescriptionsProps}>
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
          <div className="saas-table-wrap">
            <ProTable<TenantSummary>
              actionRef={tenantCrud.actionRef}
              rowKey="tenantId"
              columns={tenantColumns}
              search={{ labelWidth: 'auto', span: responsive.isMobile ? 24 : 8 }}
              options={false}
              pagination={buildMobilePagination({ showSizeChanger: true }, responsive.isMobile)}
              scroll={buildTableScroll(tenantColumns, responsive.isMobile)}
              request={buildTableRequest((params) => tenantService.list(params, { autoRedirectOnUnauthorized: false }))}
              toolBarRender={() =>
                actionPermission.buildToolbarActions([
                  {
                    permission: 'tenant:create',
                    value: (
                      <Button key="create" type="primary" size={responsive.isMobile ? 'small' : 'middle'} onClick={openCreateTenant}>
                        新增租户
                      </Button>
                    ),
                  },
                  {
                    value: (
                      <Button key="refresh" size={responsive.isMobile ? 'small' : 'middle'} onClick={tenantCrud.reloadTable}>
                        刷新
                      </Button>
                    ),
                  },
                ])
              }
            />
          </div>
        </Card>

        <Card title="我可访问的租户" bodyStyle={{ height: 320, minHeight: 0 }}>
          <div className="saas-table-wrap">
            <ProTable<MyTenant>
              rowKey="tenantId"
              columns={myTenantColumns}
              dataSource={myTenants}
              loading={myTenantsQuery.loading}
              search={false}
              options={false}
              toolBarRender={false}
              pagination={false}
              scroll={buildTableScroll(myTenantColumns, responsive.isMobile)}
            />
          </div>
        </Card>

        <Card title="当前租户启用插件" bodyStyle={{ height: 360, minHeight: 0 }}>
          <div className="saas-table-wrap">
            <ProTable<TenantPlugin>
              rowKey="pluginCode"
              columns={pluginColumns}
              dataSource={tenantPlugins}
              loading={pluginQuery.loading}
              search={false}
              options={false}
              toolBarRender={false}
              pagination={false}
              scroll={buildTableScroll(pluginColumns, responsive.isMobile)}
            />
          </div>
        </Card>

        <Card title="最近 5 次切换" loading={switchHistoryQuery.loading}>
          {switchHistoryItems.length ? <Timeline items={switchHistoryItems} /> : <Empty description="暂无最近的租户切换记录" />}
        </Card>
      </div>

      <Drawer
        title={tenantCrud.drawer.editingId ? '编辑租户' : '新增租户'}
        open={tenantCrud.drawer.open}
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
        <TenantEditorForm formProps={editorFormProps} />
      </Drawer>

      <Drawer
        title={tenantCrud.detail.currentRecord ? `租户详情 · ${tenantCrud.detail.currentRecord.tenantName}` : '租户详情'}
        open={tenantCrud.detail.open}
        onClose={tenantCrud.detail.close}
        width={720}
        destroyOnClose
      >
        {tenantCrud.detail.loading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
            <Spin />
          </div>
        ) : tenantCrud.detail.currentRecord ? (
          <Descriptions {...detailDescriptionsProps}>
            <Descriptions.Item label="租户编码">{tenantCrud.detail.currentRecord.tenantCode}</Descriptions.Item>
            <Descriptions.Item label="租户名称">{tenantCrud.detail.currentRecord.tenantName}</Descriptions.Item>
            <Descriptions.Item label="租户简称">{tenantCrud.detail.currentRecord.tenantShortName || '-'}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={tenantCrud.detail.currentRecord.status === 'ENABLED' ? 'green' : 'default'}>
                {tenantCrud.detail.currentRecord.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="创建时间">{formatDateTime(tenantCrud.detail.currentRecord.createdAt)}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{formatDateTime(tenantCrud.detail.currentRecord.updatedAt)}</Descriptions.Item>
          </Descriptions>
        ) : (
          <Empty description="暂无租户详情" />
        )}
      </Drawer>
    </PageContainer>
  );
};

export default TenantOverviewPage;
