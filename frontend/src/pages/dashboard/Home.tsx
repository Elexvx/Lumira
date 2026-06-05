import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { PageContainer, ProCard } from '@ant-design/pro-components';
import { Avatar, Col, Empty, Row, Skeleton, Space, Tabs, Tag, Typography } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import { ManagementTable } from '@/features/management/ManagementTable';
import { request } from '@/services/common/request';
import type { AuditLogRecord, DashboardSummary } from '@/types/api';
import { API_OPTS } from '@/utils/errorMessage';
import './Home.css';

const MOBILE_HIDE_RESPONSIVE: Array<'md' | 'lg' | 'xl' | 'xxl'> = ['md', 'lg', 'xl', 'xxl'];
const LOGIN_LOG_TABLE_SCROLL_X = 920;
const OPERATION_LOG_TABLE_SCROLL_X = 860;

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

  if (hour >= 18 && hour < 24) {
    return '晚上好';
  }

  return '凌晨好';
};

const formatLoginType = (value?: string | null) => {
  const map: Record<string, string> = {
    PASSWORD: '密码登录',
    SMS: '短信登录',
    EMAIL: '邮箱登录',
    TOTP: '二次验证',
    PASSKEY: '通行密钥',
    WECHAT: '微信登录',
    LOGOUT: '退出登录',
  };
  return value ? map[value.toUpperCase()] || value : '-';
};

const formatLogResult = (value?: string | null) => {
  const map: Record<string, string> = {
    SUCCESS: '成功',
    FAIL: '失败',
    FAILED: '失败',
    ERROR: '异常',
  };
  return value ? map[value.toUpperCase()] || value : '-';
};

const logResultColor = (value?: string | null) => {
  const normalized = value?.toUpperCase();
  if (normalized === 'SUCCESS') {
    return 'green';
  }
  if (normalized === 'FAIL' || normalized === 'FAILED' || normalized === 'ERROR') {
    return 'red';
  }
  return 'default';
};

const buildLoginLogColumns = (isMobile: boolean): ProColumns<AuditLogRecord>[] => [
  {
    title: '时间',
    dataIndex: 'createdAt',
    width: 180,
    render: (_: unknown, record: AuditLogRecord) => <Typography.Text>{formatDateTime(record.createdAt)}</Typography.Text>,
  },
  {
    title: '用户',
    dataIndex: 'username',
    width: 180,
    ...(isMobile ? { responsive: MOBILE_HIDE_RESPONSIVE } : {}),
    render: (_: unknown, record: AuditLogRecord) => <Typography.Text>{record.username || '-'}</Typography.Text>,
  },
  {
    title: '登录方式',
    dataIndex: 'logType',
    width: 140,
    render: (_: unknown, record: AuditLogRecord) => <Typography.Text>{formatLoginType(record.logType || record.loginType)}</Typography.Text>,
  },
  {
    title: '结果',
    dataIndex: 'logResult',
    width: 96,
    render: (_, record) => (
      <Tag color={logResultColor(record.logResult || record.loginResult)}>
        {formatLogResult(record.logResult || record.loginResult)}
      </Tag>
    ),
  },
  {
    title: '登录信息',
    dataIndex: 'loginIp',
    width: 280,
    ellipsis: true,
    render: (_: unknown, record: AuditLogRecord) => {
      const result = record.logResult || record.loginResult;
      const content = record.failReason || (record.loginIp ? `登录 IP：${record.loginIp}` : formatLogResult(result));
      return <Typography.Text ellipsis={{ tooltip: content }}>{content}</Typography.Text>;
    },
  },
];

const buildOperationLogColumns = (title: string, isMobile: boolean): ProColumns<AuditLogRecord>[] => [
  {
    title: '时间',
    dataIndex: 'createdAt',
    width: 180,
    render: (_: unknown, record: AuditLogRecord) => <Typography.Text>{formatDateTime(record.createdAt)}</Typography.Text>,
  },
  {
    title: '用户',
    dataIndex: 'username',
    width: 180,
    ...(isMobile ? { responsive: MOBILE_HIDE_RESPONSIVE } : {}),
    render: (_: unknown, record: AuditLogRecord) => <Typography.Text>{record.username || '-'}</Typography.Text>,
  },
  {
    title: '结果',
    dataIndex: 'logResult',
    width: 100,
    ...(isMobile ? { responsive: MOBILE_HIDE_RESPONSIVE } : {}),
    render: (_: unknown, record: AuditLogRecord) => (
      <Tag color={logResultColor(record.logResult)}>
        {formatLogResult(record.logResult)}
      </Tag>
    ),
  },
  {
    title: '内容',
    dataIndex: 'detailMessage',
    width: 320,
    ellipsis: true,
    render: (_: unknown, record: AuditLogRecord) => {
      const content = record.detailMessage || record.failReason || record.operationType || record.actionName || record.moduleName || title;
      return <Typography.Text ellipsis={{ tooltip: content }}>{content}</Typography.Text>;
    },
  },
];

const useDashboardHome = () => {
  const { initialState } = useInitialStateModel();
  const responsive = useResponsive();
  const dashboardQuery = useQuery({
    queryKey: ['dashboard-summary', initialState?.menuVersion],
    queryFn: async () =>
      request<DashboardSummary>('/v1/dashboard/summary', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      }),
  });

  const summary = dashboardQuery.data as DashboardSummary | undefined;
  const currentUser = summary?.currentUser || initialState?.currentUser;
  const greeting = buildGreeting(dayjs().hour());
  const displayName = currentUser?.nickname || currentUser?.realName || currentUser?.username || '当前用户';
  const recentLoginLogs = summary?.recentLoginLogs || [];
  const recentOperationLogs = summary?.recentOperationLogs || [];
  const loginLogColumns = buildLoginLogColumns(responsive.isMobile);
  const operationLogColumns = buildOperationLogColumns('操作记录', responsive.isMobile);
  const pageContainerToken = {
    paddingInlinePageContainerContent: responsive.isMobile ? 20 : 25,
    paddingBlockPageContainerContent: responsive.isMobile ? 16 : 24,
  };

  return {
    responsive,
    dashboardQuery,
    summary,
    currentUser,
    greeting,
    displayName,
    recentLoginLogs,
    recentOperationLogs,
    loginLogColumns,
    operationLogColumns,
    pageContainerToken,
    buildInitials,
    LOGIN_LOG_TABLE_SCROLL_X,
    OPERATION_LOG_TABLE_SCROLL_X,
  };
};

const DashboardHomePage = () => {
  const {
    responsive,
    dashboardQuery,
    summary,
    currentUser,
    greeting,
    displayName,
    recentLoginLogs,
    recentOperationLogs,
    loginLogColumns,
    operationLogColumns,
    pageContainerToken,
    buildInitials,
    LOGIN_LOG_TABLE_SCROLL_X,
    OPERATION_LOG_TABLE_SCROLL_X,
  } = useDashboardHome();
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

        <Row gutter={[16, 16]} align="stretch">
          <Col xs={24}>
            <ProCard variant="outlined" title="近期动态" className="saas-dashboard-home__panel">
              <Tabs
                defaultActiveKey="login"
                items={[
                  {
                    key: 'login',
                    label: `登录记录 (${recentLoginLogs.length})`,
                    children: (
                      <ManagementTable<AuditLogRecord>
                        className="saas-dashboard-home__activity-table"
                        size="small"
                        rowKey="id"
                        pagination={false}
                        isMobile={responsive.isMobile}
                        search={false}
                        scroll={{ x: LOGIN_LOG_TABLE_SCROLL_X }}
                        onRefresh={() => dashboardQuery.refetch()}
                        loading={dashboardQuery.isLoading && !summary}
                        columns={loginLogColumns}
                        dataSource={recentLoginLogs}
                        locale={{
                          emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无登录记录" />,
                        }}
                      />
                    ),
                  },
                  {
                    key: 'operation',
                    label: `操作记录 (${recentOperationLogs.length})`,
                    children: (
                      <ManagementTable<AuditLogRecord>
                        className="saas-dashboard-home__activity-table"
                        size="small"
                        rowKey="id"
                        pagination={false}
                        isMobile={responsive.isMobile}
                        search={false}
                        scroll={{ x: OPERATION_LOG_TABLE_SCROLL_X }}
                        onRefresh={() => dashboardQuery.refetch()}
                        loading={dashboardQuery.isLoading && !summary}
                        columns={operationLogColumns}
                        dataSource={recentOperationLogs}
                        locale={{
                          emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无操作记录" />,
                        }}
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
