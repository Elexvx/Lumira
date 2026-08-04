import type { LoginMode } from '@/pages/user/login/components/LoginFormFields';

const FALLBACK_LOGIN_MODE: LoginMode = 'password';

export const resolvePresentedLoginModes = (_isMobile: boolean, availableLoginModes: LoginMode[]): LoginMode[] => {
  const presentedModes = availableLoginModes.length
    ? [...availableLoginModes]
    : [FALLBACK_LOGIN_MODE];

  if (presentedModes.includes('wechat')) {
    return presentedModes;
  }

  const passwordIndex = presentedModes.indexOf('password');
  const insertIndex = passwordIndex >= 0 ? passwordIndex + 1 : presentedModes.length;
  return [
    ...presentedModes.slice(0, insertIndex),
    'wechat',
    ...presentedModes.slice(insertIndex),
  ];
};

export const resolvePresentedLoginMode = (isMobile: boolean, activeLoginMode: LoginMode, availableLoginModes: LoginMode[]): LoginMode => {
  const presentedModes = resolvePresentedLoginModes(isMobile, availableLoginModes);
  return presentedModes.includes(activeLoginMode) ? activeLoginMode : (presentedModes[0] || FALLBACK_LOGIN_MODE);
};
