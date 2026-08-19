import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { ReloadOutlined } from '@ant-design/icons';
import { PageContainer, ProCard } from '@ant-design/pro-components';
import { Button, Empty, Skeleton, Space, Tabs, Tag, Typography } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import { ManagementTable } from '@/features/management/ManagementTable';
import { UserAvatar } from '@/components/UserAvatar';
import { mergeSameSessionCurrentUser } from '@/auth/sessionState';
import { request } from '@/services/common/request';
import type { AuditLogRecord, DashboardSummary } from '@/types/api';
import { API_OPTS } from '@/utils/errorMessage';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import './Home.css';

import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

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

const buildGreeting = (hour: number) => {
  if (hour >= 5 && hour < 9) {
    return t('ui.dashboard.home.goodMorning');
  }

  if (hour >= 9 && hour < 12) {
    return t('ui.dashboard.home.goodMorning.22c41315');
  }

  if (hour >= 12 && hour < 14) {
    return t('ui.dashboard.home.goodNoon');
  }

  if (hour >= 14 && hour < 18) {
    return t('ui.dashboard.home.goodAfternoon');
  }

  if (hour >= 18 && hour < 24) {
    return t('ui.dashboard.home.goodEvening');
  }

  return t('ui.dashboard.home.goodEarlyMorning');
};

const formatLoginType = (value?: string | null) => {
  const map: Record<string, string> = {
    PASSWORD: t('ui.dashboard.home.passwordLogin'),
    SMS: t('ui.dashboard.home.smsLogin'),
    EMAIL: t('ui.dashboard.home.emailLogin'),
    TOTP: t('ui.dashboard.home.2fa'),
    PASSKEY: t('ui.dashboard.home.passkey'),
    WECHAT: t('ui.dashboard.home.wechatLogin'),
    LOGOUT: t('ui.dashboard.home.logout'),
  };
  return value ? map[value.toUpperCase()] || value : '-';
};

const formatLogResult = (value?: string | null) => {
  const map: Record<string, string> = {
    SUCCESS: t('ui.dashboard.home.success'),
    FAIL: t('ui.dashboard.home.failed'),
    FAILED: t('ui.dashboard.home.failed'),
    ERROR: t('ui.dashboard.home.error'),
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
    title: t('ui.dashboard.home.time'),
    dataIndex: 'createdAt',
    width: 'var(--saas-spacing-180)',
    render: (_: unknown, record: AuditLogRecord) => <Typography.Text>{formatDateTime(record.createdAt)}</Typography.Text>,
  },
  {
    title: t('ui.dashboard.home.user'),
    dataIndex: 'username',
    width: 'var(--saas-spacing-180)',
    ...(isMobile ? { responsive: MOBILE_HIDE_RESPONSIVE } : {}),
    render: (_: unknown, record: AuditLogRecord) => <Typography.Text>{record.username || '-'}</Typography.Text>,
  },
  {
    title: t('ui.dashboard.home.loginMethod'),
    dataIndex: 'logType',
    width: 'var(--saas-spacing-140)',
    render: (_: unknown, record: AuditLogRecord) => <Typography.Text>{formatLoginType(record.logType || record.loginType)}</Typography.Text>,
  },
  {
    title: t('ui.dashboard.home.result'),
    dataIndex: 'logResult',
    width: 'var(--saas-spacing-96)',
    render: (_, record) => (
      <Tag color={logResultColor(record.logResult || record.loginResult)}>
        {formatLogResult(record.logResult || record.loginResult)}
      </Tag>
    ),
  },
  {
    title: t('ui.dashboard.home.loginInfo'),
    dataIndex: 'loginIp',
    width: 'var(--saas-spacing-280)',
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
    title: t('ui.dashboard.home.time'),
    dataIndex: 'createdAt',
    width: 'var(--saas-spacing-180)',
    render: (_: unknown, record: AuditLogRecord) => <Typography.Text>{formatDateTime(record.createdAt)}</Typography.Text>,
  },
  {
    title: t('ui.dashboard.home.user'),
    dataIndex: 'username',
    width: 'var(--saas-spacing-180)',
    ...(isMobile ? { responsive: MOBILE_HIDE_RESPONSIVE } : {}),
    render: (_: unknown, record: AuditLogRecord) => <Typography.Text>{record.username || '-'}</Typography.Text>,
  },
  {
    title: t('ui.dashboard.home.result'),
    dataIndex: 'logResult',
    width: 'var(--saas-spacing-100)',
    ...(isMobile ? { responsive: MOBILE_HIDE_RESPONSIVE } : {}),
    render: (_: unknown, record: AuditLogRecord) => (
      <Tag color={logResultColor(record.logResult)}>
        {formatLogResult(record.logResult)}
      </Tag>
    ),
  },
  {
    title: t('ui.dashboard.home.content'),
    dataIndex: 'detailMessage',
    width: 'var(--saas-spacing-320)',
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
    queryKey: [
      'dashboard-summary',
      initialState?.currentUser?.userId,
      initialState?.currentUser?.sessionId,
      initialState?.menuVersion,
    ],
    enabled: Boolean(initialState?.currentUser),
    retry: false,
    // Dashboard summary is additive; the page already has safe local fallbacks.
    queryFn: async () =>
      request<DashboardSummary>('/v1/dashboard/summary', {
        method: 'GET',
        ...API_OPTS.SILENT_NO_REDIRECT,
      }).catch(() => undefined),
  });

  const summary = dashboardQuery.data as DashboardSummary | undefined;
  const currentUser = mergeSameSessionCurrentUser(initialState?.currentUser, summary?.currentUser);
  const greeting = buildGreeting(dayjs().hour());
  const displayName = currentUser?.nickname || currentUser?.realName || currentUser?.username || t('ui.dashboard.home.currentUser');
  const recentLoginLogs = summary?.recentLoginLogs || [];
  const recentOperationLogs = summary?.recentOperationLogs || [];
  const loginLogColumns = buildLoginLogColumns(responsive.isMobile);
  const operationLogColumns = buildOperationLogColumns(t('ui.dashboard.home.operationLogs'), responsive.isMobile);
  const pageContainerToken = {
    paddingInlinePageContainerContent: resolveResponsiveValue(APP_SPACING.pageContainerPaddingInline, responsive.isMobile),
    paddingBlockPageContainerContent: resolveResponsiveValue(APP_SPACING.pageContainerPaddingBlock, responsive.isMobile),
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
    LOGIN_LOG_TABLE_SCROLL_X,
    OPERATION_LOG_TABLE_SCROLL_X,
  } = useDashboardHome();
  const renderActivityToolbar = () => [
    <Button
      key="refresh"
      icon={<ReloadOutlined />}
      loading={dashboardQuery.isFetching}
      size={responsive.isMobile ? 'small' : 'middle'}
      onClick={() => dashboardQuery.refetch()}
    >
      刷新
    </Button>,
  ];

  return (
    <PageContainer title={t('ui.dashboard.home.dashboard')} ghost content={null} token={pageContainerToken} className="saas-dashboard-home__page">
      <Space orientation="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile)} style={{ width: '100%' }}>
        <ProCard variant="outlined" className="saas-dashboard-home__hero">
          <Space orientation="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile)} style={{ width: '100%' }}>
            <Space align="center" size={resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile)} wrap>
              <UserAvatar
                size={resolveResponsiveValue(APP_SPACING.avatarSize.normal, responsive.isMobile)}
                avatarUrl={currentUser?.avatarUrl}
                userId={currentUser?.userId}
                userUuid={currentUser?.userUuid}
                username={currentUser?.username}
              />
              <Space orientation="vertical" size={resolveResponsiveValue(APP_SPACING.microGap, responsive.isMobile)}>
                <Typography.Title level={3} style={{ margin: 0 }}>
                  {greeting}，{displayName}
                </Typography.Title>
                <Typography.Text type="secondary">{t('ui.dashboard.home.welcomeBackLetSKeepWorkingOnToday')}</Typography.Text>
              </Space>
            </Space>
            {dashboardQuery.isLoading && !summary ? <Skeleton active paragraph={{ rows: 2 }} title={false} /> : null}
          </Space>
        </ProCard>

        <ProCard title={t('ui.dashboard.home.recentActivity')} variant="outlined" className="saas-dashboard-home__panel">
          <Tabs
            defaultActiveKey="login"
            items={[
              {
                key: 'login',
                label: t('ui.dashboard.home.loginRecords').replace('{count}', String(recentLoginLogs.length)),
                children: (
                  <ManagementTable<AuditLogRecord>
                    className="saas-dashboard-home__activity-table"
                    defaultSize="small"
                    rowKey="id"
                    pagination={false}
                    isMobile={responsive.isMobile}
                    search={false}
                    scroll={{ x: LOGIN_LOG_TABLE_SCROLL_X }}
                    toolBarRender={renderActivityToolbar}
                    loading={dashboardQuery.isLoading && !summary}
                    columns={loginLogColumns}
                    dataSource={recentLoginLogs}
                    locale={{
                      emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('ui.dashboard.home.noLoginRecords')} />,
                    }}
                  />
                ),
              },
              {
                key: 'operation',
                label: t('ui.dashboard.home.operationRecords').replace('{count}', String(recentOperationLogs.length)),
                children: (
                  <ManagementTable<AuditLogRecord>
                    className="saas-dashboard-home__activity-table"
                    defaultSize="small"
                    rowKey="id"
                    pagination={false}
                    isMobile={responsive.isMobile}
                    search={false}
                    scroll={{ x: OPERATION_LOG_TABLE_SCROLL_X }}
                    toolBarRender={renderActivityToolbar}
                    loading={dashboardQuery.isLoading && !summary}
                    columns={operationLogColumns}
                    dataSource={recentOperationLogs}
                    locale={{
                      emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('ui.dashboard.home.noOperationRecords')} />,
                    }}
                  />
                ),
              },
            ]}
          />
        </ProCard>
      </Space>
    </PageContainer>
  );
};

export default DashboardHomePage;
