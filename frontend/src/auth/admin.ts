import type { CurrentUser } from '@/types/api';

export const PROTECTED_ADMIN_ID = 1001;
export const PROTECTED_ADMIN_USERNAME = 'admin';

export const isProtectedAdminAccount = (user?: Pick<CurrentUser, 'userId' | 'username'> | null) =>
  Boolean(user && (user.userId === PROTECTED_ADMIN_ID || user.username?.toLowerCase() === PROTECTED_ADMIN_USERNAME));
