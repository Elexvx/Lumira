import { useMemo } from 'react';
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components';
import { Card, Col, Descriptions, Empty, Row, Space, Tag, Typography } from 'antd';
import { useRequest } from 'umi';
import { pluginService } from '@/services/plugin';
import { tenantService } from '@/services/tenant';
import { auditService } from '@/services/audit';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import type { AuditLogRecord, CurrentTenantResponse, MyTenant, PagedResult, TenantPlugin } from '@/types/api';
import { useResponsive } from '@/hooks/useResponsive';

export default () => {
  const { initialState } = useInitialStateModel();
  const { isMobile } = useResponsive();
  const currentTenantQuery = useRequest(async () => ({ data: await tenantService.currentTenant({ autoRedirectOnUnauthorized: false }) }) as {
    data: CurrentTenantResponse;
  });
  const myTenantsQuery = useRequest(async () => ({ data: await tenantService.myTenants({ autoRedirectOnUnauthorized: false }) }) as {
    data: MyTenant[];
  });
  const pluginQuery = useRequest(async () => ({ data: await pluginService.currentAvailable({ autoRedirectOnUnauthorized: false }) }) as {
    data: TenantPlugin[];
  }, {
    refreshDeps: [initialState?.currentTenant?.tenantId],
  });
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

  const tenantColumns = useMemo<ProColumns<MyTenant>[]>(
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

  return (
    <PageContainer
      className="saas-management-page"
      ghost
      breadcrumbRender={false}
      title="租户中心"
      subTitle="查看当前租户、可访问租户、切换说明和当前租户下已启用插件。"
      style={{ height: '100%', minHeight: 0 }}
      content={null}
    >
      <div className="saas-management-page-body">
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={10}>
            <Card title="当前租户" loading={currentTenantQuery.loading}>
              {currentTenant ? (
                <Descriptions column={isMobile ? 1 : 2} size="small" bordered>
                  <Descriptions.Item label="租户编码">{currentTenant.tenantCode}</Descriptions.Item>
                  <Descriptions.Item label="租户名称">{currentTenant.tenantName}</Descriptions.Item>
                  <Descriptions.Item label="租户简称">{currentTenant.tenantShortName || '-'}</Descriptions.Item>
                  <Descriptions.Item label="状态">
                    <Tag color={currentTenant.status === 'ENABLED' ? 'green' : 'default'}>{currentTenant.status}</Tag>
                  </Descriptions.Item>
                </Descriptions>
              ) : (
                <Empty description="当前尚未选择租户" />
              )}
            </Card>
          </Col>
          <Col xs={24} lg={14}>
            <Card title="租户切换说明">
              <Typography.Paragraph>
                你可以通过顶部租户选择器在可访问租户之间切换。切换后，菜单、权限快照和插件启用状态都会重新加载。
              </Typography.Paragraph>
              <Space wrap>
                <Tag color="blue">租户隔离</Tag>
                <Tag color="purple">权限快照刷新</Tag>
                <Tag color="geekblue">插件上下文重载</Tag>
              </Space>
            </Card>
          </Col>
        </Row>

        <Card title="我可访问的租户" bodyStyle={{ height: 320, minHeight: 0 }}>
          <ProTable<MyTenant>
            rowKey="tenantId"
            columns={tenantColumns}
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
    </PageContainer>
  );
};
