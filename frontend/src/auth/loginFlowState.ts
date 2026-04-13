let loginInProgress = false;
let bootstrapInProgress = false;
let authSessionEpoch = 0;

export const beginLoginFlow = () => {
  loginInProgress = true;
};

export const endLoginFlow = () => {
  loginInProgress = false;
};

export const isLoginInProgress = () => loginInProgress;

export const beginBootstrapFlow = () => {
  bootstrapInProgress = true;
};

export const endBootstrapFlow = () => {
  bootstrapInProgress = false;
};

export const isBootstrapInProgress = () => bootstrapInProgress;

export const bumpAuthSessionEpoch = () => {
  authSessionEpoch += 1;
  return authSessionEpoch;
};

export const getAuthSessionEpoch = () => authSessionEpoch;
