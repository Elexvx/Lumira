import { useMemo, type ReactNode } from 'react';
import { Avatar, Button, Card, Col, Empty, List, Progress, Row, Space, Tag, Typography } from 'antd';
import { PageContainer } from '@ant-design/pro-components';
import {
  AppstoreOutlined,
  AuditOutlined,
  ControlOutlined,
  RocketOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { history, useRequest } from 'umi';
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
  iconBg: string;
  iconColor: string;
}

const quickEntries: QuickEntry[] = [
  {
    title: '系统管理',
    path: '/system/management',
    description: '用户、角色、菜单、字典、配置',
    icon: <SettingOutlined />,
    iconBg: '#eef4ff',
    iconColor: '#1d4ed8',
  },
  {
    title: '租户中心',
    path: '/tenant/overview',
    description: '当前租户与可访问租户',
    icon: <TeamOutlined />,
    iconBg: '#eefaf3',
    iconColor: '#15803d',
  },
  {
    title: '审计中心',
    path: '/audit/overview',
    description: '登录和操作日志',
    icon: <AuditOutlined />,
    iconBg: '#fff5eb',
    iconColor: '#d97706',
  },
  {
    title: '插件管理',
    path: '/system/plugins',
    description: '插件安装、启用、运行态',
    icon: <AppstoreOutlined />,
    iconBg: '#f4f3ff',
    iconColor: '#6d28d9',
  },
];

const quickEntryByPath = new Map(quickEntries.map((item) => [item.path, item]));

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
  const greeting = `${getGreetingByHour(new Date().getHours())}，${welcomeName}，祝你开心每一天！`;

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
        iconBg: matched?.iconBg || '#eef4ff',
        iconColor: matched?.iconColor || '#1d4ed8',
      };
    });
  }, [summary?.shortcuts]);

  const headerMetrics = [
    { label: '项目数', value: projectEntries.length },
    { label: '团队内排名', value: '8 / 24' },
    { label: '项目访问', value: (summary?.menuCount ?? 0) * 42 + (summary?.permissionCount ?? 0) },
  ];

  const indexMetrics = useMemo(() => {
    const menuScore = Math.min(100, Math.round(((summary?.menuCount ?? 0) / 40) * 100));
    const permissionScore = Math.min(100, Math.round(((summary?.permissionCount ?? 0) / 120) * 100));
    const pluginScore = Math.min(100, Math.round(((summary?.tenantPlugins?.length ?? 0) / 16) * 100));
    return [
      { key: 'menu', label: '导航覆盖', value: menuScore },
      { key: 'permission', label: '权限完备', value: permissionScore },
      { key: 'plugin', label: '插件活跃', value: pluginScore },
    ];
  }, [summary?.menuCount, summary?.permissionCount, summary?.tenantPlugins?.length]);

  const overallIndex = Math.round(indexMetrics.reduce((acc, item) => acc + item.value, 0) / indexMetrics.length);

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

  return (
    <PageContainer
      className="saas-dashboard-workplace"
      ghost
      title="工作台"
      style={{ height: '100%', minHeight: 0 }}
      content={null}
    >
      <div className="saas-management-page-body">
        <Card className="saas-dashboard-hero" loading={summaryQuery.loading}>
          <Row align="middle" gutter={[24, 16]}>
            <Col flex="auto">
              <Space align="start" size={16}>
                <Avatar
                  size={isMobile ? 56 : 72}
                  src={userInfo?.avatarUrl}
                  icon={<UserOutlined />}
                  className="saas-dashboard-hero-avatar"
                />
                <div>
                  <Typography.Title level={4} style={{ marginBottom: 4 }}>
                    {greeting}
                  </Typography.Title>
                  <Typography.Text type="secondary">
                    {tenantInfo?.tenantName || '未选择租户'} · 交互专家 | 插件平台协同 · 统一审计链路
                  </Typography.Text>
                </div>
              </Space>
            </Col>
            <Col xs={24} md={12} lg={10} xl={9}>
              <div className="saas-dashboard-hero-metrics">
                {headerMetrics.map((item) => (
                  <div className="saas-dashboard-hero-metric" key={item.label}>
                    <div className="saas-dashboard-hero-metric-label">{item.label}</div>
                    <div className="saas-dashboard-hero-metric-value">{item.value}</div>
                  </div>
                ))}
              </div>
            </Col>
          </Row>
        </Card>

        <Row gutter={[16, 16]} style={{ flex: 1, minHeight: 0 }}>
          <Col xs={24} xl={16}>
            <Card
              title="进行中的项目"
              extra={(
                <Typography.Link onClick={() => history.push('/system/management')}>
                  全部项目
                </Typography.Link>
              )}
            >
              <List
                grid={{ gutter: 16, column: isMobile ? 1 : 3 }}
                dataSource={projectEntries}
                renderItem={(item) => (
                  <List.Item>
                    <Card className="saas-dashboard-project-card" hoverable onClick={() => history.push(item.path)}>
                      <Space align="start" size={12}>
                        <span
                          className="saas-dashboard-project-icon"
                          style={{ background: item.iconBg, color: item.iconColor }}
                        >
                          {item.icon}
                        </span>
                        <div>
                          <Typography.Text strong>{item.title}</Typography.Text>
                          <Typography.Paragraph type="secondary" style={{ marginTop: 4, marginBottom: 8 }}>
                            {item.description}
                          </Typography.Paragraph>
                          <Typography.Link>立即进入</Typography.Link>
                        </div>
                      </Space>
                    </Card>
                  </List.Item>
                )}
              />
            </Card>

            <Card title="动态" className="saas-dashboard-activity-card">
              {activityItems.length ? (
                <List
                  dataSource={activityItems}
                  renderItem={(item) => (
                    <List.Item>
                      <List.Item.Meta
                        avatar={<Avatar size={32} icon={<RocketOutlined />} className="saas-dashboard-activity-avatar" />}
                        title={(
                          <Space size={8} wrap>
                            <Typography.Text>{item.title}</Typography.Text>
                            <Tag color={item.color}>{item.tag}</Tag>
                          </Space>
                        )}
                        description={item.desc}
                      />
                      <Typography.Text type="secondary">{item.createdAt}</Typography.Text>
                    </List.Item>
                  )}
                />
              ) : (
                <Empty description="暂无动态记录" />
              )}
            </Card>
          </Col>

          <Col xs={24} xl={8}>
            <Card
              title="快捷开始 / 便捷导航"
              extra={<Button type="link" size="small" onClick={() => history.push('/profile/center')}>个人中心</Button>}
            >
              <Space wrap className="saas-dashboard-quick-nav">
                {quickEntries.map((entry) => (
                  <Button key={entry.path} onClick={() => history.push(entry.path)}>
                    {entry.title}
                  </Button>
                ))}
              </Space>
            </Card>

            <Card title="XX 指数">
              <div className="saas-dashboard-index-wrap">
                <Progress type="dashboard" percent={overallIndex} width={isMobile ? 130 : 160} />
                <div style={{ flex: 1 }}>
                  {indexMetrics.map((item) => (
                    <div key={item.key} className="saas-dashboard-index-item">
                      <div className="saas-dashboard-index-label">{item.label}</div>
                      <Progress percent={item.value} size="small" />
                    </div>
                  ))}
                </div>
              </div>
            </Card>

            <Card title="团队">
              {teamMembers.length ? (
                <List
                  dataSource={teamMembers}
                  renderItem={(member, index) => (
                    <List.Item>
                      <Space>
                        <Avatar size={24} icon={<UserOutlined />} />
                        <Typography.Text>{member}</Typography.Text>
                      </Space>
                      <Tag color={['blue', 'green', 'gold', 'purple'][index % 4]}>{index === 0 ? '负责人' : '成员'}</Tag>
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
