import { useEffect } from 'react';
import { history } from '@umijs/max';
import { LOGIN_PATH } from '@/app.constants';
import { getConfiguredDefaultHomePath } from '@/auth/defaultHomePath';
import { getStoredCurrentUser } from '@/auth/sessionState';

export default function RootRedirect() {
  useEffect(() => {
    history.replace(getStoredCurrentUser() ? getConfiguredDefaultHomePath() : LOGIN_PATH);
  }, []);

  return null;
}
