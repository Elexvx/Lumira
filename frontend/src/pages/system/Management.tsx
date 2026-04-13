import {
  AppstoreOutlined,
  BuildOutlined,
  DatabaseOutlined,
  FormOutlined,
  MailOutlined,
  NotificationOutlined,
  SafetyOutlined,
  SkinOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Avatar, Card, Col, List, Row, Statistic, Typography } from 'antd';
import { history, useRequest } from '@umijs/max';
import { dashboardService } from '@/services/dashboard';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import type { DashboardSummary } from '@/types/api';

const managementEntries = [
  { title: '用户中心', path: '/user-center', icon: <TeamOutlined />, description: '用户、在线会话、角色权限分配和个人中心' },
  { title: '菜单管理', path: '/system/menus', icon: <AppstoreOutlined />, description: '菜单树、路由和权限标识' },
  { title: '字典管理', path: '/system/dicts', icon: <DatabaseOutlined />, description: '字典类型和字典项' },
  { title: '字段管理', path: '/system/profile-fields', icon: <FormOutlined />, description: '个人中心资料字段展示开关' },
  { title: '个性化设置', path: '/system/personalization', icon: <SkinOutlined />, description: '品牌标识、版权设置和水印' },
  { title: '安全设置', path: '/system/security', icon: <SafetyOutlined />, description: 'Token、验证码、阈值、密码规范' },
  { title: '通知中心', path: '/system/notifications', icon: <NotificationOutlined />, description: '系统公告、通知发布和统一入口' },
  { title: 'SMTP 配置', path: '/system/smtp', icon: <MailOutlined />, description: '平台邮件基础服务与测试发送' },
  { title: '插件管理', path: '/system/plugins', icon: <BuildOutlined />, description: '插件运行时和版本管理' },
];

export default () => {
  const { initialState } = useInitialStateModel();
  const summaryQuery = useRequest(async () => ({ data: await dashboardService.summary({ autoRedirectOnUnauthorized: false }) }) as {
    data: DashboardSummary;
  });

  const summary = summaryQuery.data;

  return (
    <PageContainer className="saas-management-page" ghost title="系统管理" style={{ height: '100%', minHeight: 0 }} content={null}>
      <div className="saas-management-page-body">
        <Row gutter={[16, 16]}>
          <Col xs={24} md={8}>
            <Card>
              <Statistic title="当前菜单数" value={summary?.menuCount ?? initialState?.menuTree?.length ?? 0} />
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card>
              <Statistic title="当前权限数" value={summary?.permissionCount ?? initialState?.currentUser?.permissions?.length ?? 0} />
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card>
              <Statistic title="启用插件" value={summary?.tenantPlugins?.length ?? initialState?.availablePlugins?.length ?? 0} />
            </Card>
          </Col>
        </Row>

        <Card title="模块入口">
          <List
            grid={{ gutter: 16, xs: 1, sm: 2, xl: 3 }}
            dataSource={managementEntries}
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
        </Card>
      </div>
    </PageContainer>
  );
};
