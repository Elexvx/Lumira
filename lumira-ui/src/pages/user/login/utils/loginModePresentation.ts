import type { LoginMode } from '@/pages/user/login/components/LoginFormFields';

const FALLBACK_LOGIN_MODE: LoginMode = 'password';

export const resolvePresentedLoginModes = (_isMobile: boolean, availableLoginModes: LoginMode[]): LoginMode[] => {
  return availableLoginModes.length ? [...availableLoginModes] : [FALLBACK_LOGIN_MODE];
};

export const resolvePresentedLoginMode = (isMobile: boolean, activeLoginMode: LoginMode, availableLoginModes: LoginMode[]): LoginMode => {
  const presentedModes = resolvePresentedLoginModes(isMobile, availableLoginModes);
  return presentedModes.includes(activeLoginMode) ? activeLoginMode : (presentedModes[0] || FALLBACK_LOGIN_MODE);
};
