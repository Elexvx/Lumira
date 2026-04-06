import { useEffect } from 'react';
import { history, Outlet, useLocation } from 'umi';

export default () => {
  const location = useLocation();

  useEffect(() => {
    if (location.pathname === '/system/monitoring') {
      history.replace('/system/monitoring/service');
    }
  }, [location.pathname]);

  if (location.pathname === '/system/monitoring') {
    return null;
  }

  return <Outlet />;
};
