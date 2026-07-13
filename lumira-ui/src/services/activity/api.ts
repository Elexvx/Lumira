import { request } from '@/services/common/request';
import type { ActivityQueryParams, ActivityRecord, ActivityRegistrationPayload, ActivityRegistrationRecord, ActivityUpsertPayload, PageResponse, PublicActivityRecord } from './types';

const ACTIVITY_API = '/v2/aiadc/activities';
const PUBLIC_ACTIVITY_API = '/v1/public/aiadc/activities';
const ACTIVITY_REGISTRATION_API = '/v2/aiadc/activity-registrations';

export const listActivities = (params: ActivityQueryParams) =>
  request<PageResponse<ActivityRecord>>(ACTIVITY_API, {
    method: 'GET',
    params: { ...params },
  });

export const listPublicActivities = (params: ActivityQueryParams) =>
  request<PageResponse<PublicActivityRecord>>(PUBLIC_ACTIVITY_API, {
    method: 'GET',
    params: { ...params },
    skipAuth: true,
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

export const listActivityRegistrations = () =>
  request<ActivityRegistrationRecord[]>(ACTIVITY_REGISTRATION_API, { method: 'GET' });

export const createActivityRegistration = (data: ActivityRegistrationPayload) =>
  request<ActivityRegistrationRecord>(ACTIVITY_REGISTRATION_API, { method: 'POST', data });
