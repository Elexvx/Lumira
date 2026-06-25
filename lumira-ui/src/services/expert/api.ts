import { request } from '@/services/common/request';
import type { ExpertQueryParams, ExpertRecord, ExpertUpsertPayload, PageResponse } from './types';

const EXPERT_API = '/v2/experts';

export const listExperts = (params: ExpertQueryParams) =>
  request<PageResponse<ExpertRecord>>(EXPERT_API, {
    method: 'GET',
    params: { ...params },
  });

export const createExpert = (data: ExpertUpsertPayload) =>
  request<ExpertRecord>(EXPERT_API, {
    method: 'POST',
    data,
  });

export const updateExpert = (id: number, data: ExpertUpsertPayload) =>
  request<ExpertRecord>(`${EXPERT_API}/${id}`, {
    method: 'PUT',
    data,
  });

export const uploadExpertAvatar = (file: File) => {
  const data = new FormData();
  data.append('file', file);
  return request<string>('/v1/profile/uploads/avatar', {
    method: 'POST',
    data,
  });
};

export const deleteExpert = (id: number) =>
  request<boolean>(`${EXPERT_API}/${id}`, {
    method: 'DELETE',
  });
