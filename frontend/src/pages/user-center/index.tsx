import { useEffect, useMemo } from 'react';
import { history, Outlet, useAccess, useLocation } from 'umi';

export default () => {
  const location = useLocation();
  const access = useAccess();
  const landingPath = useMemo(() => {
    if (access.canVisitSystemUsers) {
      return '/user-center/users';
    }
    if (access.canVisitSystemOnlineUsers) {
      return '/user-center/online-users';
    }
    if (access.canVisitSystemRoles) {
      return '/user-center/roles';
    }
    if (access.canVisitProfile) {
      return '/user-center/profile';
    }
    return '/403';
  }, [access]);

  useEffect(() => {
    if (location.pathname === '/user-center') {
      history.replace(landingPath);
    }
  }, [landingPath, location.pathname]);

  if (location.pathname === '/user-center') {
    return null;
  }

  return <Outlet />;
};
