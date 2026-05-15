import type { CurrentUser, LoginCodeChallenge, LoginEncryptionKey, LoginResponse, PasskeyCredentialRecord, PasskeyOptions, RefreshTokenResponse, WechatAuthorizeUrl } from '@/types/api';
import { request, type RequestOptions } from '@/services/common/request';

export interface LoginPayload {
  account?: string;
  username?: string;
  mobile?: string;
  password: string;
  captchaId?: string;
  captchaCode?: string;
  captchaProof?: string;
}

export interface RefreshTokenPayload {
  refreshToken: string;
}

export interface SecondFactorCompletePayload {
  factorCode: string;
  challengeId: string;
  verificationCode: string;
}

export interface LoginCodeChallengePayload {
  loginType: 'sms' | 'email';
  account: string;
}

export interface LoginCodeCompletePayload {
  challengeId: string;
  verificationCode: string;
}

export interface WechatLoginPayload {
  code: string;
  state: string;
}

export interface PasskeyRegistrationCompletePayload {
  challengeId: string;
  id: string;
  rawId: string;
  type: string;
  response: {
    clientDataJSON: string;
    attestationObject: string;
  };
  authenticatorAttachment?: string | null;
  transports?: string[];
  label?: string;
}

export interface PasskeyAuthenticationCompletePayload {
  challengeId: string;
  id: string;
  rawId: string;
  type: string;
  response: {
    clientDataJSON: string;
    authenticatorData: string;
    signature: string;
    userHandle?: string | null;
  };
  authenticatorAttachment?: string | null;
}

export interface SimulatedRolePayload {
  roleId?: number | null;
}

export const authService = {
  login: (payload: LoginPayload, options: RequestOptions = {}) =>
    request<LoginResponse>('/v1/auth/login', {
      method: 'POST',
      data: payload,
      skipAuth: true,
      silent: true,
      ...options,
    }),
  loginEncryptionKey: (options: RequestOptions = {}) =>
    request<LoginEncryptionKey>('/v1/auth/login-encryption-key', {
      method: 'GET',
      skipAuth: true,
      silent: true,
      ...options,
    }),
  loginCodeChallenge: (payload: LoginCodeChallengePayload, options: RequestOptions = {}) =>
    request<LoginCodeChallenge>('/v1/auth/login/code/challenge', {
      method: 'POST',
      data: payload,
      skipAuth: true,
      silent: true,
      ...options,
    }),
  loginCodeComplete: (payload: LoginCodeCompletePayload, options: RequestOptions = {}) =>
    request<LoginResponse>('/v1/auth/login/code/complete', {
      method: 'POST',
      data: payload,
      skipAuth: true,
      silent: true,
      ...options,
    }),
  wechatAuthorizeUrl: (options: RequestOptions = {}) =>
    request<WechatAuthorizeUrl>('/v1/auth/wechat/authorize-url', {
      method: 'GET',
      skipAuth: true,
      silent: true,
      ...options,
    }),
  wechatLogin: (payload: WechatLoginPayload, options: RequestOptions = {}) =>
    request<LoginResponse>('/v1/auth/wechat/login', {
      method: 'POST',
      data: payload,
      skipAuth: true,
      silent: true,
      ...options,
    }),
  passkeyAuthenticationOptions: (options: RequestOptions = {}) =>
    request<PasskeyOptions>('/v1/auth/passkeys/authentication/options', {
      method: 'POST',
      skipAuth: true,
      silent: true,
      ...options,
    }),
  passkeyAuthenticationComplete: (payload: PasskeyAuthenticationCompletePayload, options: RequestOptions = {}) =>
    request<LoginResponse>('/v1/auth/passkeys/authentication/complete', {
      method: 'POST',
      data: payload,
      skipAuth: true,
      silent: true,
      ...options,
    }),
  passkeyRegistrationOptions: (options: RequestOptions = {}) =>
    request<PasskeyOptions>('/v1/auth/passkeys/registration/options', {
      method: 'POST',
      ...options,
    }),
  passkeyRegistrationComplete: (payload: PasskeyRegistrationCompletePayload, options: RequestOptions = {}) =>
    request<PasskeyCredentialRecord>('/v1/auth/passkeys/registration/complete', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  passkeyCredentials: (options: RequestOptions = {}) =>
    request<PasskeyCredentialRecord[]>('/v1/auth/passkeys', {
      method: 'GET',
      ...options,
    }),
  renamePasskeyCredential: (id: number, label: string, options: RequestOptions = {}) =>
    request<PasskeyCredentialRecord>(`/v1/auth/passkeys/${id}`, {
      method: 'PATCH',
      data: { label },
      ...options,
    }),
  deletePasskeyCredential: (id: number, options: RequestOptions = {}) =>
    request<boolean>(`/v1/auth/passkeys/${id}`, {
      method: 'DELETE',
      ...options,
    }),
  logout: (options: RequestOptions = {}) =>
    request<boolean>('/v1/auth/logout', {
      method: 'POST',
      ...options,
    }),
  refreshToken: (payload: RefreshTokenPayload, options: RequestOptions = {}) =>
    request<RefreshTokenResponse>('/v1/auth/refresh-token', {
      method: 'POST',
      data: payload,
      skipAuth: true,
      ...options,
    }),
  secondFactorComplete: (payload: SecondFactorCompletePayload, options: RequestOptions = {}) =>
    request<LoginResponse>('/v1/auth/second-factor/complete', {
      method: 'POST',
      data: payload,
      skipAuth: true,
      silent: true,
      ...options,
    }),
  currentUser: (options: RequestOptions = {}) =>
    request<CurrentUser>('/v1/auth/current-user', {
      method: 'GET',
      ...options,
    }),
  simulatedRole: (payload: SimulatedRolePayload, options: RequestOptions = {}) =>
    request<CurrentUser>('/v1/auth/simulated-role', {
      method: 'PUT',
      data: payload,
      ...options,
    }),
};
