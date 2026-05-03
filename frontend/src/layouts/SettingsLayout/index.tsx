import { history, Outlet, useAccess, useLocation } from '@umijs/max';
import { useEffect, useMemo } from 'react';
import {
  buildVisibleSettingsNavigationItems,
  SETTINGS_PROFILE_PATH,
} from '@/navigation/settingsNavigation';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';

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
  return '/403';
};

const SettingsLayout = () => {
  const location = useLocation();
  const access = useAccess();
  const { initialState } = useInitialStateModel();
  const pathname = location.pathname;
  const settingsItems = useMemo(
    () => buildVisibleSettingsNavigationItems(initialState?.menuTree, (accessKey) => accessValue(access, accessKey)),
    [access, initialState?.menuTree],
  );

  useEffect(() => {
    if (pathname === '/system' || pathname === '/system/overview') {
      history.replace(settingsItems[0]?.path || '/403');
      return;
    }

    if (pathname === '/user-center') {
      history.replace(resolveUserCenterLandingPath(access));
    }
  }, [access, pathname, settingsItems]);

  if (pathname === SETTINGS_PROFILE_PATH) {
    return <Outlet />;
  }

  if (pathname === '/system' || pathname === '/system/overview' || pathname === '/user-center') {
    return null;
  }

  return <Outlet />;
};

export default SettingsLayout;
