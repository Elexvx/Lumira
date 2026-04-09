import { AppstoreOutlined, AuditOutlined, ControlOutlined, RocketOutlined, SettingOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Avatar, Card, Col, Empty, List, Row, Space, Tag, Typography } from 'antd';
import { useMemo, type ReactNode } from 'react';
import { Link, useRequest } from 'umi';
import { dashboardService } from '@/services/dashboard';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import type { DashboardSummary } from '@/types/api';
import './Home.less';

interface QuickEntry {
  title: string;
  path: string;
  description: string;
  icon: ReactNode;
  accent: string;
  tone: string;
}

const quickEntries: QuickEntry[] = [
  {
    title: '系统管理',
    path: '/system/management',
    description: '用户、角色、菜单、字典、配置',
    icon: <SettingOutlined />,
    accent: '#1d4ed8',
    tone: '#eef4ff',
  },
  {
    title: '租户中心',
    path: '/tenant/overview',
    description: '当前租户与可访问租户',
    icon: <TeamOutlined />,
    accent: '#15803d',
    tone: '#eefaf3',
  },
  {
    title: '审计中心',
    path: '/system/monitoring/audit',
    description: '登录和操作日志',
    icon: <AuditOutlined />,
    accent: '#d97706',
    tone: '#fff5eb',
  },
  {
    title: '插件管理',
    path: '/system/plugins',
    description: '插件安装、启用、运行态',
    icon: <AppstoreOutlined />,
    accent: '#6d28d9',
    tone: '#f4f3ff',
  },
];

const quickEntryByPath = new Map(quickEntries.map((item) => [item.path, item]));
const numberFormatter = new Intl.NumberFormat('zh-CN');

const parseTime = (time?: string) => {
  if (!time) {
    return 0;
  }
  const ts = new Date(time).getTime();
  return Number.isNaN(ts) ? 0 : ts;
};

const getGreetingByHour = (hour: number) => {
  if (hour < 9) {
    return '早安';
  }
  if (hour < 12) {
    return '上午好';
  }
  if (hour < 18) {
    return '下午好';
  }
  return '晚上好';
};

export default () => {
  const { initialState } = useInitialStateModel();
  const { isMobile } = useResponsive();
  const summaryQuery = useRequest(async () => ({ data: await dashboardService.summary({ autoRedirectOnUnauthorized: false }) }) as {
    data: DashboardSummary;
  }, {
    refreshDeps: [initialState?.currentTenant?.tenantId],
  });
  const summary = summaryQuery.data;

  const userInfo = useMemo(
    () => summary?.currentUser || initialState?.currentUser,
    [initialState?.currentUser, summary?.currentUser],
  );

  const tenantInfo = useMemo(
    () => summary?.currentTenant || initialState?.currentTenant || null,
    [initialState?.currentTenant, summary?.currentTenant],
  );

  const welcomeName = userInfo?.realName || userInfo?.nickname || userInfo?.username || '同学';
  const greeting = `${getGreetingByHour(new Date().getHours())}，${welcomeName}`;
  const greetingNote = tenantInfo?.tenantName || '当前尚未选择租户';

  const projectEntries = useMemo(() => {
    if (!summary?.shortcuts?.length) {
      return quickEntries;
    }
    return summary.shortcuts.map((item) => {
      const matched = quickEntryByPath.get(item.path);
      return {
        title: item.title,
        path: item.path,
        description: item.description || matched?.description || '进入工作台模块',
        icon: matched?.icon || <ControlOutlined />,
        accent: matched?.accent || '#1d4ed8',
        tone: matched?.tone || '#eef4ff',
      };
    });
  }, [summary?.shortcuts]);

  const heroMetrics = useMemo(
    () => [
      { label: '菜单', value: summary?.menuCount ?? initialState?.menuTree?.length ?? 0 },
      { label: '权限', value: summary?.permissionCount ?? initialState?.currentUser?.permissions?.length ?? 0 },
      { label: '插件', value: summary?.tenantPlugins?.length ?? initialState?.availablePlugins?.length ?? 0 },
      { label: '动态', value: (summary?.recentLoginLogs?.length || 0) + (summary?.recentOperationLogs?.length || 0) },
    ],
    [
      initialState?.availablePlugins?.length,
      initialState?.currentUser?.permissions?.length,
      initialState?.menuTree?.length,
      summary?.menuCount,
      summary?.permissionCount,
      summary?.recentLoginLogs?.length,
      summary?.recentOperationLogs?.length,
      summary?.tenantPlugins?.length,
    ],
  );

  const activityItems = useMemo(() => {
    const loginItems = (summary?.recentLoginLogs || []).map((item) => ({
      key: `login-${item.id}`,
      title: `${item.username || '未知用户'} ${item.logResult === 'SUCCESS' || item.loginResult === 'SUCCESS' ? '完成登录' : '登录失败'}`,
      desc: item.failReason || item.detailMessage || '认证链路已记录到审计中心',
      tag: '登录',
      color: item.logResult === 'SUCCESS' || item.loginResult === 'SUCCESS' ? 'green' : 'red',
      createdAt: item.createdAt,
      sortTime: parseTime(item.createdAt),
    }));
    const operationItems = (summary?.recentOperationLogs || []).map((item) => ({
      key: `op-${item.id}`,
      title: `${item.username || '系统'} 在 ${item.moduleName || '平台模块'} 执行 ${item.actionName || item.operationType || '操作'}`,
      desc: item.detailMessage || '操作日志已归档',
      tag: '操作',
      color: 'blue',
      createdAt: item.createdAt,
      sortTime: parseTime(item.createdAt),
    }));
    return [...operationItems, ...loginItems].sort((a, b) => b.sortTime - a.sortTime).slice(0, 8);
  }, [summary?.recentLoginLogs, summary?.recentOperationLogs]);

  const teamMembers = useMemo(() => {
    const members: string[] = [];
    const seed = userInfo?.realName || userInfo?.nickname || userInfo?.username;
    if (seed) {
      members.push(seed);
    }
    (summary?.recentOperationLogs || []).forEach((item) => {
      if (item.username && !members.includes(item.username)) {
        members.push(item.username);
      }
    });
    (summary?.recentLoginLogs || []).forEach((item) => {
      if (item.username && !members.includes(item.username)) {
        members.push(item.username);
      }
    });
    return members.slice(0, 8);
  }, [summary?.recentLoginLogs, summary?.recentOperationLogs, userInfo?.nickname, userInfo?.realName, userInfo?.username]);

  const trendLabels = [
    { label: '最近登录', value: summary?.recentLoginLogs?.length ?? 0 },
    { label: '最近操作', value: summary?.recentOperationLogs?.length ?? 0 },
    { label: '当前角色', value: summary?.currentUser?.permissions?.length ?? 0 },
  ];

  return (
    <PageContainer
      className="saas-dashboard-workplace"
      ghost
      title="工作台"
      style={{ height: '100%', minHeight: 0 }}
      content={null}
    >
      <div className="saas-management-page-body saas-home-page">
        <section className="saas-home-hero">
          <div className="saas-home-hero__copy">
            <Typography.Text className="saas-home-hero__eyebrow" translate="no">
              宏翔商道
            </Typography.Text>
            <Typography.Title level={2} className="saas-home-hero__title">
              {greeting}
            </Typography.Title>
            <Typography.Paragraph className="saas-home-hero__description">
              {greetingNote}。统一审计、权限、插件与租户状态都在这里集中查看，保留同一套宽高节奏与操作路径。
            </Typography.Paragraph>
            <Space wrap size={12}>
              <Link className="saas-home-hero__action saas-home-hero__action--primary" to="/system/management">
                进入系统管理
              </Link>
              <Link className="saas-home-hero__action" to="/user-center/profile">
                打开个人中心
              </Link>
            </Space>
          </div>

          <div className="saas-home-hero__metrics" aria-label="首页状态摘要">
            {heroMetrics.map((item) => (
              <div className="saas-home-hero__metric" key={item.label}>
                <div className="saas-home-hero__metric-label">{item.label}</div>
                <div className="saas-home-hero__metric-value">{numberFormatter.format(item.value)}</div>
              </div>
            ))}
          </div>
        </section>

        <Row gutter={[20, 20]} align="stretch">
          <Col xs={24} xl={16}>
            <Card className="saas-home-panel saas-home-panel--entries" title="快捷入口">
              <Row gutter={[16, 16]}>
                {projectEntries.map((entry) => (
                  <Col key={entry.path} xs={24} sm={12}>
                    <Link className="saas-home-entry" to={entry.path} aria-label={`进入${entry.title}`}>
                      <span className="saas-home-entry__icon" style={{ background: entry.tone, color: entry.accent }}>
                        {entry.icon}
                      </span>
                      <span className="saas-home-entry__content">
                        <span className="saas-home-entry__title">{entry.title}</span>
                        <span className="saas-home-entry__desc">{entry.description}</span>
                        <span className="saas-home-entry__cta">立即进入</span>
                      </span>
                    </Link>
                  </Col>
                ))}
              </Row>
            </Card>

            <Card className="saas-home-panel saas-home-panel--activity" title="最近动态">
              {activityItems.length ? (
                <List
                  dataSource={activityItems}
                  split={false}
                  renderItem={(item) => (
                    <List.Item className="saas-home-activity__item">
                      <List.Item.Meta
                        avatar={<Avatar size={36} icon={<RocketOutlined />} className="saas-home-activity__avatar" />}
                        title={
                          <Space size={8} wrap>
                            <Typography.Text className="saas-home-activity__title">{item.title}</Typography.Text>
                            <Tag color={item.color}>{item.tag}</Tag>
                          </Space>
                        }
                        description={
                          <Space direction="vertical" size={4} style={{ width: '100%' }}>
                            <Typography.Text className="saas-home-activity__desc">{item.desc}</Typography.Text>
                            <Typography.Text type="secondary" className="saas-home-activity__time">
                              {item.createdAt}
                            </Typography.Text>
                          </Space>
                        }
                      />
                    </List.Item>
                  )}
                />
              ) : (
                <Empty description="暂无动态记录" />
              )}
            </Card>
          </Col>

          <Col xs={24} xl={8}>
            <Card className="saas-home-panel saas-home-panel--summary" title="状态摘要">
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                {trendLabels.map((item) => (
                  <div className="saas-home-summary" key={item.label}>
                    <span className="saas-home-summary__label">{item.label}</span>
                    <span className="saas-home-summary__value">{numberFormatter.format(item.value)}</span>
                  </div>
                ))}
                <Typography.Paragraph type="secondary" className="saas-home-summary__note">
                  工作台、插件和租户信息均按统一宽度约束展示，避免页面内容因模块不同而出现高度抖动。
                </Typography.Paragraph>
              </Space>
            </Card>

            <Card className="saas-home-panel saas-home-panel--team" title="团队与最近使用">
              {teamMembers.length ? (
                <List
                  dataSource={teamMembers}
                  renderItem={(member, index) => (
                    <List.Item className="saas-home-team__item">
                      <Space>
                        <Avatar size={28} icon={<UserOutlined />} className="saas-home-team__avatar" />
                        <Typography.Text className="saas-home-team__name">{member}</Typography.Text>
                      </Space>
                      <Tag color={['blue', 'green', 'gold', 'purple'][index % 4]}>{index === 0 ? '当前账号' : '成员'}</Tag>
                    </List.Item>
                  )}
                />
              ) : (
                <Empty description="暂无团队成员信息" />
              )}
            </Card>
          </Col>
        </Row>
      </div>
    </PageContainer>
  );
};
