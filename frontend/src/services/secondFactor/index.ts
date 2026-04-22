import { request, type RequestOptions } from '@/services/common/request';
import type {
  SecondFactorChallenge,
  SecondFactorProviderStatus,
  SecondFactorVerification,
} from '@/types/api';

export interface SecondFactorVerifyPayload {
  factorCode: string;
  challengeId: string;
  verificationCode: string;
}

export interface SecondFactorBindPayload {
  factorCode: string;
}

export const secondFactorService = {
  providers: (options: RequestOptions = {}) =>
    request<SecondFactorProviderStatus[]>('/v1/system/verification/providers', {
      method: 'GET',
      ...options,
    }),
  provider: (factorCode: string, options: RequestOptions = {}) =>
    request<SecondFactorProviderStatus>(`/v1/system/verification/providers/${factorCode}`, {
      method: 'GET',
      ...options,
    }),
  bind: (factorCode: string, options: RequestOptions = {}) =>
    request<SecondFactorChallenge>(`/v1/system/verification/providers/${factorCode}/bind`, {
      method: 'POST',
      ...options,
    }),
  unbind: (factorCode: string, options: RequestOptions = {}) =>
    request<boolean>(`/v1/system/verification/providers/${factorCode}/unbind`, {
      method: 'POST',
      ...options,
    }),
  challenge: (factorCode: string, options: RequestOptions = {}) =>
    request<SecondFactorChallenge>(`/v1/system/verification/providers/${factorCode}/challenge`, {
      method: 'POST',
      ...options,
    }),
  verify: (factorCode: string, payload: SecondFactorVerifyPayload, options: RequestOptions = {}) =>
    request<SecondFactorVerification>(`/v1/system/verification/providers/${factorCode}/verify`, {
      method: 'POST',
      data: payload,
      ...options,
    }),
};
