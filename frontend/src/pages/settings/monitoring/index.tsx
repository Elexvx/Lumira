import { useMemo, type ReactNode } from 'react';
import { history, Outlet, useAccess, useLocation } from '@umijs/max';
import { Tabs } from 'antd';
import { ManagementPage } from '@/features/management';
import { RedisMonitorContent } from '@/pages/settings/monitoring/Redis';
import { ServiceMonitorContent } from '@/pages/settings/monitoring/Service';

const normalizeTab = (value?: string | null) => (value === 'redis' ? 'redis' : 'service');

export default () => {
  const access = useAccess();
  const location = useLocation();
  const searchParams = useMemo(() => new URLSearchParams(location.search), [location.search]);
  const activeTab = normalizeTab(searchParams.get('tab'));
  const isChildRoute = location.pathname === '/settings/monitoring/api-docs' || location.pathname === '/settings/monitoring/audit';

  const tabs = useMemo(
    () =>
      [
        access.canVisitSystemMonitoringService
          ? {
              key: 'service',
              label: '服务监控',
              children: <ServiceMonitorContent />,
            }
          : null,
        access.canVisitSystemMonitoringRedis
          ? {
              key: 'redis',
              label: 'Redis监控',
              children: <RedisMonitorContent />,
            }
          : null,
      ].filter(Boolean) as Array<{ key: string; label: string; children: ReactNode }>,
    [access.canVisitSystemMonitoringRedis, access.canVisitSystemMonitoringService],
  );

  const resolvedActiveTab = tabs.some((item) => item.key === activeTab) ? activeTab : tabs[0]?.key;

  if (!resolvedActiveTab) {
    history.replace('/403');
    return null;
  }

  if (isChildRoute) {
    return <Outlet />;
  }

  return (
    <ManagementPage title="系统监控">
      <Tabs
        activeKey={resolvedActiveTab}
        items={tabs}
        onChange={(key) => {
          history.replace(`/settings/monitoring?tab=${key}`);
        }}
      />
    </ManagementPage>
  );
};
