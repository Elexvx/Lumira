import { history } from '@umijs/max';
import { PageContainer, ProCard, StatisticCard } from '@ant-design/pro-components';
import { Avatar, Badge, Button, Col, Descriptions, Empty, List, Row, Skeleton, Space, Table, Tag, Tabs, Typography } from 'antd';
import dayjs from 'dayjs';
import { useMemo } from 'react';
import { dashboardService } from '@/services/dashboard';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useRequest } from '@umijs/max';
import type { AuditLogRecord, DashboardSummary, TenantPlugin } from '@/types/api';
import './Home.less';

const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '-';
  }

  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm') : value;
};

const buildInitials = (name?: string | null, fallback = 'U') => {
  const source = name?.trim();
  if (!source) {
    return fallback;
  }

  return source.slice(0, 1).toUpperCase();
};

const buildLogColumns = (title: string) => [
  {
    title: '时间',
    dataIndex: 'createdAt',
    width: 180,
    render: (_: unknown, record: AuditLogRecord) => <Typography.Text>{formatDateTime(record.createdAt)}</Typography.Text>,
  },
  {
    title: '用户',
    dataIndex: 'username',
    width: 140,
    render: (_: unknown, record: AuditLogRecord) => <Typography.Text>{record.username || '-'}</Typography.Text>,
  },
  {
    title: '类型',
    dataIndex: 'logResult',
    width: 120,
    render: (_: unknown, record: AuditLogRecord) => (
      <Tag color={record.logResult === 'SUCCESS' ? 'green' : record.logResult === 'FAILED' ? 'red' : 'default'}>
        {record.logResult || '-'}
      </Tag>
    ),
  },
  {
    title: '内容',
    dataIndex: 'detailMessage',
    ellipsis: true,
    render: (_: unknown, record: AuditLogRecord) => {
      const content = record.detailMessage || record.failReason || record.operationType || record.actionName || record.moduleName || title;
      return <Typography.Text ellipsis={{ tooltip: content }}>{content}</Typography.Text>;
    },
  },
];

const renderPluginDescription = (plugin: TenantPlugin) => {
  const sharedDeps = plugin.sharedDeps?.length ? plugin.sharedDeps.join('、') : '无共享依赖';
  const routeCount = plugin.routes?.length || plugin.menus?.length || 0;

  return (
    <Space direction="vertical" size={4} style={{ width: '100%' }}>
      <Typography.Text type="secondary">{plugin.manifestPath}</Typography.Text>
      <Typography.Text type="secondary">
        {routeCount > 0 ? `包含 ${routeCount} 个路由或菜单` : '未声明前端入口'}
      </Typography.Text>
      <Typography.Text type="secondary">{sharedDeps}</Typography.Text>
    </Space>
  );
};

const DashboardHomePage = () => {
  const { initialState } = useInitialStateModel();
  const actionPermission = useActionPermission();
  const dashboardQuery = useRequest(
    async () => dashboardService.summary({ autoRedirectOnUnauthorized: false }),
    {
      refreshDeps: [],
    },
  );

  const summary = dashboardQuery.data as DashboardSummary | undefined;
  const currentUser = summary?.currentUser || initialState?.currentUser;
  const currentTenant = summary?.currentTenant || initialState?.currentTenant || null;
  const tenantPlugins = summary?.tenantPlugins || [];
  const recentLoginLogs = summary?.recentLoginLogs || [];
  const recentOperationLogs = summary?.recentOperationLogs || [];
  const shortcuts = useMemo(
    () =>
      (summary?.shortcuts || [])
        .filter((item) => actionPermission.can(item.permission))
        .map((item) => ({
          ...item,
          label: item.title,
          onClick: () => history.push(item.path),
        })),
    [actionPermission, summary?.shortcuts],
  );
  const overviewStats = [
    {
      title: '菜单数',
      value: summary?.menuCount ?? 0,
      suffix: '个',
      description: '当前租户可见菜单',
    },
    {
      title: '权限数',
      value: summary?.permissionCount ?? 0,
      suffix: '项',
      description: '当前会话权限总数',
    },
    {
      title: '插件数',
      value: tenantPlugins.length,
      suffix: '个',
      description: '已加载租户插件',
    },
    {
      title: '近期记录',
      value: recentLoginLogs.length + recentOperationLogs.length,
      suffix: '条',
      description: '最近登录与操作摘要',
    },
  ];

  return (
    <PageContainer title="工作台" ghost content={null} className="saas-dashboard-home__page">
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <ProCard bordered={false} className="saas-dashboard-home__hero">
          <Row gutter={[24, 24]} align="middle">
            <Col xs={24} xl={16}>
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Space align="center" size={16} wrap>
                  <Avatar size={64} src={currentUser?.avatarUrl || undefined}>
                    {buildInitials(currentUser?.nickname || currentUser?.realName || currentUser?.username)}
                  </Avatar>
                  <Space direction="vertical" size={4}>
                    <Typography.Text type="secondary">欢迎回来</Typography.Text>
                    <Typography.Title level={3} style={{ margin: 0 }}>
                      {currentUser?.nickname || currentUser?.realName || currentUser?.username || '当前用户'}
                    </Typography.Title>
                    <Typography.Text type="secondary">
                      {currentTenant ? `${currentTenant.tenantName} · ${currentTenant.tenantCode}` : '当前未选择租户'}
                    </Typography.Text>
                  </Space>
                </Space>
                {dashboardQuery.loading && !summary ? (
                  <Skeleton active paragraph={{ rows: 2 }} title={false} />
                ) : (
                  <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                    这里汇总当前租户的菜单、权限、插件和近期操作，页面全部使用官方组件组织。
                  </Typography.Paragraph>
                )}
              </Space>
            </Col>
            <Col xs={24} xl={8}>
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                <Typography.Text type="secondary">快捷入口</Typography.Text>
                {shortcuts.length ? (
                  <Space wrap>
                    {shortcuts.map((item, index) => (
                      <Button key={item.path} type={index === 0 ? 'primary' : 'default'} onClick={item.onClick}>
                        {item.title}
                      </Button>
                    ))}
                  </Space>
                ) : (
                  <Empty description="暂无可用快捷入口" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                )}
              </Space>
            </Col>
          </Row>
        </ProCard>

        <Row gutter={[16, 16]}>
          {overviewStats.map((stat) => (
            <Col key={stat.title} xs={24} sm={12} xl={6}>
              <StatisticCard
                bordered
                statistic={{
                  title: stat.title,
                  value: stat.value,
                  suffix: stat.suffix,
                  description: <Typography.Text type="secondary">{stat.description}</Typography.Text>,
                }}
              />
            </Col>
          ))}
        </Row>

        <Row gutter={[16, 16]} align="stretch">
          <Col xs={24} xl={16}>
            <ProCard bordered title="近期动态" className="saas-dashboard-home__panel">
              <Tabs
                defaultActiveKey="login"
                items={[
                  {
                    key: 'login',
                    label: `登录记录 (${recentLoginLogs.length})`,
                    children: (
                      <Table<AuditLogRecord>
                        size="small"
                        rowKey="id"
                        pagination={false}
                        loading={dashboardQuery.loading && !summary}
                        columns={buildLogColumns('登录记录')}
                        dataSource={recentLoginLogs}
                        locale={{
                          emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无登录记录" />,
                        }}
                        scroll={{ x: 720 }}
                      />
                    ),
                  },
                  {
                    key: 'operation',
                    label: `操作记录 (${recentOperationLogs.length})`,
                    children: (
                      <Table<AuditLogRecord>
                        size="small"
                        rowKey="id"
                        pagination={false}
                        loading={dashboardQuery.loading && !summary}
                        columns={buildLogColumns('操作记录')}
                        dataSource={recentOperationLogs}
                        locale={{
                          emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无操作记录" />,
                        }}
                        scroll={{ x: 720 }}
                      />
                    ),
                  },
                ]}
              />
            </ProCard>
          </Col>

          <Col xs={24} xl={8}>
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <ProCard bordered title="当前租户" className="saas-dashboard-home__panel">
                {currentTenant ? (
                  <Descriptions column={1} size="small" colon labelStyle={{ width: 88, textAlign: 'right' }}>
                    <Descriptions.Item label="租户名称">{currentTenant.tenantName}</Descriptions.Item>
                    <Descriptions.Item label="租户编码">{currentTenant.tenantCode}</Descriptions.Item>
                    <Descriptions.Item label="状态">
                      <Badge status={currentTenant.status === 'ENABLED' ? 'success' : 'default'} text={currentTenant.status} />
                    </Descriptions.Item>
                    <Descriptions.Item label="更新时间">{formatDateTime(currentTenant.updatedAt)}</Descriptions.Item>
                  </Descriptions>
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未获取到租户信息" />
                )}
              </ProCard>

              <ProCard bordered title="租户插件" className="saas-dashboard-home__panel">
                {tenantPlugins.length ? (
                  <List
                    size="small"
                    dataSource={tenantPlugins}
                    renderItem={(plugin) => (
                      <List.Item style={{ paddingInline: 0 }}>
                        <List.Item.Meta title={plugin.pluginName || plugin.pluginCode} description={renderPluginDescription(plugin)} />
                        <Tag color="blue">{plugin.version}</Tag>
                      </List.Item>
                    )}
                  />
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无插件数据" />
                )}
              </ProCard>
            </Space>
          </Col>
        </Row>
      </Space>
    </PageContainer>
  );
};

export default DashboardHomePage;
