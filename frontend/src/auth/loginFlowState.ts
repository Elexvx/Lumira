let loginInProgress = false;
let authSessionEpoch = 0;

export const beginLoginFlow = () => {
  loginInProgress = true;
};

export const endLoginFlow = () => {
  loginInProgress = false;
};

export const isLoginInProgress = () => loginInProgress;

export const bumpAuthSessionEpoch = () => {
  authSessionEpoch += 1;
  return authSessionEpoch;
};

export const getAuthSessionEpoch = () => authSessionEpoch;
