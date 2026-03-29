import { request, type RequestOptions } from '@/services/common/request';
import type { ProfileSummary } from '@/types/api';

export const profileService = {
  summary: (options: RequestOptions = {}) =>
    request<ProfileSummary>('/v1/profile/summary', {
      method: 'GET',
      ...options,
    }),
};
