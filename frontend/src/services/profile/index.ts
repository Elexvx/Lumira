import { request, type RequestOptions } from '@/services/common/request';
import type { CurrentUser, ProfileSummary } from '@/types/api';

export interface ProfileEmailPayload {
  email: string;
}

export interface ProfileBasicInfoPayload {
  avatarUrl?: string;
  nickname?: string;
  realName?: string;
  mobile?: string;
  email?: string;
  birthMonth?: string;
  gender?: string;
  region?: string;
  availableTime?: string;
  idCardNumber?: string;
}

export const profileService = {
  summary: (options: RequestOptions = {}) =>
    request<ProfileSummary>('/v1/profile/summary', {
      method: 'GET',
      ...options,
    }),
  updateBasicInfo: (payload: ProfileBasicInfoPayload, options: RequestOptions = {}) =>
    request<CurrentUser>('/v1/profile', {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  updateEmail: (payload: ProfileEmailPayload, options: RequestOptions = {}) =>
    request<CurrentUser>('/v1/profile/email', {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  uploadAvatar: (file: File, options: RequestOptions = {}) => {
    const formData = new FormData();
    formData.append('file', file);
    return request<string>('/v1/profile/uploads/avatar', {
      method: 'POST',
      headers: {},
      data: formData,
      ...options,
    });
  },
};
