import { AppstoreOutlined, BuildOutlined, DatabaseOutlined, MailOutlined, SafetyOutlined, SkinOutlined, TeamOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Card, Col, Row, Statistic, Typography } from 'antd';
import { history, useRequest } from 'umi';
import { dashboardService } from '@/services/dashboard';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import type { DashboardSummary } from '@/types/api';

const managementEntries = [
  { title: '用户中心', path: '/user-center', icon: <TeamOutlined />, description: '用户、在线会话、角色和个人中心', permission: 'profile:view' },
  { title: '菜单管理', path: '/system/menus', icon: <AppstoreOutlined />, description: '菜单树、路由和权限标识', permission: 'system:menu:view' },
  { title: '字典管理', path: '/system/dicts', icon: <DatabaseOutlined />, description: '字典类型和字典项', permission: 'system:dict:view' },
  { title: '个性化设置', path: '/system/personalization', icon: <SkinOutlined />, description: '品牌标识、版权设置和水印', permission: 'system:config:view' },
  { title: '安全设置', path: '/system/security', icon: <SafetyOutlined />, description: 'Token、验证码、阈值、密码规范', permission: 'system:config:view' },
  { title: 'SMTP 配置', path: '/system/smtp', icon: <MailOutlined />, description: '平台邮件基础服务与测试发送', permission: 'system:config:view' },
  { title: '插件管理', path: '/system/plugins', icon: <BuildOutlined />, description: '插件运行时和版本管理', permission: 'plugin:management:view' },
];

export default () => {
  const { initialState } = useInitialStateModel();
  const summaryQuery = useRequest(async () => ({ data: await dashboardService.summary({ autoRedirectOnUnauthorized: false }) }) as {
    data: DashboardSummary;
  });

  return (
    <PageContainer
      className="saas-management-page"
      ghost
      title="系统管理"
      style={{ height: '100%', minHeight: 0 }}
      content={null}
    >
      <div className="saas-management-page-body">
        <Row gutter={[16, 16]}>
          <Col xs={24} md={8}>
            <Card>
              <Statistic title="当前菜单数" value={summaryQuery.data?.menuCount ?? initialState?.menuTree?.length ?? 0} />
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card>
              <Statistic title="当前权限数" value={summaryQuery.data?.permissionCount ?? initialState?.currentUser?.permissions?.length ?? 0} />
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card>
              <Statistic title="启用插件" value={summaryQuery.data?.tenantPlugins?.length ?? initialState?.availablePlugins?.length ?? 0} />
            </Card>
          </Col>
        </Row>

        <Row gutter={[16, 16]}>
          {managementEntries.map((item) => (
            <Col key={item.path} xs={24} sm={12} xl={8}>
              <Card hoverable onClick={() => history.push(item.path)} style={{ height: '100%' }}>
                <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
                  <div
                    style={{
                      width: 48,
                      height: 48,
                      borderRadius: 14,
                      background: '#eef4ff',
                      color: '#1d4ed8',
                      display: 'grid',
                      placeItems: 'center',
                      flexShrink: 0,
                      fontSize: 20,
                    }}
                  >
                    {item.icon}
                  </div>
                  <div style={{ minWidth: 0 }}>
                    <Typography.Title level={5} style={{ marginBottom: 4 }}>
                      {item.title}
                    </Typography.Title>
                    <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                      {item.description}
                    </Typography.Paragraph>
                  </div>
                </div>
              </Card>
            </Col>
          ))}
        </Row>
      </div>
    </PageContainer>
  );
};
