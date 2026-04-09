import { request, type RequestOptions } from '@/services/common/request';
import type {
  SecondFactorChallenge,
  SecondFactorProviderStatus,
  SecondFactorVerification,
} from '@/types/api';

export interface SecondFactorVerifyPayload {
  challengeId: string;
  verificationCode: string;
}

export interface SecondFactorBindPayload {
  pluginCode: string;
}

export const secondFactorService = {
  providers: (options: RequestOptions = {}) =>
    request<SecondFactorProviderStatus[]>('/v1/second-factor/providers', {
      method: 'GET',
      ...options,
    }),
  provider: (pluginCode: string, options: RequestOptions = {}) =>
    request<SecondFactorProviderStatus>(`/v1/second-factor/providers/${pluginCode}`, {
      method: 'GET',
      ...options,
    }),
  bind: (pluginCode: string, options: RequestOptions = {}) =>
    request<SecondFactorChallenge>(`/v1/second-factor/providers/${pluginCode}/bind`, {
      method: 'POST',
      ...options,
    }),
  unbind: (pluginCode: string, options: RequestOptions = {}) =>
    request<boolean>(`/v1/second-factor/providers/${pluginCode}/unbind`, {
      method: 'POST',
      ...options,
    }),
  challenge: (pluginCode: string, options: RequestOptions = {}) =>
    request<SecondFactorChallenge>(`/v1/second-factor/providers/${pluginCode}/challenge`, {
      method: 'POST',
      ...options,
    }),
  verify: (pluginCode: string, payload: SecondFactorVerifyPayload, options: RequestOptions = {}) =>
    request<SecondFactorVerification>(`/v1/second-factor/providers/${pluginCode}/verify`, {
      method: 'POST',
      data: payload,
      ...options,
    }),
};

