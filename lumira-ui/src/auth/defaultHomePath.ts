import { DEFAULT_HOME_PATH } from '@/app.constants';
import { getStoredCurrentUser } from '@/auth/sessionState';
import { resolveCanonicalRoutePath } from '@/routes/meta';

export const getConfiguredDefaultHomePath = () =>
  resolveCanonicalRoutePath(getStoredCurrentUser()?.defaultHomePath?.trim() || DEFAULT_HOME_PATH);
