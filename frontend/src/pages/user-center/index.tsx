import { AppstoreOutlined, SafetyCertificateOutlined, TeamOutlined, UserOutlined, UserSwitchOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Avatar, Card, Col, Empty, List, Row, Typography } from 'antd';
import { history, Outlet, useAccess, useLocation } from 'umi';

const userCenterEntries = [
  {
    title: '用户管理',
    path: '/user-center/users',
    icon: <TeamOutlined />,
    description: '账号、资料和启停状态',
    canAccess: 'canVisitSystemUsers',
  },
  {
    title: '在线用户',
    path: '/user-center/online-users',
    icon: <UserSwitchOutlined />,
    description: '当前在线会话和踢出控制',
    canAccess: 'canVisitSystemOnlineUsers',
  },
  {
    title: '角色管理',
    path: '/user-center/roles',
    icon: <AppstoreOutlined />,
    description: '角色定义与权限分配',
    canAccess: 'canVisitSystemRoles',
  },
  {
    title: '权限管理',
    path: '/user-center/permissions',
    icon: <SafetyCertificateOutlined />,
    description: '权限清单与授权视图',
    canAccess: 'canVisitIam',
  },
  {
    title: '个人中心',
    path: '/user-center/profile',
    icon: <UserOutlined />,
    description: '账号资料、2FA 和个人设置',
    canAccess: 'canVisitProfile',
  },
] as const;

export default () => {
  const location = useLocation();
  const access = useAccess();

  if (location.pathname !== '/user-center') {
    return <Outlet />;
  }

  const entries = userCenterEntries.filter((item) => Boolean((access as Record<string, unknown>)[item.canAccess]));

  return (
    <PageContainer className="saas-management-page" ghost title="用户中心" style={{ height: '100%', minHeight: 0 }} content={null}>
      <div className="saas-management-page-body">
        <Row gutter={[16, 16]}>
          <Col xs={24} md={8}>
            <Card>
              <Typography.Title level={5} style={{ marginBottom: 4 }}>
                入口说明
              </Typography.Title>
              <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                这里汇总用户相关的管理入口，包括账号、在线会话、角色、权限和个人资料。
              </Typography.Paragraph>
            </Card>
          </Col>
          <Col xs={24} md={16}>
            <Card title="模块入口">
              {entries.length ? (
                <List
                  grid={{ gutter: 16, xs: 1, sm: 2, xl: 3 }}
                  dataSource={entries}
                  renderItem={(item) => (
                    <List.Item>
                      <Card hoverable onClick={() => history.push(item.path)} style={{ height: '100%' }}>
                        <Row gutter={12} align="middle" wrap={false}>
                          <Col flex="none">
                            <Avatar icon={item.icon} />
                          </Col>
                          <Col flex="auto">
                            <Typography.Title level={5} style={{ marginBottom: 4 }}>
                              {item.title}
                            </Typography.Title>
                            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                              {item.description}
                            </Typography.Paragraph>
                          </Col>
                        </Row>
                      </Card>
                    </List.Item>
                  )}
                />
              ) : (
                <Empty description="暂无可访问的用户中心入口" />
              )}
            </Card>
          </Col>
        </Row>
      </div>
    </PageContainer>
  );
};
