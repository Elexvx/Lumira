import { request } from '@/services/common/request';
import type { ActivityQueryParams, ActivityRecord, ActivityUpsertPayload, PageResponse } from './types';

const ACTIVITY_API = '/v2/aiadc/activities';

export const listActivities = (params: ActivityQueryParams) =>
  request<PageResponse<ActivityRecord>>(ACTIVITY_API, {
    method: 'GET',
    params: { ...params },
  });

export const createActivity = (data: ActivityUpsertPayload) =>
  request<ActivityRecord>(ACTIVITY_API, {
    method: 'POST',
    data,
  });

export const updateActivity = (id: number, data: ActivityUpsertPayload) =>
  request<ActivityRecord>(`${ACTIVITY_API}/${id}`, {
    method: 'PUT',
    data,
  });

export const deleteActivity = (id: number) =>
  request<boolean>(`${ACTIVITY_API}/${id}`, {
    method: 'DELETE',
  });
