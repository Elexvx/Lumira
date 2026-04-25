import type { CurrentUser, LoginCodeChallenge, LoginEncryptionKey, LoginResponse, RefreshTokenResponse } from '@/types/api';
import { request, type RequestOptions } from '@/services/common/request';

export interface LoginPayload {
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
};
