import dayjs from 'dayjs';
import { PageContainer, ProCard } from '@ant-design/pro-components';
import { Avatar, Skeleton, Space, Typography } from 'antd';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';

const buildInitials = (name?: string | null, fallback = 'U') => {
  const source = name?.trim();
  if (!source) {
    return fallback;
  }

  return source.slice(0, 1).toUpperCase();
};

const buildGreeting = (hour: number) => {
  if (hour >= 5 && hour < 9) return '早上好';
  if (hour >= 9 && hour < 12) return '上午好';
  if (hour >= 12 && hour < 14) return '中午好';
  if (hour >= 14 && hour < 18) return '下午好';
  if (hour >= 18 && hour < 24) return '晚上好';
  return '凌晨好';
};

const DashboardHomePage = () => {
  const { initialState } = useInitialStateModel();
  const responsive = useResponsive();
  const currentUser = initialState?.currentUser;
  const greeting = buildGreeting(dayjs().hour());
  const displayName = currentUser?.nickname || currentUser?.realName || currentUser?.username || '当前用户';
  const pageContainerToken = {
    paddingInlinePageContainerContent: resolveResponsiveValue(APP_SPACING.pageContainerPaddingInline, responsive.isMobile),
    paddingBlockPageContainerContent: resolveResponsiveValue(APP_SPACING.pageContainerPaddingBlock, responsive.isMobile),
  };

  return (
    <PageContainer title="工作台" ghost content={null} token={pageContainerToken} className="saas-dashboard-home__page">
      <ProCard variant="borderless" className="saas-dashboard-home__hero">
        <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile)} style={{ width: '100%' }}>
          <Space align="center" size={resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile)} wrap>
            <Avatar size={resolveResponsiveValue(APP_SPACING.avatarSize.normal, responsive.isMobile)} src={currentUser?.avatarUrl || undefined}>
              {buildInitials(currentUser?.nickname || currentUser?.realName || currentUser?.username)}
            </Avatar>
            <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.microGap, responsive.isMobile)}>
              <Typography.Title level={3} style={{ margin: 0 }}>
                {greeting}，{displayName}
              </Typography.Title>
              <Typography.Text type="secondary">欢迎回来，继续处理今天的系统事项</Typography.Text>
            </Space>
          </Space>
          {!currentUser ? <Skeleton active paragraph={{ rows: 2 }} title={false} /> : null}
        </Space>
      </ProCard>
      <div className="saas-dashboard-home__debug-placeholder">dashboard-home-mounted</div>
    </PageContainer>
  );
};

export default DashboardHomePage;
