import { Navigate, Outlet, useAccess, useLocation } from '@umijs/max';
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

  if (pathname === PROFILE_PATH) {
    return <Outlet />;
  }

  if (pathname === '/settings' || pathname === '/settings/overview') {
    return (
      <Navigate
        to={resolveFirstSettingsNavigationPath(initialState?.menuTree, (accessKey) => accessValue(access, accessKey), initialState?.availablePlugins) || DEFAULT_HOME_PATH}
        replace
      />
    );
  }

  if (pathname === '/user-center') {
    return <Navigate to={resolveUserCenterLandingPath(access)} replace />;
  }

  if (pathname === '/user-center/personal-center') {
    return <Navigate to={PROFILE_PATH} replace />;
  }

  if (pathname === '/files') {
    return <Navigate to={resolveFileCenterLandingPath(access)} replace />;
  }

  return <Outlet />;
};

export default SettingsLayout;
