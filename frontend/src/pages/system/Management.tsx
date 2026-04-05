import { AppstoreOutlined, BuildOutlined, DatabaseOutlined, SafetyOutlined, SettingOutlined, SkinOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Card, Col, Row, Statistic, Typography } from 'antd';
import { history, useRequest } from 'umi';
import { dashboardService } from '@/services/dashboard';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import type { DashboardSummary } from '@/types/api';

const managementEntries = [
  { title: '用户管理', path: '/system/users', icon: <UserOutlined />, description: '查询、新增、编辑、启停用户', permission: 'system:user:view' },
  { title: '角色管理', path: '/system/roles', icon: <TeamOutlined />, description: '角色维护与权限分配', permission: 'system:role:view' },
  { title: '菜单管理', path: '/system/menus', icon: <AppstoreOutlined />, description: '菜单树、路由和权限标识', permission: 'system:menu:view' },
  { title: '字典管理', path: '/system/dicts', icon: <DatabaseOutlined />, description: '字典类型和字典项', permission: 'system:dict:view' },
  { title: '参数配置', path: '/system/configs', icon: <SettingOutlined />, description: '平台级和租户级配置', permission: 'system:config:view' },
  { title: '个性化设置', path: '/system/personalization', icon: <SkinOutlined />, description: '站点名、Logo、Icon、页脚展示', permission: 'system:config:view' },
  { title: '安全设置', path: '/system/security', icon: <SafetyOutlined />, description: '空闲超时、token 生命周期', permission: 'system:config:view' },
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
