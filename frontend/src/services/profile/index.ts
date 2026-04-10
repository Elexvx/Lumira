import { request, type RequestOptions } from '@/services/common/request';
import type { CurrentUser, ProfileSummary } from '@/types/api';

export interface ProfileEmailPayload {
  email: string;
}

export const profileService = {
  summary: (options: RequestOptions = {}) =>
    request<ProfileSummary>('/v1/profile/summary', {
      method: 'GET',
      ...options,
    }),
  updateEmail: (payload: ProfileEmailPayload, options: RequestOptions = {}) =>
    request<CurrentUser>('/v1/profile/email', {
      method: 'PUT',
      data: payload,
      ...options,
    }),
};
