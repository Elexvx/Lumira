import { request, type RequestOptions } from '@/services/common/request';
import type { DashboardSummary } from '@/types/api';

export const dashboardService = {
  summary: (options: RequestOptions = {}) =>
    request<DashboardSummary>('/v1/dashboard/summary', {
      method: 'GET',
      ...options,
    }),
};
