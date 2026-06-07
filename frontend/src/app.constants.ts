export const LOGIN_PATH = '/user/login';
export const DEFAULT_HOME_PATH = '/dashboard/home';
export const PUBLIC_PATHS = new Set([LOGIN_PATH, '/403', '/404', '/blank/workflow']);

export const isPublicPath = (path: string) => PUBLIC_PATHS.has(path) || path.startsWith('/ai/share/');
