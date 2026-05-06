import { history, Outlet, useAccess, useLocation } from '@umijs/max';
import { useEffect, useMemo } from 'react';
import {
  buildVisibleSettingsNavigationItems,
  SETTINGS_PROFILE_PATH,
} from '@/navigation/settingsNavigation';

const accessValue = (access: unknown, accessKey: string) =>
  Boolean((access as Record<string, unknown>)[accessKey]);

const resolveUserCenterLandingPath = (access: unknown) => {
  if (accessValue(access, 'canVisitSystemUsers')) {
    return '/user-center/users';
  }
  if (accessValue(access, 'canVisitSystemOnlineUsers')) {
    return '/user-center/online-users';
  }
  if (accessValue(access, 'canVisitSystemRoles')) {
    return '/user-center/roles';
  }
  if (accessValue(access, 'canVisitSystemMyFiles')) {
    return '/user-center/files';
  }
  return '/403';
};

const resolveFileCenterLandingPath = (access: unknown) => {
  if (accessValue(access, 'canVisitSystemAllFiles')) {
    return '/settings/files/all';
  }
  return '/403';
};

const SettingsLayout = () => {
  const location = useLocation();
  const access = useAccess();
  const pathname = location.pathname;
  const settingsItems = useMemo(
    () => buildVisibleSettingsNavigationItems((accessKey) => accessValue(access, accessKey)),
    [access],
  );

  useEffect(() => {
    if (pathname === '/settings' || pathname === '/settings/overview') {
      history.replace(settingsItems.find((item) => item.path === '/settings/menus')?.path || settingsItems[0]?.path || '/403');
      return;
    }

    if (pathname === '/user-center') {
      history.replace(resolveUserCenterLandingPath(access));
      return;
    }

    if (pathname === '/files') {
      history.replace(resolveFileCenterLandingPath(access));
    }
  }, [access, pathname, settingsItems]);

  if (pathname === SETTINGS_PROFILE_PATH) {
    return <Outlet />;
  }

  if (pathname === '/settings' || pathname === '/settings/overview' || pathname === '/user-center' || pathname === '/files') {
    return null;
  }

  return <Outlet />;
};

export default SettingsLayout;
