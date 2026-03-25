import type { CurrentUser } from '@/types/api';

export default function access(initialState: { currentUser?: CurrentUser }) {
  const permissions = new Set(initialState?.currentUser?.permissions ?? []);
  return {
    hasPermission: (permission: string) => permissions.has(permission),
    isLogin: Boolean(initialState?.currentUser),
  };
}
