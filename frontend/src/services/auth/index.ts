import { request } from '@/services/common/request';

export const authService = {
  currentUser: () => request('/auth/current-user'),
};
