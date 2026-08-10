export const LOGIN_PATH = '/user/login';
export const DEFAULT_HOME_PATH = '/dashboard/home';
export const PUBLIC_PATHS = new Set([
  LOGIN_PATH,
  '/403',
  '/404',
  '/500',
  '/blank/workflow',
  '/public/certificate/verify',
  '/account-activation',
  '/review/invitation',
]);

export const isPublicPath = (path: string) => PUBLIC_PATHS.has(path) || path.startsWith('/certificate/verify/');
