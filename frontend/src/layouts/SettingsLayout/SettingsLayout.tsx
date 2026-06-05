import { history, Outlet, useAccess, useLocation } from '@umijs/max';
import { useEffect } from 'react';
import { DEFAULT_HOME_PATH } from '@/app.constants';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { PROFILE_PATH } from '@/navigation/settingsNavigationConfig';
import { resolveFirstSettingsNavigationPath } from '@/navigation/settingsNavigationRuntime';

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
  return DEFAULT_HOME_PATH;
};

const resolveFileCenterLandingPath = (access: unknown) => {
  if (accessValue(access, 'canVisitSystemAllFiles')) {
    return '/settings/files/all';
  }
  return DEFAULT_HOME_PATH;
};

const SettingsLayout = () => {
  const location = useLocation();
  const access = useAccess();
  const { initialState } = useInitialStateModel();
  const pathname = location.pathname;

  useEffect(() => {
    if (pathname === '/settings' || pathname === '/settings/overview') {
      history.replace(resolveFirstSettingsNavigationPath(initialState?.menuTree, (accessKey) => accessValue(access, accessKey)) || DEFAULT_HOME_PATH);
      return;
    }

    if (pathname === '/user-center') {
      history.replace(resolveUserCenterLandingPath(access));
      return;
    }

    if (pathname === '/user-center/personal-center') {
      history.replace(PROFILE_PATH);
      return;
    }

    if (pathname === '/files') {
      history.replace(resolveFileCenterLandingPath(access));
    }
  }, [access, initialState?.menuTree, pathname]);

  if (pathname === PROFILE_PATH) {
    return <Outlet />;
  }

  if (pathname === '/settings' || pathname === '/settings/overview' || pathname === '/user-center' || pathname === '/user-center/personal-center' || pathname === '/files') {
    return null;
  }

  return <Outlet />;
};

export default SettingsLayout;
