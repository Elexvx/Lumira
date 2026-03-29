import { useMemo } from 'react';
import { Button, Card, Col, Descriptions, Row, Space, Statistic, Tag, Timeline, Typography } from 'antd';
import { AppstoreOutlined, AuditOutlined, SettingOutlined, TeamOutlined } from '@ant-design/icons';
import { history, useRequest } from 'umi';
import { ManagementPageContainer } from '@/components/ManagementPageContainer';
import { EmptyState } from '@/components/EmptyState';
import { dashboardService } from '@/services/dashboard';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import type { DashboardSummary } from '@/types/api';

const quickEntries = [
  { title: '系统管理', path: '/system/management', description: '用户、角色、菜单、字典、配置', icon: <SettingOutlined /> },
  { title: '租户中心', path: '/tenant/overview', description: '当前租户与可访问租户', icon: <TeamOutlined /> },
  { title: '审计中心', path: '/audit/overview', description: '登录和操作日志', icon: <AuditOutlined /> },
  { title: '插件管理', path: '/system/plugins', description: '插件安装、启用、运行态', icon: <AppstoreOutlined /> },
];

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

  return (
    <ManagementPageContainer
      title="控制台首页"
      description="当前阶段聚焦平台可用性、租户上下文、插件能力和基础管理闭环。"
    >
      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={6}>
          <Card>
            <Statistic title="当前用户" value={userInfo?.username || '未登录'} />
            <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
              {userInfo?.realName || userInfo?.nickname || '账号信息已同步'}
            </Typography.Paragraph>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card>
            <Statistic title="当前租户" value={tenantInfo?.tenantShortName || tenantInfo?.tenantName || '未选择'} />
            <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
              {tenantInfo?.tenantCode || '租户上下文会随切换同步刷新'}
            </Typography.Paragraph>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card>
            <Statistic title="已启用插件" value={summary?.tenantPlugins?.length ?? initialState?.availablePlugins?.length ?? 0} />
            <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
              插件运行时、菜单和权限已纳入统一加载链路
            </Typography.Paragraph>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card>
            <Statistic title="菜单 / 权限" value={summary ? `${summary.menuCount} / ${summary.permissionCount}` : '--'} />
            <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
              页面访问和按钮控制都来自同一份权限快照
            </Typography.Paragraph>
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ flex: 1, minHeight: 0 }}>
        <Col xs={24} xl={16}>
          <Card title="快捷入口" bodyStyle={{ minHeight: isMobile ? undefined : 260 }}>
            <Row gutter={[16, 16]}>
              {quickEntries.map((item) => (
                <Col key={item.path} xs={24} sm={12}>
                  <Card hoverable onClick={() => history.push(item.path)} style={{ height: '100%' }}>
                    <Space align="start">
                      <div
                        style={{
                          width: 40,
                          height: 40,
                          borderRadius: 12,
                          display: 'grid',
                          placeItems: 'center',
                          background: '#eef4ff',
                          color: '#1d4ed8',
                          flexShrink: 0,
                        }}
                      >
                        {item.icon}
                      </div>
                      <div>
                        <Typography.Title level={5} style={{ marginBottom: 4 }}>
                          {item.title}
                        </Typography.Title>
                        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                          {item.description}
                        </Typography.Paragraph>
                      </div>
                    </Space>
                  </Card>
                </Col>
              ))}
            </Row>
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card title="待处理与公告">
            <Timeline
              items={[
                { children: '当前已接入登录、租户切换、权限快照和插件运行时。' },
                { children: '系统管理页已补齐最小闭环，后续可继续扩展审批、工单和消息中心。' },
                { children: '所有页面已统一到查询区、操作区、表格区和详情区的规范骨架。' },
              ]}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title="最近登录摘要" loading={summaryQuery.loading}>
            {summary?.recentLoginLogs?.length ? (
              <Timeline
                items={summary.recentLoginLogs.map((item) => ({
                  children: (
                    <Space direction="vertical" size={0}>
                      <Typography.Text strong>{item.username || '未知用户'}</Typography.Text>
                      <Typography.Text type="secondary">
                        {item.logResult || item.loginResult || '-'} · {item.createdAt}
                      </Typography.Text>
                    </Space>
                  ),
                  color: item.logResult === 'SUCCESS' || item.loginResult === 'SUCCESS' ? 'green' : 'red',
                }))}
              />
            ) : (
              <EmptyState description="暂无最近登录日志" />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="最近操作摘要">
            {summary?.recentOperationLogs?.length ? (
              <Timeline
                items={summary.recentOperationLogs.map((item) => ({
                  children: (
                    <Space direction="vertical" size={0}>
                      <Typography.Text strong>{item.moduleName || item.operationType || '系统操作'}</Typography.Text>
                      <Typography.Text type="secondary">{item.detailMessage || item.actionName || '-'}</Typography.Text>
                    </Space>
                  ),
                  color: 'blue',
                }))}
              />
            ) : (
              <EmptyState description="暂无最近操作日志" />
            )}
          </Card>
        </Col>
      </Row>

      <Card title="系统信息" extra={<Button onClick={() => summaryQuery.refresh()}>刷新</Button>}>
        <Descriptions column={isMobile ? 1 : 3} size="small">
          <Descriptions.Item label="请求状态">{summaryQuery.error ? '加载失败' : '正常'}</Descriptions.Item>
          <Descriptions.Item label="当前租户">{tenantInfo?.tenantName || '未选择'}</Descriptions.Item>
          <Descriptions.Item label="权限数量">{summary?.permissionCount ?? userInfo?.permissions?.length ?? 0}</Descriptions.Item>
        </Descriptions>
      </Card>
    </ManagementPageContainer>
  );
};
