import { history, Outlet, useAccess, useLocation } from '@umijs/max';
import { useEffect } from 'react';
import {
  SETTINGS_PROFILE_PATH,
  resolveFirstSettingsNavigationPath,
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
  const { initialState } = useInitialStateModel();
  const pathname = location.pathname;

  useEffect(() => {
    if (pathname === '/settings' || pathname === '/settings/overview') {
      history.replace(resolveFirstSettingsNavigationPath(initialState?.menuTree, (accessKey) => accessValue(access, accessKey)) || '/403');
      return;
    }

    if (pathname === '/user-center') {
      history.replace(resolveUserCenterLandingPath(access));
      return;
    }

    if (pathname === '/files') {
      history.replace(resolveFileCenterLandingPath(access));
    }
  }, [access, initialState?.menuTree, pathname]);

  if (pathname === SETTINGS_PROFILE_PATH) {
    return <Outlet />;
  }

  if (pathname === '/settings' || pathname === '/settings/overview' || pathname === '/user-center' || pathname === '/files') {
    return null;
  }

  return <Outlet />;
};

export default SettingsLayout;
