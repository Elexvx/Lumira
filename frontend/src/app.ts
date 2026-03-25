import { restoreSession } from '@/auth/session';
import type { CurrentUser } from '@/types/api';
import { request } from '@/services/common/request';

export async function getInitialState(): Promise<{ currentUser?: CurrentUser }> {
  if (!restoreSession()) {
    return {};
  }
  try {
    const currentUser = await request<CurrentUser>('/auth/current-user');
    return { currentUser };
  } catch (error) {
    return {};
  }
}
