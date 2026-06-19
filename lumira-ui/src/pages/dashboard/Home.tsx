import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { PageContainer, ProCard } from '@ant-design/pro-components';
import { Avatar, Empty, Skeleton, Space, Tabs, Tag, Typography } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import { ManagementTable } from '@/features/management/ManagementTable';
import { request } from '@/services/common/request';
import type { AuditLogRecord, DashboardSummary } from '@/types/api';
import { API_OPTS } from '@/utils/errorMessage';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import './Home.css';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

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
    return t('早上好', 'Good morning');
  }

  if (hour >= 9 && hour < 12) {
    return t('上午好', 'Good morning');
  }

  if (hour >= 12 && hour < 14) {
    return t('中午好', 'Good noon');
  }

  if (hour >= 14 && hour < 18) {
    return t('下午好', 'Good afternoon');
  }

  if (hour >= 18 && hour < 24) {
    return t('晚上好', 'Good evening');
  }

  return t('凌晨好', 'Good early morning');
};

const formatLoginType = (value?: string | null) => {
  const map: Record<string, string> = {
    PASSWORD: t('密码登录', 'Password login'),
    SMS: t('短信登录', 'SMS login'),
    EMAIL: t('邮箱登录', 'Email login'),
    TOTP: t('二次验证', '2FA'),
    PASSKEY: t('通行密钥', 'Passkey'),
    WECHAT: t('微信登录', 'WeChat login'),
    LOGOUT: t('退出登录', 'Logout'),
  };
  return value ? map[value.toUpperCase()] || value : '-';
};

const formatLogResult = (value?: string | null) => {
  const map: Record<string, string> = {
    SUCCESS: t('成功', 'Success'),
    FAIL: t('失败', 'Failed'),
    FAILED: t('失败', 'Failed'),
    ERROR: t('异常', 'Error'),
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
    title: t('时间', 'Time'),
    dataIndex: 'createdAt',
    width: 'var(--saas-spacing-180)',
    render: (_: unknown, record: AuditLogRecord) => <Typography.Text>{formatDateTime(record.createdAt)}</Typography.Text>,
  },
  {
    title: t('用户', 'User'),
    dataIndex: 'username',
    width: 'var(--saas-spacing-180)',
    ...(isMobile ? { responsive: MOBILE_HIDE_RESPONSIVE } : {}),
    render: (_: unknown, record: AuditLogRecord) => <Typography.Text>{record.username || '-'}</Typography.Text>,
  },
  {
    title: t('登录方式', 'Login method'),
    dataIndex: 'logType',
    width: 'var(--saas-spacing-140)',
    render: (_: unknown, record: AuditLogRecord) => <Typography.Text>{formatLoginType(record.logType || record.loginType)}</Typography.Text>,
  },
  {
    title: t('结果', 'Result'),
    dataIndex: 'logResult',
    width: 'var(--saas-spacing-96)',
    render: (_, record) => (
      <Tag color={logResultColor(record.logResult || record.loginResult)}>
        {formatLogResult(record.logResult || record.loginResult)}
      </Tag>
    ),
  },
  {
    title: t('登录信息', 'Login info'),
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
    title: t('时间', 'Time'),
    dataIndex: 'createdAt',
    width: 'var(--saas-spacing-180)',
    render: (_: unknown, record: AuditLogRecord) => <Typography.Text>{formatDateTime(record.createdAt)}</Typography.Text>,
  },
  {
    title: t('用户', 'User'),
    dataIndex: 'username',
    width: 'var(--saas-spacing-180)',
    ...(isMobile ? { responsive: MOBILE_HIDE_RESPONSIVE } : {}),
    render: (_: unknown, record: AuditLogRecord) => <Typography.Text>{record.username || '-'}</Typography.Text>,
  },
  {
    title: t('结果', 'Result'),
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
    title: t('内容', 'Content'),
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
    queryKey: ['dashboard-summary', initialState?.menuVersion],
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
  const currentUser = summary?.currentUser || initialState?.currentUser;
  const greeting = buildGreeting(dayjs().hour());
  const displayName = currentUser?.nickname || currentUser?.realName || currentUser?.username || t('当前用户', 'Current user');
  const recentLoginLogs = summary?.recentLoginLogs || [];
  const recentOperationLogs = summary?.recentOperationLogs || [];
  const loginLogColumns = buildLoginLogColumns(responsive.isMobile);
  const operationLogColumns = buildOperationLogColumns(t('操作记录', 'Operation logs'), responsive.isMobile);
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
    <PageContainer title={t('工作台', 'Dashboard')} ghost content={null} token={pageContainerToken} className="saas-dashboard-home__page">
      <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile)} style={{ width: '100%' }}>
        <ProCard variant="borderless" className="saas-dashboard-home__hero">
          <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile)} style={{ width: '100%' }}>
            <Space align="center" size={resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile)} wrap>
              <Avatar size={resolveResponsiveValue(APP_SPACING.avatarSize.normal, responsive.isMobile)} src={currentUser?.avatarUrl || undefined}>
                {buildInitials(currentUser?.nickname || currentUser?.realName || currentUser?.username)}
              </Avatar>
              <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.microGap, responsive.isMobile)}>
                <Typography.Title level={3} style={{ margin: 0 }}>
                  {greeting}，{displayName}
                </Typography.Title>
                <Typography.Text type="secondary">{t('欢迎回来，继续处理今天的系统事项', 'Welcome back. Let’s keep working on today’s tasks.')}</Typography.Text>
              </Space>
            </Space>
            {dashboardQuery.isLoading && !summary ? <Skeleton active paragraph={{ rows: 2 }} title={false} /> : null}
          </Space>
        </ProCard>

        <ProCard title={t('近期动态', 'Recent activity')} variant="outlined" className="saas-dashboard-home__panel">
          <Tabs
            defaultActiveKey="login"
            items={[
              {
                key: 'login',
                label: t('登录记录 ({count})', 'Login records ({count})').replace('{count}', String(recentLoginLogs.length)),
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
                      emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('暂无登录记录', 'No login records')} />,
                    }}
                  />
                ),
              },
              {
                key: 'operation',
                label: t('操作记录 ({count})', 'Operation records ({count})').replace('{count}', String(recentOperationLogs.length)),
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
                      emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('暂无操作记录', 'No operation records')} />,
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
