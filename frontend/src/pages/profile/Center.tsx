import { SafetyOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Alert, Button, Card, Col, Descriptions, Empty, Modal, Row, Space, Tag, Tabs, Timeline, Typography, message } from 'antd';
import { history, useRequest } from 'umi';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { profileService } from '@/services/profile';
import { secondFactorService } from '@/services/secondFactor';
import { useResponsive } from '@/hooks/useResponsive';
import type { ProfileSummary, SecondFactorProviderStatus } from '@/types/api';

const ProfileCenterPage = () => {
  const { initialState } = useInitialStateModel();
  const { isMobile } = useResponsive();
  const profileQuery = useRequest(async () => ({ data: await profileService.summary({ autoRedirectOnUnauthorized: false }) }) as {
    data: ProfileSummary;
  });
  const secondFactorQuery = useRequest(async () => ({ data: await secondFactorService.providers({ autoRedirectOnUnauthorized: false }) }) as {
    data: SecondFactorProviderStatus[];
  });

  const summary = profileQuery.data;
  const currentUser = summary?.currentUser || initialState?.currentUser;
  const currentTenant = summary?.currentTenant || initialState?.currentTenant || null;
  const roleNames = summary?.roleNames || [];
  const recentLoginLogs = summary?.recentLoginLogs || [];
  const providerList = secondFactorQuery.data || [];
  const requiresEmail = providerList.some((provider) => provider.emailRequired);
  const hasEmail = Boolean(currentUser?.email);

  const handleOpenPlugin = (pluginCode: string) => {
    history.push(`/plugins/${pluginCode}`);
  };

  const handleUnbind = (pluginCode: string, pluginName?: string | null) => {
    Modal.confirm({
      title: `解绑 ${pluginName || pluginCode}`,
      content: '解绑后该验证方式将立即失效，确认继续吗？',
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        await secondFactorService.unbind(pluginCode, { autoRedirectOnUnauthorized: false });
        message.success('已解绑');
        await secondFactorQuery.refresh();
      },
    });
  };

  const providerCards = providerList.length ? (
    <Row gutter={[16, 16]}>
      {providerList.map((provider) => {
        const statusColor = provider.enabled && provider.bound ? 'green' : provider.enabled ? 'gold' : 'default';
        return (
          <Col key={provider.pluginCode} xs={24} lg={12}>
            <Card
              title={
                <Space wrap>
                  <SafetyOutlined />
                  <span>{provider.pluginName || provider.pluginCode}</span>
                  <Tag color={statusColor}>{provider.bound ? '已绑定' : '未绑定'}</Tag>
                </Space>
              }
              loading={secondFactorQuery.loading}
              extra={<Tag color={provider.emailRequired ? 'blue' : 'default'}>{provider.factorName || '二次验证'}</Tag>}
            >
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Typography.Paragraph style={{ marginBottom: 0 }}>
                  {provider.statusMessage || '请前往对应插件页面完成绑定与验证设置。'}
                </Typography.Paragraph>
                <Descriptions column={1} size="small" bordered>
                  <Descriptions.Item label="验证方式">{provider.factorName || '-'}</Descriptions.Item>
                  <Descriptions.Item label="绑定标识">{provider.maskedContact || '-'}</Descriptions.Item>
                  <Descriptions.Item label="邮箱要求">{provider.emailRequired ? '需要邮箱' : '不需要邮箱'}</Descriptions.Item>
                </Descriptions>
                <Space wrap>
                  <Button type="primary" onClick={() => handleOpenPlugin(provider.pluginCode)}>
                    打开插件页面
                  </Button>
                  {provider.bound ? (
                    <Button danger onClick={() => handleUnbind(provider.pluginCode, provider.pluginName)}>
                      解绑
                    </Button>
                  ) : null}
                </Space>
              </Space>
            </Card>
          </Col>
        );
      })}
    </Row>
  ) : (
    <Empty description="当前租户未启用任何二次验证插件" />
  );

  return (
    <PageContainer
      className="saas-management-page"
      ghost
      title="个人中心"
      style={{ height: '100%', minHeight: 0 }}
      content={null}
    >
      <div className="saas-management-page-body">
        <Tabs
          defaultActiveKey="second-factor"
          items={[
            {
              key: 'second-factor',
              label: '2FA验证',
              children: (
                <Space direction="vertical" size={16} style={{ width: '100%' }}>
                  <Alert
                    showIcon
                    type="info"
                    message="二次验证入口"
                    description="短信验证码与 2FA 验证是两个独立插件。启用任一方式后，建议先完善邮箱信息，避免验证码不可用时无法登录。"
                  />
                  {requiresEmail && !hasEmail ? (
                    <Alert
                      showIcon
                      type="warning"
                      message="请先补充邮箱"
                      description="当前租户启用了需要邮箱的验证插件，请先在账号资料中补充邮箱，避免短信或 2FA 不可用时无法登录。"
                    />
                  ) : null}
                  {providerCards}
                </Space>
              ),
            },
            {
              key: 'overview',
              label: '账号概览',
              children: (
                <Space direction="vertical" size={16} style={{ width: '100%' }}>
                  <Row gutter={[16, 16]}>
                    <Col xs={24} lg={12}>
                      <Card title="账号信息" loading={profileQuery.loading}>
                        <Descriptions column={isMobile ? 1 : 2} size="small" bordered>
                          <Descriptions.Item label="用户名">{currentUser?.username || '-'}</Descriptions.Item>
                          <Descriptions.Item label="昵称">{currentUser?.nickname || '-'}</Descriptions.Item>
                          <Descriptions.Item label="姓名">{currentUser?.realName || '-'}</Descriptions.Item>
                          <Descriptions.Item label="邮箱">{currentUser?.email || '-'}</Descriptions.Item>
                          <Descriptions.Item label="手机号">{currentUser?.mobile || '-'}</Descriptions.Item>
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
                          当前会话沿用 JWT、Redis 会话和租户上下文恢复逻辑。退出登录后会同步清理本地 token 与租户缓存。
                        </Typography.Paragraph>
                        <Space wrap>
                          <Tag color="green">JWT</Tag>
                          <Tag color="blue">Redis 会话</Tag>
                          <Tag color="purple">租户恢复</Tag>
                          <Tag color="gold">SMTP 兜底</Tag>
                        </Space>
                      </Card>
                    </Col>
                  </Row>
                </Space>
              ),
            },
          ]}
        />
      </div>
    </PageContainer>
  );
};

export default ProfileCenterPage;
