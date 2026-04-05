import { PageContainer } from '@ant-design/pro-components';
import { Card, Col, Descriptions, Empty, Row, Space, Tag, Timeline, Typography } from 'antd';
import { profileService } from '@/services/profile';
import { useRequest } from 'umi';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import type { ProfileSummary } from '@/types/api';

export default () => {
  const { initialState } = useInitialStateModel();
  const { isMobile } = useResponsive();
  const profileQuery = useRequest(async () => ({ data: await profileService.summary({ autoRedirectOnUnauthorized: false }) }) as {
    data: ProfileSummary;
  });
  const summary = profileQuery.data;
  const currentUser = summary?.currentUser || initialState?.currentUser;
  const currentTenant = summary?.currentTenant || initialState?.currentTenant || null;
  const roleNames = summary?.roleNames || [];
  const recentLoginLogs = summary?.recentLoginLogs || [];

  return (
    <PageContainer
      className="saas-management-page"
      ghost
      title="个人中心"
      style={{ height: '100%', minHeight: 0 }}
      content={null}
    >
      <div className="saas-management-page-body">
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={12}>
            <Card title="账号信息" loading={profileQuery.loading}>
              <Descriptions column={isMobile ? 1 : 2} size="small" bordered>
                <Descriptions.Item label="用户名">{currentUser?.username || '-'}</Descriptions.Item>
                <Descriptions.Item label="昵称">{currentUser?.nickname || '-'}</Descriptions.Item>
                <Descriptions.Item label="姓名">{currentUser?.realName || '-'}</Descriptions.Item>
                <Descriptions.Item label="用户ID">{currentUser?.userId || '-'}</Descriptions.Item>
                <Descriptions.Item label="会话ID">{currentUser?.sessionId || '-'}</Descriptions.Item>
                <Descriptions.Item label="会话版本">{currentUser?.sessionVersion ?? '-'}</Descriptions.Item>
              </Descriptions>
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card title="租户与安全">
              <Descriptions column={isMobile ? 1 : 2} size="small" bordered>
                <Descriptions.Item label="当前租户">{currentTenant?.tenantName || '未选择'}</Descriptions.Item>
                <Descriptions.Item label="租户编码">{currentTenant?.tenantCode || '-'}</Descriptions.Item>
                <Descriptions.Item label="权限数">{summary?.permissionCount ?? currentUser?.permissions?.length ?? 0}</Descriptions.Item>
                <Descriptions.Item label="角色摘要">
                  <Space wrap>
                    {roleNames.length ? roleNames.map((name) => <Tag key={name}>{name}</Tag>) : <Tag>暂无角色摘要</Tag>}
                  </Space>
                </Descriptions.Item>
              </Descriptions>
            </Card>
          </Col>
        </Row>

        <Row gutter={[16, 16]}>
          <Col xs={24} lg={14}>
            <Card title="最近登录记录" loading={profileQuery.loading}>
              {recentLoginLogs.length ? (
                <Timeline
                  items={recentLoginLogs.map((item) => ({
                    children: (
                      <Space direction="vertical" size={0}>
                        <Typography.Text strong>{item.username || '未知用户'}</Typography.Text>
                        <Typography.Text type="secondary">
                          {item.logResult || item.failReason || '登录记录'} · {item.createdAt}
                        </Typography.Text>
                      </Space>
                    ),
                    color: item.logResult === 'SUCCESS' ? 'green' : 'red',
                  }))}
                />
              ) : (
                <Empty description="暂无最近登录记录" />
              )}
            </Card>
          </Col>
          <Col xs={24} lg={10}>
            <Card title="账号安全">
              <Typography.Paragraph>
                当前阶段已保留 Redis 会话、JWT 刷新和租户上下文恢复逻辑。退出登录后会同步清理本地 token 与租户缓存。
              </Typography.Paragraph>
              <Space wrap>
                <Tag color="green">JWT</Tag>
                <Tag color="blue">Redis 会话</Tag>
                <Tag color="purple">租户恢复</Tag>
              </Space>
            </Card>
          </Col>
        </Row>
      </div>
    </PageContainer>
  );
};
