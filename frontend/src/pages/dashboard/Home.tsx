import { PageContainer, ProCard } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { Avatar, Col, Empty, List, Row, Skeleton, Space, Statistic, Table, Tag, Tabs, Typography } from 'antd';
import dayjs from 'dayjs';
import { buildTableScroll } from '@/features/table/proTable';
import { dashboardService } from '@/services/dashboard';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import type { AuditLogRecord, DashboardSummary } from '@/types/api';
import './Home.css';

const MOBILE_HIDE_RESPONSIVE: Array<'md' | 'lg' | 'xl' | 'xxl'> = ['md', 'lg', 'xl', 'xxl'];

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

const buildGreeting = (hour: number) => {
  if (hour >= 5 && hour < 9) {
    return '早上好';
  }

  if (hour >= 9 && hour < 12) {
    return '上午好';
  }

  if (hour >= 12 && hour < 14) {
    return '中午好';
  }

  if (hour >= 14 && hour < 18) {
    return '下午好';
  }

  if (hour >= 18 && hour < 23) {
    return '晚上好';
  }

  return '凌晨好';
};

const buildLogColumns = (title: string, isMobile: boolean) => [
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
    ...(isMobile ? { responsive: MOBILE_HIDE_RESPONSIVE } : {}),
    render: (_: unknown, record: AuditLogRecord) => <Typography.Text>{record.username || '-'}</Typography.Text>,
  },
  {
    title: '类型',
    dataIndex: 'logResult',
    width: 120,
    ...(isMobile ? { responsive: MOBILE_HIDE_RESPONSIVE } : {}),
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

const DashboardHomePage = () => {
  const { initialState } = useInitialStateModel();
  const responsive = useResponsive();
  const dashboardQuery = useQuery({
    queryKey: ['dashboard-summary', initialState?.menuVersion],
    queryFn: async () => dashboardService.summary({ autoRedirectOnUnauthorized: false }),
  });

  const summary = dashboardQuery.data as DashboardSummary | undefined;
  const currentUser = summary?.currentUser || initialState?.currentUser;
  const greeting = buildGreeting(dayjs().hour());
  const displayName = currentUser?.nickname || currentUser?.realName || currentUser?.username || '当前用户';
  const recentLoginLogs = summary?.recentLoginLogs || [];
  const recentOperationLogs = summary?.recentOperationLogs || [];
  const taskSummary = summary?.taskSummary;
  const latestPendingTasks = taskSummary?.latestPending || [];
  const loginLogColumns = buildLogColumns('登录记录', responsive.isMobile);
  const operationLogColumns = buildLogColumns('操作记录', responsive.isMobile);
  const pageContainerToken = {
    paddingInlinePageContainerContent: responsive.isMobile ? 20 : 25,
    paddingBlockPageContainerContent: responsive.isMobile ? 16 : 24,
  };
  return (
    <PageContainer title="工作台" ghost content={null} token={pageContainerToken} className="saas-dashboard-home__page">
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <ProCard variant="borderless" className="saas-dashboard-home__hero">
          <Row gutter={[24, 24]} align="middle">
            <Col xs={24}>
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Space align="center" size={16} wrap>
                  <Avatar size={64} src={currentUser?.avatarUrl || undefined}>
                    {buildInitials(currentUser?.nickname || currentUser?.realName || currentUser?.username)}
                  </Avatar>
                  <Space direction="vertical" size={4}>
                    <Typography.Title level={3} style={{ margin: 0 }}>
                      {greeting}，{displayName}
                    </Typography.Title>
                    <Typography.Text type="secondary">欢迎回来，继续处理今天的系统事项</Typography.Text>
                  </Space>
                </Space>
                {dashboardQuery.isLoading && !summary ? (
                  <Skeleton active paragraph={{ rows: 2 }} title={false} />
                ) : null}
              </Space>
            </Col>
          </Row>
        </ProCard>

        <ProCard variant="outlined" title="待办事项" className="saas-dashboard-home__todo">
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Row gutter={[16, 16]}>
              <Col xs={12} md={6}><Statistic title="全部待办" value={taskSummary?.pendingCount || 0} /></Col>
              <Col xs={12} md={6}><Statistic title="待审批" value={taskSummary?.approvalCount || 0} /></Col>
              <Col xs={12} md={6}><Statistic title="待评分" value={taskSummary?.evaluationCount || 0} /></Col>
              <Col xs={12} md={6}><Statistic title="待复核" value={taskSummary?.reviewCount || 0} /></Col>
            </Row>
            {latestPendingTasks.length ? (
              <List
                dataSource={latestPendingTasks}
                renderItem={(item) => (
                  <List.Item>
                    <List.Item.Meta title={item.title} description={`${item.businessType} · ${formatDateTime(item.createTime)}`} />
                    <Tag color="blue">{item.taskType}</Tag>
                  </List.Item>
                )}
              />
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无待办事项" />
            )}
          </Space>
        </ProCard>

        <Row gutter={[16, 16]} align="stretch">
          <Col xs={24} xl={16}>
            <ProCard variant="outlined" title="近期动态" className="saas-dashboard-home__panel">
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
                        loading={dashboardQuery.isLoading && !summary}
                        columns={loginLogColumns}
                        dataSource={recentLoginLogs}
                        locale={{
                          emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无登录记录" />,
                        }}
                        scroll={buildTableScroll(loginLogColumns, responsive.isMobile)}
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
                        loading={dashboardQuery.isLoading && !summary}
                        columns={operationLogColumns}
                        dataSource={recentOperationLogs}
                        locale={{
                          emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无操作记录" />,
                        }}
                        scroll={buildTableScroll(operationLogColumns, responsive.isMobile)}
                      />
                    ),
                  },
                ]}
              />
            </ProCard>
          </Col>

        </Row>
      </Space>
    </PageContainer>
  );
};

export default DashboardHomePage;
