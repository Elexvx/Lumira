import { storage } from '@/cache/storage';

const TOKEN_KEY = 'auth_token';

export const tokenManager = {
  getToken: () => storage.get<string>(TOKEN_KEY),
  setToken: (token: string) => storage.set(TOKEN_KEY, token),
  clearToken: () => storage.remove(TOKEN_KEY),
};
