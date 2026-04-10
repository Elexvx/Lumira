import { AppstoreOutlined, AuditOutlined, ControlOutlined, RocketOutlined, SettingOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Avatar, Button, Card, Col, Empty, List, Row, Space, Statistic, Tag, Typography } from 'antd';
import { useMemo, type ReactNode } from 'react';
import { history, useRequest } from 'umi';
import { dashboardService } from '@/services/dashboard';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import type { DashboardSummary } from '@/types/api';
import './Home.less';

interface QuickEntry {
  title: string;
  path: string;
  description: string;
  icon: ReactNode;
  color: string;
}

const quickEntries: QuickEntry[] = [
  {
    title: '系统管理',
    path: '/system/management',
    description: '用户、角色、菜单、字典、配置',
    icon: <SettingOutlined />,
    color: '#1677ff',
  },
  {
    title: '租户中心',
    path: '/tenant/overview',
    description: '当前租户与可访问租户',
    icon: <TeamOutlined />,
    color: '#52c41a',
  },
  {
    title: '审计中心',
    path: '/system/monitoring/audit',
    description: '登录和操作日志',
    icon: <AuditOutlined />,
    color: '#fa8c16',
  },
  {
    title: '插件管理',
    path: '/system/plugins',
    description: '插件安装、启用、运行态',
    icon: <AppstoreOutlined />,
    color: '#722ed1',
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
        color: matched?.color || '#1677ff',
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
    <PageContainer className="saas-dashboard-workplace" ghost title="工作台" style={{ height: '100%', minHeight: 0 }} content={null}>
      <div className="saas-management-page-body saas-home-page">
        <Card>
          <Row gutter={[16, 16]} align="middle">
            <Col xs={24} lg={14}>
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Typography.Text type="secondary" translate="no">
                  宏翔商道
                </Typography.Text>
                <Typography.Title level={2} style={{ margin: 0 }}>
                  {greeting}
                </Typography.Title>
                <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                  {greetingNote}。统一审计、权限、插件与租户状态都在这里集中查看。
                </Typography.Paragraph>
                <Space wrap>
                  <Button type="primary" onClick={() => history.push('/system/management')}>
                    进入系统管理
                  </Button>
                  <Button onClick={() => history.push('/user-center/profile')}>打开个人中心</Button>
                </Space>
              </Space>
            </Col>

            <Col xs={24} lg={10}>
              <Row gutter={[16, 16]}>
                {heroMetrics.map((item) => (
                  <Col key={item.label} xs={12}>
                    <Card size="small">
                      <Statistic title={item.label} value={numberFormatter.format(item.value)} />
                    </Card>
                  </Col>
                ))}
              </Row>
            </Col>
          </Row>
        </Card>

        <Row gutter={[16, 16]}>
          <Col xs={24} xl={16}>
            <Card title="快捷入口">
              <List
                grid={{ gutter: 16, xs: 1, sm: 2 }}
                dataSource={projectEntries}
                renderItem={(entry) => (
                  <List.Item>
                    <Card hoverable onClick={() => history.push(entry.path)} style={{ height: '100%' }}>
                      <Space align="start">
                        <Avatar style={{ backgroundColor: entry.color }} icon={entry.icon} />
                        <div>
                          <Typography.Title level={5} style={{ marginBottom: 4 }}>
                            {entry.title}
                          </Typography.Title>
                          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                            {entry.description}
                          </Typography.Paragraph>
                        </div>
                      </Space>
                    </Card>
                  </List.Item>
                )}
              />
            </Card>

            <Card title="最近动态">
              {activityItems.length ? (
                <List
                  dataSource={activityItems}
                  split={false}
                  renderItem={(item) => (
                    <List.Item>
                      <List.Item.Meta
                        avatar={<Avatar size={36} icon={<RocketOutlined />} />}
                        title={
                          <Space size={8} wrap>
                            <Typography.Text strong>{item.title}</Typography.Text>
                            <Tag color={item.color}>{item.tag}</Tag>
                          </Space>
                        }
                        description={
                          <Space direction="vertical" size={4}>
                            <Typography.Text type="secondary">{item.desc}</Typography.Text>
                            <Typography.Text type="secondary">{item.createdAt}</Typography.Text>
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
            <Card title="状态摘要">
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                {trendLabels.map((item) => (
                  <Card key={item.label} size="small">
                    <Statistic title={item.label} value={numberFormatter.format(item.value)} />
                  </Card>
                ))}
                <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                  工作台、插件和租户信息均按统一宽度约束展示。
                </Typography.Paragraph>
              </Space>
            </Card>

            <Card title="团队与最近使用">
              {teamMembers.length ? (
                <List
                  dataSource={teamMembers}
                  renderItem={(member, index) => (
                    <List.Item>
                      <Space>
                        <Avatar size={28} icon={<UserOutlined />} />
                        <Typography.Text>{member}</Typography.Text>
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
