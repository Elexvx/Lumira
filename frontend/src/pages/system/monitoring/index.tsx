import { useEffect, useMemo } from 'react';
import { history, Outlet, useAccess, useLocation } from '@umijs/max';

export default () => {
  const location = useLocation();
  const access = useAccess();
  const landingPath = useMemo(() => {
    if (access.canVisitSystemMonitoringService) {
      return '/system/monitoring/service';
    }
    if (access.canVisitSystemMonitoringRedis) {
      return '/system/monitoring/redis';
    }
    if (access.canVisitSystemMonitoringDocs) {
      return '/system/monitoring/api-docs';
    }
    if (access.hasPermission('audit:view')) {
      return '/system/monitoring/audit';
    }
    return '/403';
  }, [access]);

  useEffect(() => {
    if (location.pathname === '/system/monitoring') {
      history.replace(landingPath);
    }
  }, [landingPath, location.pathname]);

  if (location.pathname === '/system/monitoring') {
    return null;
  }

  return <Outlet />;
};
