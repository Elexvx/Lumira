import { DEFAULT_HOME_PATH } from '@/app.constants';
import { getStoredCurrentUser } from '@/auth/sessionState';
import { resolveCanonicalRoutePath } from '@/routes/meta';
import type { CurrentUser } from '@/types/api';

export const getCurrentRoleDefaultHomePath = (currentUser?: CurrentUser | null, fallback = DEFAULT_HOME_PATH) => {
  if (!currentUser) {
    return fallback;
  }
  const selectedRoleDefaultHomePath = currentUser.simulatedRoleId
    ? currentUser.availableRoles?.find((role) => role.id === currentUser.simulatedRoleId)?.defaultHomePath?.trim()
    : undefined;

  return selectedRoleDefaultHomePath || currentUser.defaultHomePath?.trim() || fallback;
};

export const getConfiguredDefaultHomePath = () =>
  resolveCanonicalRoutePath(getCurrentRoleDefaultHomePath(getStoredCurrentUser()));
