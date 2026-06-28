import { request } from '@/services/common/request';

export interface AccountActivationInfo {
  valid: boolean;
  username?: string | null;
  email?: string | null;
  reason?: string | null;
}

export const verifyAccountActivationToken = (token: string) =>
  request<AccountActivationInfo>('/v2/account-activation/verify', {
    method: 'GET',
    params: { token },
  });

export const completeAccountActivation = (token: string, password: string) =>
  request<boolean>('/v2/account-activation/complete', {
    method: 'POST',
    data: { token, password },
  });
