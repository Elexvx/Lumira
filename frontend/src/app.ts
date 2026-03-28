import { history } from 'umi';
import { getStoredCurrentUser, isLoggedIn, restoreSession } from '@/auth/session';
import { tenantContext } from '@/tenant/context';
import type { CurrentUser, MyTenant, TenantSummary } from '@/types/api';

const LOGIN_PATH = '/user/login';
const DEFAULT_HOME_PATH = '/dashboard/home';
const PUBLIC_PATHS = new Set([LOGIN_PATH, '/403', '/blank/workflow']);

export interface AppInitialState {
  currentUser?: CurrentUser;
  currentTenant?: TenantSummary | null;
  myTenants: MyTenant[];
}

export async function getInitialState(): Promise<AppInitialState> {
  try {
    const restored = await restoreSession();
    if (restored?.currentUser) {
      return {
        currentUser: restored.currentUser,
        currentTenant: tenantContext.getCurrentTenant(),
        myTenants: tenantContext.getMyTenants(),
      };
    }
  } catch (error) {
    // 恢复失败时继续走本地兜底，交给路由守卫引导登录
  }

  return {
    currentUser: getStoredCurrentUser() || undefined,
    currentTenant: tenantContext.getCurrentTenant(),
    myTenants: tenantContext.getMyTenants(),
  };
}

export function onRouteChange({ location }: { location: Location }) {
  const path = location.pathname;
  const loggedIn = isLoggedIn();
  const isPublicPath = PUBLIC_PATHS.has(path);

  if (!loggedIn && !isPublicPath) {
    const redirect = `${path}${location.search || ''}`;
    history.replace(`${LOGIN_PATH}?redirect=${encodeURIComponent(redirect)}`);
    return;
  }

  if (loggedIn && path === LOGIN_PATH) {
    const searchParams = new URLSearchParams(location.search || '');
    const redirect = searchParams.get('redirect') || DEFAULT_HOME_PATH;
    history.replace(redirect);
  }
}
