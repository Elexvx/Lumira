import { request, type RequestOptions } from '@/services/common/request';
import type { PagedResult, TaskRecord, TaskSummaryRecord } from '@/types/api';

export interface TaskPageQuery extends Record<string, unknown> {
  pageNo?: number;
  pageSize?: number;
}

export const taskService = {
  myPending: (params: TaskPageQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<TaskRecord>>('/tasks/my-pending', { method: 'GET', params, ...options }),
  myHandled: (params: TaskPageQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<TaskRecord>>('/tasks/my-handled', { method: 'GET', params, ...options }),
  summary: (options: RequestOptions = {}) =>
    request<TaskSummaryRecord>('/tasks/summary', { method: 'GET', ...options }),
};
