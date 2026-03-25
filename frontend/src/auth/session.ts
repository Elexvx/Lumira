import { history } from '@umijs/max';
import { tokenManager } from './token';

export const restoreSession = () => Boolean(tokenManager.getToken());

export const logout = () => {
  tokenManager.clearToken();
  history.push('/user/login');
};
